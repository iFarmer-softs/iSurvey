package asia.ifarmer.isurvey.engine

import asia.ifarmer.isurvey.model.GroupJumpDto
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.util.Locale

object RuleEvaluator {

    /**
     * Evaluates whether a group jump trigger condition is satisfied by the user's answer.
     * Compatible with evaluateJumpTrigger in in-app-survey-engine's MobilePreviewModal.
     * Supports: match, not_match, else, greater, less, greater_or_equal, less_or_equal.
     */
    fun evaluateJumpTrigger(jump: GroupJumpDto, answer: String?): Boolean {
        if (answer.isNullOrBlank()) return false
        val trimmedAns = answer.trim()
        val lowerAns = trimmedAns.lowercase(Locale.ROOT)
        val trig = (jump.triggerValue ?: "").trim()
        val lowerTrig = trig.lowercase(Locale.ROOT)
        val trigType = (jump.triggerType ?: "match").lowercase(Locale.ROOT)

        when (trigType) {
            "greater" -> {
                val num = trimmedAns.toDoubleOrNull()
                val threshold = trig.toDoubleOrNull()
                return num != null && threshold != null && num > threshold
            }
            "less" -> {
                val num = trimmedAns.toDoubleOrNull()
                val threshold = trig.toDoubleOrNull()
                return num != null && threshold != null && num < threshold
            }
            "greater_or_equal" -> {
                val num = trimmedAns.toDoubleOrNull()
                val threshold = trig.toDoubleOrNull()
                return num != null && threshold != null && num >= threshold
            }
            "less_or_equal" -> {
                val num = trimmedAns.toDoubleOrNull()
                val threshold = trig.toDoubleOrNull()
                return num != null && threshold != null && num <= threshold
            }
            "not_match", "else" -> {
                if (trig.isBlank() || trig.equals("else", ignoreCase = true)) return true
                val opts = trig.split(",").map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
                if (opts.size > 1) {
                    return opts.none { lowerAns == it || lowerAns.contains(it) }
                }
                return lowerAns != lowerTrig && !lowerAns.contains(lowerTrig)
            }
            else -> { // "match"
                if (trig.isBlank()) return true
                val opts = trig.split(",").map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
                if (opts.isNotEmpty()) {
                    return opts.any { lowerAns == it || lowerAns.contains(it) || it.contains(lowerAns) }
                }
                return lowerAns == lowerTrig || lowerAns.contains(lowerTrig) || lowerTrig.contains(lowerAns)
            }
        }
    }

