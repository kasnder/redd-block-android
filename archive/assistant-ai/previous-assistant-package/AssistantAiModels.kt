package net.kollnig.reddblockandroid.assistant

object AssistantAiModels {
    const val DEFAULT_MODEL = "Qwen/Qwen3-235B-A22B-Instruct-2507"

    val PRESETS = listOf(
        Preset(
            id = DEFAULT_MODEL,
            label = "Qwen 235B",
            description = "Balanced"
        ),
        Preset(
            id = "nvidia/Nemotron-3-Nano-Omni",
            label = "Nemotron Nano",
            description = "Cheapest"
        ),
        Preset(
            id = "openai/gpt-oss-120b",
            label = "GPT OSS 120B",
            description = "Alternative"
        )
    )

    private val presetIds = PRESETS.map { it.id }.toSet()

    fun normalize(model: String): String {
        return model.trim().takeIf { it in presetIds } ?: DEFAULT_MODEL
    }

    data class Preset(
        val id: String,
        val label: String,
        val description: String
    )
}
