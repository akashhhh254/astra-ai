package com.example.core.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "SearchService"

data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String
)

interface SearchProvider {
    val name: String
    suspend fun search(query: String): List<SearchResult>
}

// 1. Wikipedia Entity & General Knowledge Search Provider
class WikipediaSearchProvider(
    private val client: OkHttpClient
) : SearchProvider {
    override val name: String = "Wikipedia"

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=1&format=json&srlimit=4"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AstraAI/2.0 (Mobile App; Android; Contact: info@astraai.app)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val json = JSONObject(body)
            val queryObj = json.optJSONObject("query") ?: return@withContext emptyList()
            val searchArr = queryObj.optJSONArray("search") ?: return@withContext emptyList()

            for (i in 0 until searchArr.length().coerceAtMost(4)) {
                val item = searchArr.getJSONObject(i)
                val title = item.optString("title", "")
                val rawSnippet = item.optString("snippet", "")
                val cleanSnippet = cleanHtml(rawSnippet)
                val pageUrl = "https://en.wikipedia.org/wiki/${URLEncoder.encode(title.replace(' ', '_'), "UTF-8")}"

                if (title.isNotBlank()) {
                    results.add(
                        SearchResult(
                            title = title,
                            snippet = cleanSnippet,
                            url = pageUrl,
                            source = "Wikipedia"
                        )
                    )
                }
            }

            // Also check for direct summary if query looks like a specific entity (e.g. "Rohit Sharma")
            if (results.isNotEmpty()) {
                val topTitle = results[0].title
                val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(topTitle.replace(' ', '_'), "UTF-8")}"
                try {
                    val summaryReq = Request.Builder().url(summaryUrl).build()
                    val summaryResp = client.newCall(summaryReq).execute()
                    val summaryBody = summaryResp.body?.string()
                    if (!summaryBody.isNullOrBlank()) {
                        val summaryJson = JSONObject(summaryBody)
                        val extract = summaryJson.optString("extract", "")
                        val description = summaryJson.optString("description", "")
                        if (extract.isNotBlank()) {
                            results[0] = results[0].copy(
                                snippet = if (description.isNotBlank()) "[$description] $extract" else extract
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia search failed for '$query': ${e.message}")
        }
        results
    }

    private fun cleanHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .trim()
    }
}

// 2. DuckDuckGo Instant Answer Search Provider
class DuckDuckGoSearchProvider(
    private val client: OkHttpClient
) : SearchProvider {
    override val name: String = "DuckDuckGo"

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AstraAI/2.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val json = JSONObject(body)
            val abstractText = json.optString("AbstractText", "")
            val abstractUrl = json.optString("AbstractURL", "")
            val heading = json.optString("Heading", "")

            if (abstractText.isNotBlank()) {
                results.add(
                    SearchResult(
                        title = if (heading.isNotBlank()) heading else query,
                        snippet = abstractText,
                        url = if (abstractUrl.isNotBlank()) abstractUrl else "https://duckduckgo.com/?q=$encoded",
                        source = json.optString("AbstractSource", "DuckDuckGo")
                    )
                )
            }

            val relatedTopics = json.optJSONArray("RelatedTopics")
            if (relatedTopics != null) {
                for (i in 0 until relatedTopics.length().coerceAtMost(3)) {
                    val topic = relatedTopics.optJSONObject(i) ?: continue
                    val text = topic.optString("Text", "")
                    val firstUrl = topic.optString("FirstURL", "")
                    if (text.isNotBlank()) {
                        results.add(
                            SearchResult(
                                title = text.take(60),
                                snippet = text,
                                url = if (firstUrl.isNotBlank()) firstUrl else "https://duckduckgo.com/?q=$encoded",
                                source = "DuckDuckGo"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DuckDuckGo search failed for '$query': ${e.message}")
        }
        results
    }
}

// 3. Composite Multi-Source Search Engine
class MultiSourceSearchEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val providers: List<SearchProvider> = listOf(
        WikipediaSearchProvider(client),
        DuckDuckGoSearchProvider(client)
    )

    suspend fun executeSearch(query: String): List<SearchResult> {
        val collected = mutableListOf<SearchResult>()
        for (provider in providers) {
            val results = withTimeoutOrNull(6000L) {
                try {
                    provider.search(query)
                } catch (e: Exception) {
                    Log.w(TAG, "${provider.name} provider error: ${e.message}")
                    emptyList()
                }
            } ?: emptyList()

            collected.addAll(results)
            // If primary provider returned good high-confidence results, we can continue or stop
            if (collected.size >= 4) break
        }
        return collected.distinctBy { it.url }
    }
}
