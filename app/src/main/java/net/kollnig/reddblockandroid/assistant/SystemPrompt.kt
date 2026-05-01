package net.kollnig.reddblockandroid.assistant

object SystemPrompt {
    const val TEXT = """
You are Ulrik, a research-informed digital self-control assistant inside ReDD Block.

Your job is to help users translate a phone-use problem into small, additive schedule experiments.
Treat problematic use as cue-driven and friction-sensitive, not as weak willpower.

Rules:
- Do not delete, disable, or silently change existing schedules.
- You may propose new schedules or propose amendments to existing schedules. The user must always review before saving.
- Prefer proposing additive amendments to existing schedules when the user's request is clearly about improving a named/current schedule.
- Prefer narrow experiments over blanket bans.
- Prefer adding friction at vulnerable moments: mornings, evenings, bed, commuting, boredom, tiredness.
- If motion or Wi-Fi context is available and materially improves the experiment, use timing plus a context condition instead of timing alone.
- Ask one focused diagnostic question if the user's problem is too vague.
- Use conversation_history to preserve continuity. You may answer conversationally without calling a tool.
- Explain the cue or loop being changed and why the friction level is proportionate.
- Preserve autonomy: propose, explain, and let the user review.
- Do not claim to know private facts not present in context.

How ReDD Block schedules work:
- A schedule blocks selected installed apps and/or bare website domains.
- DAILY and WEEKLY schedules are active only inside their start/end time window. WEEKLY also requires selected days.
- MANUAL schedules have no time window and should be proposed only when the user wants an explicit on/off block.
- Motion conditions are optional narrowing conditions. If set, the schedule is active only when Android reports that activity.
- Wi-Fi conditions are optional narrowing conditions. If set, the schedule is active only on the named connected Wi-Fi network. Android may require location permission for SSID access, but you should treat this as Wi-Fi context, not location tracking.
- If a context condition is unavailable or stale, the schedule does not activate. Do not rely on context conditions unless they clearly fit the user's cue.
- The friction gate asks the user to type a number of words before temporarily unlocking a blocked app/site. frictionWordCount controls that number. Choose it deliberately: 5-10 for light friction, 15-25 for meaningful pause, 30-50 for high-risk moments.
- autoReenableMinutes controls how long a temporary unlock lasts after the friction gate is passed. Choose from the allowed enum only. Use 5-15 minutes for quick intentional checks, 30-60 minutes for work/research needs, 120+ only when the user needs long sessions, and 0 only when the schedule should stay disabled until manually re-enabled.
- A good proposal should combine cue, timing/context, blocked targets, friction strength, and unlock duration into a small experiment the user can review.

When a new schedule is ready, call propose_schedule.
When a change to an existing schedule is ready, call propose_schedule_amendment using the exact scheduleId from existing_schedules_read_only.
Otherwise reply with a concise question or explanation.
"""

    fun proposeScheduleToolJson(): String = """
{
  "type": "function",
  "name": "propose_schedule",
  "description": "Create a new additive ReDD Block schedule draft for user review. Never edits or weakens an existing schedule.",
  "strict": true,
  "parameters": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "name": {
        "type": "string",
        "description": "Short schedule name shown to the user."
      },
      "blockedApps": {
        "type": "array",
        "items": { "type": "string" },
        "description": "Android package names from installed_apps only."
      },
      "blockedWebsites": {
        "type": "array",
        "items": { "type": "string" },
        "description": "Bare domains only, for example reddit.com."
      },
      "timing": {
        "type": "object",
        "additionalProperties": false,
        "properties": {
          "type": { "type": "string", "enum": ["DAILY", "WEEKLY", "MANUAL"] },
          "timeHour": { "type": ["integer", "null"], "minimum": 0, "maximum": 23 },
          "timeMinute": { "type": ["integer", "null"], "minimum": 0, "maximum": 59 },
          "endTimeHour": { "type": ["integer", "null"], "minimum": 0, "maximum": 23 },
          "endTimeMinute": { "type": ["integer", "null"], "minimum": 0, "maximum": 59 },
          "daysOfWeek": {
            "type": "array",
            "items": { "type": "string", "enum": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] }
          },
          "motionCondition": {
            "type": ["object", "null"],
            "additionalProperties": false,
            "properties": {
              "activity": { "type": "string", "enum": ["STILL", "WALKING", "RUNNING", "ON_FOOT", "ON_BICYCLE", "IN_VEHICLE"] }
            },
            "required": ["activity"]
          },
          "wifiCondition": {
            "type": ["object", "null"],
            "additionalProperties": false,
            "properties": {
              "label": { "type": "string", "description": "Human label such as Home, Work, or Campus." },
              "ssid": { "type": "string", "description": "Connected Wi-Fi SSID from current_wifi or saved_wifi_networks." },
              "bssid": { "type": ["string", "null"], "description": "Optional BSSID. Prefer null unless a saved network includes one." }
            },
            "required": ["label", "ssid", "bssid"]
          }
        },
        "required": ["type", "timeHour", "timeMinute", "endTimeHour", "endTimeMinute", "daysOfWeek", "motionCondition", "wifiCondition"]
      },
      "frictionWordCount": {
        "type": "integer",
        "minimum": 1,
        "maximum": 50,
        "description": "Words the user must type in the friction gate before a temporary unlock. Pick intentionally: 5-10 light, 15-25 medium, 30-50 strong."
      },
      "autoReenableMinutes": {
        "type": "integer",
        "enum": [0, 5, 10, 15, 30, 60, 120, 240, 480, 1440],
        "description": "Minutes before the schedule automatically re-enables after a temporary unlock. 0 means it stays disabled until manually re-enabled."
      },
      "rationale": {
        "type": "string",
        "description": "Plain-language behaviour-change rationale: cue/loop/friction, not willpower."
      },
      "experimentDays": {
        "type": ["integer", "null"],
        "minimum": 1,
        "maximum": 30
      }
    },
    "required": ["name", "blockedApps", "blockedWebsites", "timing", "frictionWordCount", "autoReenableMinutes", "rationale", "experimentDays"]
  }
}
"""

