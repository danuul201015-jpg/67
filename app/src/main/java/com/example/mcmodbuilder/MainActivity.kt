package com.example.mcmodbuilder

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val PREFS = "mc_mod_builder_prefs"
private val LOADERS = listOf("fabric", "forge", "neoforge")

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var owner by remember { mutableStateOf(prefs.getString("owner", "") ?: "") }
    var repo by remember { mutableStateOf(prefs.getString("repo", "") ?: "") }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }

    var loader by remember { mutableStateOf(LOADERS[0]) }
    var mcVersion by remember { mutableStateOf("1.20.4") }
    var pickedZip by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf("") }

    var status by remember { mutableStateOf("Готово") }
    var busy by remember { mutableStateOf(false) }
    var downloadReady by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }

    val pickZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedZip = uri
            pickedName = uri.lastPathSegment ?: "project.zip"
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("MC Mod Builder", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Сборка происходит на GitHub Actions — приложение только отправляет " +
                        "проект и забирает готовый .jar.",
                    style = MaterialTheme.typography.bodySmall
                )

                ExpandableSection(title = "Настройка GitHub (один раз)") {
                    OutlinedTextField(owner, { owner = it }, label = { Text("Владелец репозитория (username)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(repo, { repo = it }, label = { Text("Название репозитория") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(token, { token = it }, label = { Text("Personal Access Token") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        prefs.edit().putString("owner", owner).putString("repo", repo).putString("token", token).apply()
                        status = "Настройки сохранены"
                    }) { Text("Сохранить") }
                }

                Divider()

                Text("Проект мода", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { pickZipLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                    Text(if (pickedName.isEmpty()) "Выбрать ZIP архив" else "Выбран: $pickedName")
                }

                Text("Загрузчик модов", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LOADERS.forEach { l ->
                        FilterChip(selected = loader == l, onClick = { loader = l }, label = { Text(l) })
                    }
                }

                OutlinedTextField(
                    value = mcVersion,
                    onValueChange = { mcVersion = it },
                    label = { Text("Версия Minecraft (например 1.20.4)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = !busy && pickedZip != null && owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        val uri = pickedZip ?: return@Button
                        busy = true
                        status = "Загружаю проект на GitHub…"
                        scope.launch {
                            try {
                                val bytes = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                                }
                                val api = GitHubApi(owner.trim(), repo.trim(), token.trim())
                                val runTag = "modbuild-${System.currentTimeMillis()}"
                                val zipPath = "incoming/$runTag.zip"

                                withContext(Dispatchers.IO) {
                                    api.uploadFile(zipPath, bytes, "Upload project for $runTag")
                                }

                                status = "Запускаю сборку на GitHub Actions…"
                                val since = withContext(Dispatchers.IO) {
                                    api.triggerWorkflow(
                                        "build-mod.yml",
                                        "main",
                                        mapOf(
                                            "zip_path" to zipPath,
                                            "loader" to loader,
                                            "mc_version" to mcVersion.trim(),
                                            "release_tag" to runTag,
                                        )
                                    )
                                }

                                status = "Сборка запущена. Жду завершения…"
                                var conclusion: String? = null
                                var attempts = 0
                                while (attempts < 90) { // up to ~15 minutes
                                    kotlinx.coroutines.delay(10_000)
                                    val run = withContext(Dispatchers.IO) { api.findRecentRun("build-mod.yml", since) }
                                    if (run != null) {
                                        val (_, runStatus, runConclusion) = run
                                        status = "Статус сборки: $runStatus"
                                        if (runStatus == "completed") {
                                            conclusion = runConclusion
                                            break
                                        }
                                    }
                                    attempts++
                                }

                                if (conclusion != "success") {
                                    status = "Сборка не удалась (итог: ${conclusion ?: "нет ответа"}). Проверьте логи workflow на GitHub."
                                    busy = false
                                    return@launch
                                }

                                status = "Сборка завершена, скачиваю jar…"
                                val asset = withContext(Dispatchers.IO) { api.getReleaseAsset(runTag) }
                                if (asset == null) {
                                    status = "Сборка успешна, но jar-файл не найден в релизе."
                                    busy = false
                                    return@launch
                                }
                                val (assetName, assetUrl) = asset
                                val jarBytes = withContext(Dispatchers.IO) { api.downloadAsset(assetUrl) }
                                downloadReady = assetName to jarBytes
                                status = "Готово! Файл: $assetName"
                            } catch (e: Exception) {
                                status = "Ошибка: ${e.message}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(if (busy) "Собираю…" else "Собрать мод")
                }

                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                Text(status)

                downloadReady?.let { (name, bytes) ->
                    Button(onClick = {
                        try {
                            val resolver = context.contentResolver
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/java-archive")
                                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                            }
                            val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                            val itemUri = resolver.insert(collection, values)
                            if (itemUri == null) {
                                status = "Не удалось создать файл в Downloads"
                            } else {
                                resolver.openOutputStream(itemUri)?.use { it.write(bytes) }
                                values.clear()
                                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                                resolver.update(itemUri, values, null, null)
                                status = "Сохранено в Downloads: $name"
                            }
                        } catch (e: Exception) {
                            status = "Ошибка сохранения: ${e.message}"
                        }
                    }) {
                        Text("Сохранить .jar на телефон")
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "▼ $title" else "▶ $title")
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}
