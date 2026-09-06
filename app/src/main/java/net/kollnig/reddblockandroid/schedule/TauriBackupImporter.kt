package net.kollnig.reddblockandroid.schedule

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import java.math.BigDecimal
import java.time.DayOfWeek
import java.util.UUID

/** Converts the Tauri redd-block-rules backup format into native schedule drafts. */
internal class TauriBackupImporter(
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    /**
     * Returns converted schedules for a recognised Tauri payload, or null when the payload is
     * not a Tauri backup. Structural errors in a recognised payload are reported to the caller.
     */
    fun importSchedules(jsonString: String): List<Schedule>? {
        val root = JsonParser.parseString(jsonString)
        if (!root.isJsonObject) return null

        val rootObject = root.asJsonObject
        if (!isTauriPayload(rootObject)) return null

        validateVersion(rootObject)
        val blocklists = rootObject.getAsJsonArray("blocklists")
        return blocklists.flatMapIndexed { index, blocklistElement ->
            if (!blocklistElement.isJsonObject) {
                throw IllegalArgumentException("Tauri blocklist at index $index is not an object")
            }
            convertBlocklist(blocklistElement.asJsonObject, index)
        }
    }

    private fun isTauriPayload(root: JsonObject): Boolean {
        val format = root.get("format")
        return format?.isJsonPrimitive == true &&
            format.asJsonPrimitive.isString &&
            format.asString == FORMAT &&
            root.get("blocklists")?.isJsonArray == true
    }

    private fun validateVersion(root: JsonObject) {
        val version = root.get("formatVersion") ?: return
        if (!version.isJsonPrimitive || !version.asJsonPrimitive.isNumber) {
            throw IllegalArgumentException("Tauri formatVersion must be numeric")
        }

        val numericVersion = try {
            BigDecimal(version.asJsonPrimitive.asString)
        } catch (exception: NumberFormatException) {
            throw IllegalArgumentException("Tauri formatVersion must be numeric", exception)
        }
        if (numericVersion > MAX_SUPPORTED_VERSION) {
            throw IllegalArgumentException("Unsupported Tauri formatVersion: $numericVersion")
        }
    }

    private fun convertBlocklist(blocklist: JsonObject, index: Int): List<Schedule> {
        val modeElement = blocklist.get("mode")
        if (modeElement != null && !modeElement.isJsonNull) {
            if (!modeElement.isJsonPrimitive || !modeElement.asJsonPrimitive.isString) {
                return emptyList()
            }
            if (modeElement.asString != BLOCKLIST_MODE) return emptyList()
        } else if (modeElement != null) {
            return emptyList()
        }

        val blockedApps = readStringList(blocklist, "apps")
        val blockedWebsites = readStringList(blocklist, "websites")
        val rawName = blocklist.get("name")
        val name = if (rawName?.isJsonPrimitive == true && rawName.asJsonPrimitive.isString) {
            rawName.asString.trim()
        } else {
            ""
        }
        val importedName = name.takeIf { it.isNotBlank() } ?: DEFAULT_NAME

        if (name.isBlank() && blockedApps.isEmpty() && blockedWebsites.isEmpty()) {
            return emptyList()
        }

        val frictionWordCount = readPositiveInteger(
            blocklist.getAsJsonObjectOrNull("overrideDifficulty")?.get("count")
        ) ?: DEFAULT_FRICTION_WORD_COUNT

        val scheduleElement = blocklist.get("schedule")
        if (scheduleElement == null) {
            return listOf(
                nativeSchedule(
                    name = importedName,
                    timing = ScheduleTiming(ScheduleTiming.ScheduleType.MANUAL),
                    blockedApps = blockedApps,
                    blockedWebsites = blockedWebsites,
                    frictionWordCount = frictionWordCount
                )
            )
        }
        if (!scheduleElement.isJsonObject) {
            throw IllegalArgumentException("Tauri schedule at blocklist index $index is not an object")
        }

        val segmentsElement = scheduleElement.asJsonObject.get("segments")
        if (segmentsElement == null) return emptyList()
        if (!segmentsElement.isJsonArray) {
            throw IllegalArgumentException("Tauri schedule segments at blocklist index $index are not an array")
        }

        return segmentsElement.asJsonArray.mapNotNull { segmentElement ->
            if (!segmentElement.isJsonObject) return@mapNotNull null
            convertSegment(segmentElement.asJsonObject)
        }.mapIndexed { segmentIndex, segment ->
            nativeSchedule(
                name = if (segmentIndex == 0) importedName else "$importedName (${segmentIndex + 1})",
                timing = segment,
                blockedApps = blockedApps,
                blockedWebsites = blockedWebsites,
                frictionWordCount = frictionWordCount
            )
        }
    }

    private fun convertSegment(segment: JsonObject): ScheduleTiming? {
        val startHour = readInteger(segment.get("startHour")) ?: return null
        val startMinute = readInteger(segment.get("startMinute")) ?: return null
        val endHour = readInteger(segment.get("endHour")) ?: return null
        val endMinute = readInteger(segment.get("endMinute")) ?: return null
        if (startHour !in 0..23 || endHour !in 0..23 || startMinute !in 0..59 || endMinute !in 0..59) {
            return null
        }

        val daysElement = segment.get("days")
        if (daysElement != null && !daysElement.isJsonArray && !daysElement.isJsonNull) return null
        val days = linkedSetOf<Int>()
        daysElement?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { dayElement ->
            readInteger(dayElement)?.takeIf { it in 0..6 }?.let(days::add)
        }

        val type = if (days.isEmpty() || days.size == DAYS_IN_WEEK) {
            ScheduleTiming.ScheduleType.DAILY
        } else {
            ScheduleTiming.ScheduleType.WEEKLY
        }
        val nativeDays = if (type == ScheduleTiming.ScheduleType.WEEKLY) {
            days.map { DayOfWeek.of(it + 1) }.toSet()
        } else {
            emptySet()
        }
        return ScheduleTiming(
            type = type,
            timeHour = startHour,
            timeMinute = startMinute,
            endTimeHour = endHour,
            endTimeMinute = endMinute,
            daysOfWeek = nativeDays,
            isRecurring = true
        )
    }

    private fun nativeSchedule(
        name: String,
        timing: ScheduleTiming,
        blockedApps: List<String>,
        blockedWebsites: List<String>,
        frictionWordCount: Int
    ) = Schedule(
        id = idFactory(),
        name = name,
        isEnabled = false,
        timing = timing,
        blockedApps = blockedApps,
        blockedWebsites = blockedWebsites,
        frictionWordCount = frictionWordCount
    )

    private fun readStringList(blocklist: JsonObject, key: String): List<String> {
        val element = blocklist.get(key) ?: return emptyList()
        if (element.isJsonNull) return emptyList()
        if (!element.isJsonArray) throw IllegalArgumentException("Tauri $key is not an array")

        val values = linkedSetOf<String>()
        element.asJsonArray.forEach { entry ->
            if (entry.isJsonPrimitive && entry.asJsonPrimitive.isString) {
                entry.asString.trim().takeIf { it.isNotEmpty() }?.let(values::add)
            }
        }
        return values.toList()
    }

    private fun readPositiveInteger(element: JsonElement?): Int? = readInteger(element)?.takeIf { it > 0 }

    private fun readInteger(element: JsonElement?): Int? {
        if (element == null || element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            return null
        }
        return try {
            val decimal = BigDecimal(element.asJsonPrimitive.asString)
            decimal.setScale(0, java.math.RoundingMode.UNNECESSARY).intValueExact()
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun JsonObject.getAsJsonObjectOrNull(key: String): JsonObject? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return element.takeIf { it.isJsonObject }?.asJsonObject
    }

    private companion object {
        const val FORMAT = "redd-block-rules"
        const val BLOCKLIST_MODE = "blocklist"
        const val DEFAULT_NAME = "Imported blocklist"
        const val DEFAULT_FRICTION_WORD_COUNT = 15
        const val DAYS_IN_WEEK = 7
        val MAX_SUPPORTED_VERSION = BigDecimal("2")
    }
}
