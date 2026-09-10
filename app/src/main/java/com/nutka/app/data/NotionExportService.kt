package com.nutka.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class NotionExportResult {
    object Success : NotionExportResult()
    data class Failure(val message: String) : NotionExportResult()
}

/** Real Notion API integration — creates one page per sent recording. */
class NotionExportService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()
    private val notionVersion = "2022-06-28"

    private companion object {
        /** Hard limit on `children` per Notion API request. */
        const val MAX_BLOCKS_PER_REQUEST = 100
    }

    fun export(recording: Recording, settings: SettingsState): NotionExportResult {
        if (settings.notionToken.isBlank() || settings.notionDatabaseId.isBlank()) {
            return NotionExportResult.Failure("Skonfiguruj token integracji i ID bazy danych Notion w Ustawieniach")
        }
        val titleProperty = resolveTitlePropertyName(settings) ?: "Name"

        val title = recording.title?.takeIf { it.isNotBlank() }
            ?: "Nagranie ${SimpleDateFormat("d MMM yyyy, HH:mm", Locale("pl")).format(Date(recording.createdAtMillis))}"

        val blocks: List<JSONObject> = if (recording.segments.isEmpty()) {
            listOf(paragraphBlock("(Brak transkrypcji)"))
        } else {
            recording.segments.map { seg ->
                val speakerName = recording.speakerNames[seg.speaker] ?: seg.speakerLabel ?: "Mówca"
                paragraphBlock("$speakerName: ${seg.text}")
            }
        }

        val body = JSONObject().apply {
            put("parent", JSONObject().put("database_id", settings.notionDatabaseId))
            put("properties", JSONObject().put(
                titleProperty,
                JSONObject().put("title", JSONArray().put(
                    JSONObject().put("text", JSONObject().put("content", title))
                ))
            ))
            // Notion rejects a request carrying more than 100 children outright,
            // so a transcript longer than 100 segments used to fail completely.
            // The first 100 go with the page; the rest are appended afterwards.
            put("children", blocks.take(MAX_BLOCKS_PER_REQUEST).fold(JSONArray()) { arr, b -> arr.put(b) })
        }

        val request = Request.Builder()
            .url("https://api.notion.com/v1/pages")
            .addHeader("Authorization", "Bearer ${settings.notionToken}")
            .addHeader("Notion-Version", notionVersion)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return runCatching {
            val pageId = client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@runCatching NotionExportResult.Failure(
                        "Notion API: ${response.code} $bodyStr".take(200)
                    )
                }
                JSONObject(bodyStr).optString("id").takeIf { it.isNotBlank() }
            }

            val remaining = blocks.drop(MAX_BLOCKS_PER_REQUEST)
            if (remaining.isEmpty()) {
                NotionExportResult.Success
            } else if (pageId == null) {
                NotionExportResult.Failure("Notion utworzył stronę, ale nie zwrócił jej ID — dopisano tylko pierwsze $MAX_BLOCKS_PER_REQUEST wypowiedzi")
            } else {
                appendRemainingBlocks(pageId, remaining, settings)
            }
        }.getOrElse { e -> NotionExportResult.Failure(e.message ?: "Nie udało się połączyć z Notion") }
    }

    /**
     * Adds the rest of a long transcript to an already-created page, 100 blocks
     * at a time. A failure part-way leaves a page with a partial transcript, so
     * the message says so explicitly — retrying would create a second page
     * rather than resume this one.
     */
    private fun appendRemainingBlocks(
        pageId: String,
        remaining: List<JSONObject>,
        settings: SettingsState
    ): NotionExportResult {
        remaining.chunked(MAX_BLOCKS_PER_REQUEST).forEachIndexed { chunkIndex, chunk ->
            val payload = JSONObject().put(
                "children",
                chunk.fold(JSONArray()) { arr, b -> arr.put(b) }
            )
            val request = Request.Builder()
                .url("https://api.notion.com/v1/blocks/$pageId/children")
                .addHeader("Authorization", "Bearer ${settings.notionToken}")
                .addHeader("Notion-Version", notionVersion)
                .patch(payload.toString().toRequestBody(jsonMedia))
                .build()

            val failure = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) null
                else "Notion API: ${response.code} ${response.body?.string().orEmpty()}".take(200)
            }
            if (failure != null) {
                val written = MAX_BLOCKS_PER_REQUEST * (chunkIndex + 1)
                AppLog.e("Notion", "Dopisywanie bloków przerwane po $written wypowiedziach: $failure")
                return NotionExportResult.Failure(
                    "Strona powstała, ale zapisano tylko pierwsze $written wypowiedzi. $failure"
                )
            }
        }
        return NotionExportResult.Success
    }

    private fun paragraphBlock(text: String): JSONObject = JSONObject().apply {
        put("object", "block")
        put("type", "paragraph")
        put("paragraph", JSONObject().put("rich_text", JSONArray().put(
            JSONObject().put("type", "text").put("text", JSONObject().put("content", text.take(2000)))
        )))
    }

    /** Looks up the database's actual title-property name — it isn't always "Name". */
    private fun resolveTitlePropertyName(settings: SettingsState): String? {
        val request = Request.Builder()
            .url("https://api.notion.com/v1/databases/${settings.notionDatabaseId}")
            .addHeader("Authorization", "Bearer ${settings.notionToken}")
            .addHeader("Notion-Version", notionVersion)
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string().orEmpty())
                val props = json.optJSONObject("properties") ?: return@use null
                props.keys().asSequence().firstOrNull { key ->
                    props.getJSONObject(key).optString("type") == "title"
                }
            }
        }.getOrNull()
    }
}
