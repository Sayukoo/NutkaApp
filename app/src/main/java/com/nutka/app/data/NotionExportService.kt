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

    fun export(recording: Recording, settings: SettingsState): NotionExportResult {
        if (settings.notionToken.isBlank() || settings.notionDatabaseId.isBlank()) {
            return NotionExportResult.Failure("Skonfiguruj token integracji i ID bazy danych Notion w Ustawieniach")
        }
        val titleProperty = resolveTitlePropertyName(settings) ?: "Name"

        val title = recording.title?.takeIf { it.isNotBlank() }
            ?: "Nagranie ${SimpleDateFormat("d MMM yyyy, HH:mm", Locale("pl")).format(Date(recording.createdAtMillis))}"

        val children = JSONArray().apply {
            if (recording.segments.isEmpty()) {
                put(paragraphBlock("(Brak transkrypcji)"))
            }
            recording.segments.forEach { seg ->
                val speakerName = recording.speakerNames[seg.speaker] ?: seg.speakerLabel ?: "Mówca"
                put(paragraphBlock("$speakerName: ${seg.text}"))
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
            put("children", children)
        }

        val request = Request.Builder()
            .url("https://api.notion.com/v1/pages")
            .addHeader("Authorization", "Bearer ${settings.notionToken}")
            .addHeader("Notion-Version", notionVersion)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    NotionExportResult.Success
                } else {
                    val err = response.body?.string().orEmpty()
                    NotionExportResult.Failure("Notion API: ${response.code} $err".take(200))
                }
            }
        }.getOrElse { e -> NotionExportResult.Failure(e.message ?: "Nie udało się połączyć z Notion") }
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
