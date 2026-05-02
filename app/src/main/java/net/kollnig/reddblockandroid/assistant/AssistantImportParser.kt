package net.kollnig.reddblockandroid.assistant

import org.json.JSONArray
import org.json.JSONObject

object AssistantImportParser {
    private val fencedBlockRegex = Regex(
        pattern = """```([A-Za-z0-9_-]+)?\s*\n([\s\S]*?)```""",
        options = setOf(RegexOption.MULTILINE)
    )

    fun extractJson(text: String): Result<String> = runCatching {
        val trimmed = text.trim()
        require(trimmed.isNotBlank()) { "Paste the full AI reply first." }

        fencedBlockRegex.findAll(trimmed).firstOrNull { match ->
            val tag = match.groupValues.getOrNull(1).orEmpty().lowercase()
            tag == "redd-block-json" || tag == "json"
        }?.let { return@runCatching it.groupValues[2].trim() }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            trimmed
        } else {
            error("Could not find a redd-block-json block. Paste the full AI reply or ask the AI to regenerate it.")
        }
    }

    fun parseActions(
        replyText: String,
        installedApps: List<InstalledAppSummary>,
        existingSchedules: () -> List<net.kollnig.reddblockandroid.data.Schedule> = { net.kollnig.reddblockandroid.schedule.Schedules.getAll() },
        scheduleById: (String) -> net.kollnig.reddblockandroid.data.Schedule? = { net.kollnig.reddblockandroid.schedule.Schedules.get(it) }
    ): Result<List<ImportedAssistantAction>> = runCatching {
        val jsonText = extractJson(replyText).getOrElse { throw it }
        val root = JSONObject(jsonText)
        require(root.optInt("version") == 1) { "The JSON version is missing or unsupported." }
        val actions = root.optJSONArray("actions") ?: JSONArray()
        require(actions.length() > 0) { "The AI reply did not include any schedule actions." }

        val validator = ToolValidator(
            installedApps = installedApps,
            existingSchedules = existingSchedules,
            scheduleById = scheduleById
        )
        (0 until actions.length()).map { index ->
            val action = actions.getJSONObject(index)
            val type = action.getString("type")
            val arguments = action.getJSONObject("arguments").toString()
            when (type) {
                "propose_schedule" -> {
                    val proposal = validator.parseAndValidate(arguments).getOrElse {
                        throw IllegalArgumentException("Action ${index + 1} is not valid: ${it.message}")
                    }
                    ImportedAssistantAction.Proposal(
                        rationale = proposal.rationale,
                        proposal = proposal
                    )
                }
                "propose_schedule_amendment" -> {
                    val amendment = validator.parseAndValidateAmendment(arguments).getOrElse {
                        throw IllegalArgumentException("Action ${index + 1} is not valid: ${it.message}")
                    }
                    ImportedAssistantAction.Amendment(
                        rationale = amendment.rationale,
                        amendment = amendment
                    )
                }
                else -> error("Action ${index + 1} uses an unsupported type: $type.")
            }
        }
    }
}
