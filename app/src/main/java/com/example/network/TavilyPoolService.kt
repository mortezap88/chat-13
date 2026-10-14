package com.example.network

import android.util.Log
import com.example.data.ApiKeyItem
import com.example.data.KeyStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TavilyPoolService(
    private var apiKeys: List<ApiKeyItem> = emptyList()
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private var activeKeyIndex = 0

    fun updateKeys(newKeys: List<ApiKeyItem>) {
        apiKeys = newKeys
        if (activeKeyIndex >= apiKeys.size) activeKeyIndex = 0
    }

    suspend fun searchWeb(
        query: String,
        thoroughness: String = "Balanced"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKeys.isEmpty()) {
            return@withContext Result.failure(Exception("No Tavily API keys configured"))
        }

        val (searchDepth, numResults) = when (thoroughness.lowercase().trim()) {
            "fast" -> "basic" to 2
            "comprehensive" -> "advanced" to 8
            else -> "basic" to 5 // Balanced
        }

        val jsonBody = JSONObject().apply {
            put("query", query)
            put("search_depth", searchDepth)
            put("include_answer", true)
            put("max_results", numResults)
        }

        var attempts = 0
        var lastError = "All Tavily keys failed or exhausted"
        while (attempts < apiKeys.size) {
            val keyItem = apiKeys[activeKeyIndex]
            try {
                val request = Request.Builder()
                    .url("https://api.tavily.com/search")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer ${keyItem.key}")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code
                val responseBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val root = JSONObject(responseBody)
                    val directAnswer = root.optString("answer", "")
                    val results = root.optJSONArray("results") ?: JSONArray()

                    if (results.length() == 0 && directAnswer.isBlank()) {
                        Log.w("TavilyPool", "Key ${keyItem.label}: 200 OK but zero results")
                        lastError = "Key ${keyItem.label}: Search succeeded but returned 0 results"
                        activeKeyIndex = (activeKeyIndex + 1) % apiKeys.size
                        attempts++
                        continue
                    }

                    val formatted = buildString {
                        if (directAnswer.isNotBlank()) {
                            appendLine("Summary: $directAnswer")
                            appendLine()
                        }
                        appendLine("Sources:")
                        for (i in 0 until results.length().coerceAtMost(numResults)) {
                            val r = results.getJSONObject(i)
                            val title = r.optString("title", "Source")
                            val content = r.optString("content", "")
                            val url = r.optString("url", "")
                            appendLine("- [$title]($url): ${content.take(240)}...")
                        }
                    }
                    return@withContext Result.success(formatted)
                } else {
                    Log.w("TavilyPool", "Key ${keyItem.label} failed with code $code: $responseBody")
                    lastError = "HTTP $code: ${responseBody.take(180)}"
                    activeKeyIndex = (activeKeyIndex + 1) % apiKeys.size
                    attempts++
                }
            } catch (e: Exception) {
                Log.e("TavilyPool", "Error querying Tavily: ${e.message}")
                lastError = e.message ?: "Connection error"
                activeKeyIndex = (activeKeyIndex + 1) % apiKeys.size
                attempts++
            }
        }
        Result.failure(Exception(lastError))
    }
}
