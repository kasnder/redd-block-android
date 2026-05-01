package net.kollnig.reddblockandroid.assistant

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    fun requestAssistantTurn(
        apiKey: String,
        model: String,
        userContextJson: String,
        installedApps: List<InstalledAppSummary>
    ): AssistantResult {
        val requestJson = JSONObject()
            .put("model", model)
            .put("instructions", SystemPrompt.TEXT)
            .put("input", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", JSONArray().put(
                        JSONObject()
                            .put("type", "input_text")
                            .put("text", userContextJson)
                    ))
            ))
            .put("tools", JSONArray().put(JSONObject(SystemPrompt.proposeScheduleToolJson())))
            .put("tool_choice", "auto")

        val request = Request.Builder()
            .url(RESPONSES_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(errorMessage(response.code, body))
            }
            return parseResponse(JSONObject(body), installedApps)
        }
    }

    fun validateKey(apiKey: String, model: String) {
        val requestJson = JSONObject()
            .put("model", model)
            .put("input", "Reply with OK.")
            .put("max_output_tokens", 16)

        val request = Request.Builder()
            .url(RESPONSES_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(errorMessage(response.code, body))
            }
        }
    }

    private fun parseResponse(response: JSONObject, installedApps: List<InstalledAppSummary>): AssistantResult {
        val output = response.optJSONArray("output") ?: JSONArray()
        val validator = ToolValidator(installedApps)
        var fallbackText = response.optString("output_text", "")

        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            when (item.optString("type")) {
                "function_call" -> {
                    if (item.optString("name") == "propose_schedule") {
                        val proposal = validator.parseAndValidate(item.getString("arguments")).getOrThrow()
                        val text = proposal.rationale.ifBlank { "I drafted a new schedule for review." }
                        return AssistantResult.Proposal(text, proposal)
                    }
                }
                "message" -> {
                    fallbackText = extractMessageText(item).ifBlank { fallbackText }
                }
            }
        }

        return AssistantResult.Message(fallbackText.ifBlank {
            "I need one more detail before I can suggest a schedule."
        })
    }

    private fun extractMessageText(message: JSONObject): String {
        val content = message.optJSONArray("content") ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            if (item.optString("type") == "output_text") {
                parts.add(item.optString("text"))
            }
        }
        return parts.joinToString("\n").trim()
    }

    private fun errorMessage(code: Int, body: String): String {
        val apiMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return apiMessage?.takeIf { it.isNotBlank() } ?: "OpenAI request failed with HTTP $code."
    }

    companion object {
        private const val RESPONSES_URL = "https://api.openai.com/v1/responses"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

