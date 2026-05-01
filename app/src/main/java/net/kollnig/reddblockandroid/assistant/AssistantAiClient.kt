package net.kollnig.reddblockandroid.assistant

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AssistantAiClient private constructor(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val provider: AssistantAiProvider
) {
    constructor() : this(
        httpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build(),
        provider = AssistantAiProvider.NEBIUS_CHAT_COMPLETIONS
    )

    fun requestAssistantTurn(
        apiKey: String,
        model: String,
        userContextJson: String,
        installedApps: List<InstalledAppSummary>
    ): AssistantResult {
        val retryContext = provider.buildRetryContext(model, userContextJson)
        val body = executeRequest(apiKey, provider.assistantRequest(model, retryContext.payload))
        return try {
            provider.parseResponse(JSONObject(body), installedApps)
        } catch (e: MalformedAssistantResponse) {
            val retryPayload = retryContext.withRepairInstruction(repairInstruction(e))
            provider.parseResponse(JSONObject(executeRequest(apiKey, provider.assistantRequest(model, retryPayload))), installedApps)
        }
    }

    fun validateKey(apiKey: String, model: String) {
        executeRequest(apiKey, provider.validationRequest(model))
    }

    private fun executeRequest(apiKey: String, requestJson: JSONObject): String {
        val request = Request.Builder()
            .url(provider.url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(provider.errorMessage(response.code, body))
            }
            return body
        }
    }

    private fun repairInstruction(error: MalformedAssistantResponse): String {
        return """
Your previous response could not be applied by the app.
Validation error: ${error.message.orEmpty().take(MAX_REPAIR_ERROR_CHARS)}

Return either concise plain text if you need clarification, or call exactly one of the provided tools with valid JSON arguments matching the schema. Do not mention this repair step to the user.
""".trimIndent()
    }

    companion object {
        private const val MAX_REPAIR_ERROR_CHARS = 800
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private enum class AssistantAiProvider(
    val url: String,
    private val apiName: String
) {
    NEBIUS_CHAT_COMPLETIONS(
        url = "https://api.tokenfactory.nebius.com/v1/chat/completions",
        apiName = "Nebius"
    ) {
        override fun buildRetryContext(model: String, userContextJson: String): RetryContext {
            return RetryContext.ChatCompletion(
                JSONArray()
                    .put(JSONObject()
                        .put("role", "system")
                        .put("content", SystemPrompt.TEXT)
                    )
                    .put(JSONObject()
                        .put("role", "user")
                        .put("content", userContextJson)
                    )
            )
        }

        override fun assistantRequest(model: String, payload: Any): JSONObject = JSONObject()
            .put("model", model)
            .put("messages", payload as JSONArray)
            .put("tools", JSONArray()
                .put(chatToolJson(SystemPrompt.proposeScheduleToolJson()))
                .put(chatToolJson(SystemPrompt.proposeScheduleAmendmentToolJson()))
            )
            .put("tool_choice", "auto")

        override fun validationRequest(model: String): JSONObject = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", "Reply with OK.")
            ))
            .put("max_tokens", 16)

        override fun parseResponse(response: JSONObject, installedApps: List<InstalledAppSummary>): AssistantResult {
            val message = response.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?: JSONObject()
            val fallbackText = message.optString("content", "")
            val toolCalls = message.optJSONArray("tool_calls") ?: JSONArray()
            return parseToolCallsOrMessage(toolCalls, fallbackText, installedApps)
        }
    },
    OPENAI_RESPONSES(
        url = "https://api.openai.com/v1/responses",
        apiName = "OpenAI"
    ) {
        override fun buildRetryContext(model: String, userContextJson: String): RetryContext {
            return RetryContext.ResponsesInput(
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", JSONArray().put(
                            JSONObject()
                                .put("type", "input_text")
                                .put("text", userContextJson)
                        ))
                )
            )
        }

        override fun assistantRequest(model: String, payload: Any): JSONObject = JSONObject()
            .put("model", model)
            .put("instructions", SystemPrompt.TEXT)
            .put("input", payload as JSONArray)
            .put("tools", JSONArray()
                .put(JSONObject(SystemPrompt.proposeScheduleToolJson()))
                .put(JSONObject(SystemPrompt.proposeScheduleAmendmentToolJson()))
            )
            .put("tool_choice", "auto")

        override fun validationRequest(model: String): JSONObject = JSONObject()
            .put("model", model)
            .put("input", "Reply with OK.")
            .put("max_output_tokens", 16)

        override fun parseResponse(response: JSONObject, installedApps: List<InstalledAppSummary>): AssistantResult {
            val output = response.optJSONArray("output") ?: JSONArray()
            var fallbackText = response.optString("output_text", "")

            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                when (item.optString("type")) {
                    "function_call" -> {
                        val toolCalls = JSONArray().put(
                            JSONObject().put(
                                "function",
                                JSONObject()
                                    .put("name", item.optString("name"))
                                    .put("arguments", item.optString("arguments"))
                            )
                        )
                        return parseToolCallsOrMessage(toolCalls, fallbackText, installedApps)
                    }
                    "message" -> {
                        fallbackText = extractResponsesMessageText(item).ifBlank { fallbackText }
                    }
                }
            }

            return AssistantResult.Message(fallbackText.ifBlank {
                "I need one more detail before I can suggest a schedule."
            })
        }
    };

    abstract fun buildRetryContext(model: String, userContextJson: String): RetryContext
    abstract fun assistantRequest(model: String, payload: Any): JSONObject
    abstract fun validationRequest(model: String): JSONObject
    abstract fun parseResponse(response: JSONObject, installedApps: List<InstalledAppSummary>): AssistantResult

    fun errorMessage(code: Int, body: String): String {
        val apiMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return apiMessage?.takeIf { it.isNotBlank() } ?: "$apiName request failed with HTTP $code."
    }

    protected fun parseToolCallsOrMessage(
        toolCalls: JSONArray,
        fallbackText: String,
        installedApps: List<InstalledAppSummary>
    ): AssistantResult {
        val validator = ToolValidator(installedApps)
        for (i in 0 until toolCalls.length()) {
            val function = toolCalls.optJSONObject(i)?.optJSONObject("function") ?: continue
            when (function.optString("name")) {
                "propose_schedule" -> {
                    val proposal = validator.parseAndValidate(function.getString("arguments")).getOrElse {
                        throw MalformedAssistantResponse("Invalid propose_schedule arguments: ${it.message}")
                    }
                    val text = proposal.rationale.ifBlank { "I drafted a new schedule for review." }
                    return AssistantResult.Proposal(text, proposal)
                }
                "propose_schedule_amendment" -> {
                    val amendment = validator.parseAndValidateAmendment(function.getString("arguments")).getOrElse {
                        throw MalformedAssistantResponse("Invalid propose_schedule_amendment arguments: ${it.message}")
                    }
                    val text = amendment.rationale.ifBlank { "I drafted changes to an existing schedule for review." }
                    return AssistantResult.Amendment(text, amendment)
                }
                else -> throw MalformedAssistantResponse("Unknown tool call: ${function.optString("name")}")
            }
        }

        return AssistantResult.Message(fallbackText.ifBlank {
            "I need one more detail before I can suggest a schedule."
        })
    }

    protected fun chatToolJson(toolJson: String): JSONObject {
        val tool = JSONObject(toolJson)
        return JSONObject()
            .put("type", "function")
            .put("function", tool)
    }
}

private sealed class RetryContext {
    abstract val payload: Any
    abstract fun withRepairInstruction(instruction: String): Any

    data class ChatCompletion(override val payload: JSONArray) : RetryContext() {
        override fun withRepairInstruction(instruction: String): JSONArray {
            return JSONArray(payload.toString())
                .put(JSONObject()
                    .put("role", "user")
                    .put("content", instruction)
                )
        }
    }

    data class ResponsesInput(override val payload: JSONArray) : RetryContext() {
        override fun withRepairInstruction(instruction: String): JSONArray {
            return JSONArray(payload.toString())
                .put(JSONObject()
                    .put("role", "user")
                    .put("content", JSONArray().put(
                        JSONObject()
                            .put("type", "input_text")
                            .put("text", instruction)
                    ))
                )
        }
    }
}

private fun extractResponsesMessageText(message: JSONObject): String {
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

private class MalformedAssistantResponse(message: String) : IOException(message)