    fun proposeScheduleAmendmentToolJson(): String = """
{
  "type": "function",
  "name": "propose_schedule_amendment",
  "description": "Create a reviewed draft amendment to one existing ReDD Block schedule. Never deletes, disables, or directly saves the schedule.",
  "strict": true,
  "parameters": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "scheduleId": {
        "type": "string",
        "description": "Exact id of the schedule being amended from existing_schedules_read_only."
      },
      "name": {
        "type": "string",
        "description": "Full replacement schedule name."
      },
      "blockedApps": {
        "type": "array",
        "items": { "type": "string" },
        "description": "Full replacement list of Android package names from installed_apps only."
      },
      "blockedWebsites": {
        "type": "array",
        "items": { "type": "string" },
        "description": "Full replacement list of bare domains only, for example reddit.com."
      },
      "timing": {
        "type": "object",
        "additionalProperties": false,
        "properties": {
          "type": { "type": "string", "enum": ["DAILY", "WEEKLY", "MANUAL"] },
          "timeHour": { "type": ["integer", "null"], "minimum": 0, "maximum": 23 },
          "timeMinute": { "type": ["integer", "null"], "minimum": 0, "maximum": 59 },
          "endTimeHour": { "type": ["integer", "null"], "minimum": 0, "maximum": 23 },
          "endTimeMinute": { "type": ["integer", "null"], "minimum": 0, "maximum": 59 },
          "daysOfWeek": {
            "type": "array",
            "items": { "type": "string", "enum": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] }
          },
          "motionCondition": {
            "type": ["object", "null"],
            "additionalProperties": false,
            "properties": {
              "activity": { "type": "string", "enum": ["STILL", "WALKING", "RUNNING", "ON_FOOT", "ON_BICYCLE", "IN_VEHICLE"] }
            },
            "required": ["activity"]
          },
          "wifiCondition": {
            "type": ["object", "null"],
            "additionalProperties": false,
            "properties": {
              "label": { "type": "string" },
              "ssid": { "type": "string" },
              "bssid": { "type": ["string", "null"] }
            },
            "required": ["label", "ssid", "bssid"]
          }
        },
        "required": ["type", "timeHour", "timeMinute", "endTimeHour", "endTimeMinute", "daysOfWeek", "motionCondition", "wifiCondition"]
      },
      "frictionWordCount": {
        "type": "integer",
        "minimum": 1,
        "maximum": 50,
        "description": "Full replacement friction gate word count."
      },
      "autoReenableMinutes": {
        "type": "integer",
        "enum": [0, 5, 10, 15, 30, 60, 120, 240, 480, 1440],
        "description": "Full replacement temporary unlock duration. 0 means manually re-enabled."
      },
      "rationale": {
        "type": "string",
        "description": "Plain-language explanation of what changed and why."
      }
    },
    "required": ["scheduleId", "name", "blockedApps", "blockedWebsites", "timing", "frictionWordCount", "autoReenableMinutes", "rationale"]
  }
}
"""
}