    /**
     * Robust rule evaluator handling match maps, wildcard, negation/else, numeric, and string comparisons.
     * Compatible with evaluateRule in in-app-survey-engine's MobilePreviewModal.
     */
    fun evaluateRule(ruleConfig: Any?, parentAnswer: String?): Boolean {
        if (parentAnswer.isNullOrBlank()) return false
        if (ruleConfig == null) return true

        val trimmedParent = parentAnswer.trim()
        val lowerParent = trimmedParent.lowercase(Locale.ROOT)

        // If ruleConfig is raw String or Number (not a JSON object)
        if (ruleConfig is Number || (ruleConfig is String && !ruleConfig.trim().startsWith("{") && !ruleConfig.trim().startsWith("["))) {
            val s = ruleConfig.toString().trim()
            if (s.isEmpty()) return true
            if (s.startsWith(">=")) {
                val numVal = trimmedParent.toDoubleOrNull()
                val threshold = s.substring(2).trim().toDoubleOrNull()
                return numVal != null && threshold != null && numVal >= threshold
            }
            if (s.startsWith("<=")) {
                val numVal = trimmedParent.toDoubleOrNull()
                val threshold = s.substring(2).trim().toDoubleOrNull()
                return numVal != null && threshold != null && numVal <= threshold
            }
            if (s.startsWith(">")) {
                val numVal = trimmedParent.toDoubleOrNull()
                val threshold = s.substring(1).trim().toDoubleOrNull()
                return numVal != null && threshold != null && numVal > threshold
            }
            if (s.startsWith("<")) {
                val numVal = trimmedParent.toDoubleOrNull()
                val threshold = s.substring(1).trim().toDoubleOrNull()
                return numVal != null && threshold != null && numVal < threshold
            }
            if (s.startsWith("!=") || s.startsWith("NOT ", ignoreCase = true)) {
                val target = s.replace(Regex("^(?:!=|NOT\\s*)", RegexOption.IGNORE_CASE), "").trim().lowercase(Locale.ROOT)
                return lowerParent != target && !lowerParent.contains(target)
            }
            val lowerS = s.lowercase(Locale.ROOT)
            return lowerParent == lowerS || lowerParent.contains(lowerS)
        }

        // Parse ruleConfig as JsonElement using Gson
        val jsonElement: JsonElement = try {
            when (ruleConfig) {
                is JsonElement -> ruleConfig
                else -> JsonParser.parseString(ruleConfig.toString())
            }
        } catch (e: Exception) {
            return true
        }

        if (!jsonElement.isJsonObject) {
            if (jsonElement.isJsonPrimitive) {
                val s = jsonElement.asString.trim()
                return lowerParent == s.lowercase(Locale.ROOT) || lowerParent.contains(s.lowercase(Locale.ROOT))
            }
            return true
        }

        val jsonConfig = jsonElement.asJsonObject
        val vType = if (jsonConfig.has("validation_type")) jsonConfig.get("validation_type").asString.lowercase(Locale.ROOT) else "match"

        when (vType) {
            "greater" -> {
                val num = trimmedParent.toDoubleOrNull()
                val threshold = try { jsonConfig.get("validation")?.asString?.toDoubleOrNull() } catch (e: Exception) { null }
                return num != null && threshold != null && num > threshold
            }
            "less" -> {
                val num = trimmedParent.toDoubleOrNull()
                val threshold = try { jsonConfig.get("validation")?.asString?.toDoubleOrNull() } catch (e: Exception) { null }
                return num != null && threshold != null && num < threshold
            }
            "greater_or_equal" -> {
                val num = trimmedParent.toDoubleOrNull()
                val threshold = try { jsonConfig.get("validation")?.asString?.toDoubleOrNull() } catch (e: Exception) { null }
                return num != null && threshold != null && num >= threshold
            }
            "less_or_equal" -> {
                val num = trimmedParent.toDoubleOrNull()
                val threshold = try { jsonConfig.get("validation")?.asString?.toDoubleOrNull() } catch (e: Exception) { null }
                return num != null && threshold != null && num <= threshold
            }
            "not_match" -> {
                val targetObj = jsonConfig.get("validation")
                val trig = try { targetObj?.asString?.trim()?.lowercase(Locale.ROOT) ?: "" } catch (e: Exception) { "" }
                return lowerParent != trig && !lowerParent.contains(trig)
            }
            "contains" -> {
                val targetObj = jsonConfig.get("validation")
                val trig = try { targetObj?.asString?.trim()?.lowercase(Locale.ROOT) ?: "" } catch (e: Exception) { "" }
                return lowerParent.contains(trig)
            }
            else -> { // match
                val targetVal = if (jsonConfig.has("validation") && !jsonConfig.get("validation").isJsonNull) {
                    jsonConfig.get("validation")
                } else {
                    jsonConfig
                }

                if (targetVal.isJsonObject) {
                    val obj = targetVal.asJsonObject
                    val falsyKeys = mutableListOf<String>()
                    val truthyKeys = mutableListOf<String>()
                    val metaKeys = setOf("validation_type", "is_same_group", "group", "logical_operator", "active", "enabled")

                    for (entry in obj.entrySet()) {
                        val key = entry.key
                        if (metaKeys.contains(key.lowercase(Locale.ROOT))) continue
                        val v = entry.value
                        val isTrue = when {
                            v.isJsonPrimitive && v.asJsonPrimitive.isBoolean -> v.asBoolean
                            v.isJsonPrimitive && v.asJsonPrimitive.isNumber -> v.asInt != 0
                            v.isJsonPrimitive && v.asJsonPrimitive.isString -> v.asString.equals("true", ignoreCase = true)
                            else -> false
                        }
                        if (isTrue) {
                            truthyKeys.add(key.trim())
                        } else {
                            falsyKeys.add(key.trim())
                        }
                    }

                    // If pure negation/else (e.g. { "Yes": false } without truthy keys)
                    if (truthyKeys.isEmpty() && falsyKeys.isNotEmpty()) {
                        return falsyKeys.none { k ->
                            val lowerKey = k.lowercase(Locale.ROOT)
                            lowerParent == lowerKey || lowerParent.contains(lowerKey)
                        }
                    }

                    // If only metadata was present and targetVal was jsonConfig, it's unconditional
                    if (truthyKeys.isEmpty() && falsyKeys.isEmpty()) {
                        return true
                    }

                    if (truthyKeys.contains("")) return true // Wildcard match

                    val matchesTruthy = truthyKeys.any { k ->
                        val lowerKey = k.lowercase(Locale.ROOT)
                        lowerParent == lowerKey || lowerParent.contains(lowerKey) || lowerKey.contains(lowerParent)
                    }

                    if (!matchesTruthy) return false

                    val matchesFalsy = falsyKeys.any { k ->
                        val lowerKey = k.lowercase(Locale.ROOT)
                        lowerParent == lowerKey
                    }

                    return !matchesFalsy
                }

                if (targetVal.isJsonArray) {
                    val arr = targetVal.asJsonArray
                    for (i in 0 until arr.size()) {
                        val s = arr.get(i).asString.trim().lowercase(Locale.ROOT)
                        if (lowerParent == s || lowerParent.contains(s)) return true
                    }
                    return false
                }

                val strTarget = if (targetVal.isJsonPrimitive) targetVal.asString.trim() else ""
                if (strTarget.isEmpty()) return true

                if (strTarget.startsWith(">=")) {
                    val numVal = trimmedParent.toDoubleOrNull()
                    val threshold = strTarget.substring(2).trim().toDoubleOrNull()
                    return numVal != null && threshold != null && numVal >= threshold
                }
                if (strTarget.startsWith("<=")) {
                    val numVal = trimmedParent.toDoubleOrNull()
                    val threshold = strTarget.substring(2).trim().toDoubleOrNull()
                    return numVal != null && threshold != null && numVal <= threshold
                }
                if (strTarget.startsWith(">")) {
                    val numVal = trimmedParent.toDoubleOrNull()
                    val threshold = strTarget.substring(1).trim().toDoubleOrNull()
                    return numVal != null && threshold != null && numVal > threshold
                }
                if (strTarget.startsWith("<")) {
                    val numVal = trimmedParent.toDoubleOrNull()
                    val threshold = strTarget.substring(1).trim().toDoubleOrNull()
                    return numVal != null && threshold != null && numVal < threshold
                }

                val lowerTarget = strTarget.lowercase(Locale.ROOT)
                return lowerParent == lowerTarget || lowerParent.contains(lowerTarget) || lowerTarget.contains(lowerParent)
            }
        }
    }
}
