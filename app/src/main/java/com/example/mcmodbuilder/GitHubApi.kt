package com.example.mcmodbuilder

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Minimal wrapper around the parts of the GitHub REST API this app needs:
 *  1) upload the project zip into the repo (Contents API)
 *  2) trigger the "build-mod.yml" workflow (workflow_dispatch)
 *  3) poll the run until it finishes
 *  4) fetch the release asset (the built .jar) that the workflow publishes
 *
 * Docs: https://docs.github.com/en/rest
 */
class GitHubApi(
    private val owner: String,
    private val repo: String,
    private val token: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    private fun req(url: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    /** Uploads (creates or updates) a file in the repo via the Contents API. */
    @Throws(IOException::class)
    fun uploadFile(path: String, bytes: ByteArray, commitMessage: String, branch: String = "main") {
        val url = "https://api.github.com/repos/$owner/$repo/contents/$path"

        // Need the existing file's sha if it already exists, otherwise GitHub rejects the update.
        val existingSha = try {
            client.newCall(req(url).get().build()).execute().use { resp ->
                if (resp.isSuccessful) JSONObject(resp.body!!.string()).optString("sha") else null
            }
        } catch (_: Exception) { null }

        val body = JSONObject().apply {
            put("message", commitMessage)
            put("content", Base64.getEncoder().encodeToString(bytes))
            put("branch", branch)
            if (!existingSha.isNullOrEmpty()) put("sha", existingSha)
        }

        val request = req(url).put(body.toString().toRequestBody(jsonMedia)).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Upload failed: ${resp.code} ${resp.body?.string()}")
        }
    }

    /** Triggers the workflow_dispatch event with the given inputs and returns the dispatch time (ms). */
    @Throws(IOException::class)
    fun triggerWorkflow(workflowFile: String, ref: String, inputs: Map<String, String>): Long {
        val url = "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFile/dispatches"
        val body = JSONObject().apply {
            put("ref", ref)
            put("inputs", JSONObject(inputs))
        }
        val dispatchedAt = System.currentTimeMillis()
        val request = req(url).post(body.toString().toRequestBody(jsonMedia)).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Dispatch failed: ${resp.code} ${resp.body?.string()}")
        }
        return dispatchedAt
    }

    /**
     * Finds the workflow run created at/after [sinceMs] for [workflowFile].
     * Returns (runId, status, conclusion) or null if not found yet.
     */
    @Throws(IOException::class)
    fun findRecentRun(workflowFile: String, sinceMs: Long): Triple<Long, String, String?>? {
        val url = "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFile/runs?per_page=5"
        val request = req(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("List runs failed: ${resp.code}")
            val runs: JSONArray = JSONObject(resp.body!!.string()).getJSONArray("workflow_runs")
            for (i in 0 until runs.length()) {
                val run = runs.getJSONObject(i)
                val createdAtMs = java.time.Instant.parse(run.getString("created_at")).toEpochMilli()
                if (createdAtMs >= sinceMs - 15_000) {
                    return Triple(
                        run.getLong("id"),
                        run.getString("status"),
                        if (run.isNull("conclusion")) null else run.getString("conclusion"),
                    )
                }
            }
        }
        return null
    }

    /** Fetches a release by tag and returns the first asset's (name, browserDownloadUrl), if any. */
    @Throws(IOException::class)
    fun getReleaseAsset(tag: String): Pair<String, String>? {
        val url = "https://api.github.com/repos/$owner/$repo/releases/tags/$tag"
        val request = req(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val release = JSONObject(resp.body!!.string())
            val assets = release.getJSONArray("assets")
            if (assets.length() == 0) return null
            val asset = assets.getJSONObject(0)
            return asset.getString("name") to asset.getString("browser_download_url")
        }
    }

    /** Downloads an asset (needs auth because release assets on private repos require it). */
    @Throws(IOException::class)
    fun downloadAsset(url: String): ByteArray {
        val request = req(url)
            .header("Accept", "application/octet-stream")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed: ${resp.code}")
            return resp.body!!.bytes()
        }
    }
}
