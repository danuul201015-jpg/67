package com.example.autoclicker

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * Резервная копия и пресеты настроек.
 * Копия пишется в файл, который выбирает пользователь (например в Загрузках).
 * Такой файл не удаляется вместе с приложением, поэтому настройки можно вернуть после переустановки.
 */
object Backup {
    private const val CFG = "cfg"
    private const val PRESET_STORE = "presets"
    private const val BACKUP = "backup"
    private const val VERSION = 1

    private fun cfgPrefs(c: Context) = c.getSharedPreferences(CFG, Context.MODE_PRIVATE)
    private fun presetPrefs(c: Context) = c.getSharedPreferences(PRESET_STORE, Context.MODE_PRIVATE)
    private fun backupPrefs(c: Context) = c.getSharedPreferences(BACKUP, Context.MODE_PRIVATE)

    /** Все настройки и точки с типами значений, чтобы восстановить их точно */
    fun snapshot(c: Context): JSONObject {
        val o = JSONObject()
        for ((k, v) in cfgPrefs(c).all) {
            val e = JSONObject()
            when (v) {
                is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                is Int -> { e.put("t", "i"); e.put("v", v) }
                is Long -> { e.put("t", "l"); e.put("v", v) }
                is Boolean -> { e.put("t", "b"); e.put("v", v) }
                is String -> { e.put("t", "s"); e.put("v", v) }
                else -> continue
            }
            o.put(k, e)
        }
        return o
    }

    /** Заменяет текущие настройки и точки снимком */
    fun restore(c: Context, o: JSONObject) {
        val ed = cfgPrefs(c).edit()
        ed.clear()
        o.keys().forEach { k ->
            val e = o.optJSONObject(k) ?: return@forEach
            when (e.optString("t")) {
                "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                "i" -> ed.putInt(k, e.getInt("v"))
                "l" -> ed.putLong(k, e.getLong("v"))
                "b" -> ed.putBoolean(k, e.getBoolean("v"))
                "s" -> ed.putString(k, e.getString("v"))
            }
        }
        ed.commit()
    }

    private fun loadPresets(c: Context): JSONObject =
        try {
            JSONObject(presetPrefs(c).getString("list", "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

    private fun storePresets(c: Context, o: JSONObject) {
        presetPrefs(c).edit().putString("list", o.toString()).apply()
    }

    fun presetNames(c: Context): List<String> =
        loadPresets(c).keys().asSequence().toList().sorted()

    fun savePreset(c: Context, name: String) {
        val all = loadPresets(c)
        all.put(name, snapshot(c))
        storePresets(c, all)
    }

    fun loadPreset(c: Context, name: String): Boolean {
        val snap = loadPresets(c).optJSONObject(name) ?: return false
        restore(c, snap)
        return true
    }

    fun deletePreset(c: Context, name: String) {
        val all = loadPresets(c)
        all.remove(name)
        storePresets(c, all)
    }

    /** Весь файл: текущие настройки и все пресеты */
    fun fileText(c: Context): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("settings", snapshot(c))
        root.put("presets", loadPresets(c))
        return root.toString(2)
    }

    /** Загружает файл копии. Возвращает false, если это не копия */
    fun importText(c: Context, text: String): Boolean {
        val root = try { JSONObject(text) } catch (e: Exception) { return false }
        val settings = root.optJSONObject("settings") ?: return false
        restore(c, settings)
        root.optJSONObject("presets")?.let { storePresets(c, it) }
        return true
    }

    fun readText(c: Context, uri: Uri): String? =
        try {
            c.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }

    fun backupUri(c: Context): Uri? =
        backupPrefs(c).getString("uri", null)?.let { Uri.parse(it) }

    fun setBackupUri(c: Context, uri: Uri) {
        backupPrefs(c).edit().putString("uri", uri.toString()).apply()
    }

    /** Пишет копию в выбранный файл. Вызывается после каждого сохранения */
    fun autoWrite(c: Context) {
        val uri = backupUri(c) ?: return
        try {
            c.contentResolver.openOutputStream(uri, "wt")?.use { it.write(fileText(c).toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            // файл недоступен (например, разрешение потеряно): пропускаем, настройки в приложении целы
        }
    }
}
