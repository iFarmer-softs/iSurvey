package asia.ifarmer.isurvey.engine

import asia.ifarmer.isurvey.model.GroupJumpDto
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.util.Locale
import java.util.regex.Pattern

object RuleEvaluator {

    /**
     * Checks if a phone number or partial phone number matches another.
     * Supports Bangladeshi mobile formats (+8801XXXXXXXXX, 8801XXXXXXXXX, 01XXXXXXXXX, 1XXXXXXXXX),
     * space/hyphen formatting, and operator/prefix matching (e.g. 017, +88017).
     */
    fun isPhoneMatch(p: String, t: String): Boolean {
        val pDigits = p.filter { it.isDigit() }
        val tDigits = t.filter { it.isDigit() }
        if (pDigits.isEmpty() || tDigits.isEmpty()) return false

        fun extractBdCorePhone(digits: String): String? {
            if (digits.length >= 10) {
                val last10 = digits.takeLast(10)
                if (last10[0] == '1' && last10[1] in '1'..'9') {
                    return last10
                }
            }
            return null
        }

        val pCore = extractBdCorePhone(pDigits)
        val tCore = extractBdCorePhone(tDigits)

        if (pCore != null && tCore != null) {
            return pCore == tCore
        }

        fun stripPrefix(digits: String): String {
            var s = digits
            if (s.startsWith("00880")) s = s.substring(5)
            else if (s.startsWith("880")) s = s.substring(3)
            if (s.startsWith("0")) s = s.substring(1)
            return s
        }

        val pClean = stripPrefix(pDigits)
        val tClean = stripPrefix(tDigits)

        if (pClean.isNotEmpty() && tClean.isNotEmpty()) {
            if (pClean == tClean || pClean.startsWith(tClean) || tClean.startsWith(pClean)) {
                return true
            }
        }

        return false
    }

    /**
     * General value comparison supporting exact string, substring, wildcard/boolean presence,
     * and normalized phone number matching.
     */
    fun isValueMatch(parentAnswer: String, target: String): Boolean {
        val trimmedTarget = target.trim()
        val lowerTarget = trimmedTarget.lowercase(Locale.ROOT)
        if (lowerTarget.isEmpty() || lowerTarget == "*" ||
            lowerTarget == "true" || lowerTarget == "any" || lowerTarget == "all" ||
            lowerTarget == "not_empty" || lowerTarget == "filled" ||
            lowerTarget == "default-next" ||
            lowerTarget.contains("cross-group") || lowerTarget.contains("cross_group")
        ) {
            return true
        }

        val trimmedParent = parentAnswer.trim()
        val lowerParent = trimmedParent.lowercase(Locale.ROOT)

        if (lowerParent == lowerTarget || lowerParent.contains(lowerTarget) || lowerTarget.contains(lowerParent)) {
            return true
        }

        if (isPhoneMatch(trimmedParent, trimmedTarget)) {
            return true
        }

        return false
    }

    /**
     * Evaluates whether a group jump trigger condition is satisfied by the user's answer.
     * Compatible with evaluateJumpTrigger in in-app-survey-engine's MobilePreviewModal.
     * Supports: match, not_match, else, greater, less, greater_or_equal, less_or_equal.
     */
    fun evaluateJumpTrigger(jump: GroupJumpDto, answer: String?): Boolean {
        if (answer.isNullOrBlank()) return false
        val trimmedAns = answer.trim()
        val trig = (jump.triggerValue ?: "").trim()
        val trigLower = trig.lowercase(Locale.ROOT)
        val handleLower = (jump.sourceHandle ?: "").lowercase(Locale.ROOT)

        if (trigLower.contains("cross-group") || trigLower.contains("cross_group") || trigLower == "default-next" ||
            handleLower.contains("cross-group") || handleLower.contains("cross_group") || handleLower == "default-next"
        ) {
            return true
        }

        val trigType = (jump.triggerType ?: "match").lowercase(Locale.ROOT)

        val isElseOrNotMatch = trigType == "not_match" || trigType == "else" ||
                jump.sourceHandle.equals("else", ignoreCase = true) ||
                trig.equals("else", ignoreCase = true)

        if (isElseOrNotMatch) {
            if (trig.isBlank() || trig.equals("else", ignoreCase = true)) return true
            val opts = trig.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (opts.size > 1) {
                return opts.none { isValueMatch(trimmedAns, it) }
            }
            return !isValueMatch(trimmedAns, trig)
        }

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
            else -> { // "match"
                if (trig.isBlank()) return true
                val opts = trig.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (opts.isNotEmpty()) {
                    return opts.any { isValueMatch(trimmedAns, it) }
                }
                return isValueMatch(trimmedAns, trig)
            }
        }
    }

    /**
     * Robust rule evaluator handling match maps, wildcard, negation/else, numeric, phone, and string comparisons.
     * Compatible with evaluateRule in in-app-survey-engine's MobilePreviewModal.
     */
    fun evaluateRule(ruleConfig: Any?, parentAnswer: String?): Boolean {
        if (parentAnswer.isNullOrBlank()) return false
        if (ruleConfig == null) return true

        if (ruleConfig is Boolean) return ruleConfig
        if (ruleConfig is JsonPrimitive && ruleConfig.isBoolean) return ruleConfig.asBoolean

        val trimmedParent = parentAnswer.trim()
        val lowerParent = trimmedParent.lowercase(Locale.ROOT)

        // If ruleConfig is raw String or Number (not a JSON object)
        if (ruleConfig is Number || (ruleConfig is String && !ruleConfig.trim().startsWith("{") && !ruleConfig.trim().startsWith("["))) {
            val s = ruleConfig.toString().trim()
            val lowerS = s.lowercase(Locale.ROOT)
            if (lowerS.isEmpty() || lowerS == "*" || lowerS == "true" || lowerS == "any" || lowerS == "all" ||
                lowerS == "not_empty" || lowerS == "filled" || lowerS == "default-next" ||
                lowerS.contains("cross-group") || lowerS.contains("cross_group")
            ) return true
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
                val target = s.replace(Regex("^(?:!=|NOT\\s*)", RegexOption.IGNORE_CASE), "").trim()
                if (target.isEmpty()) return true
                return !isValueMatch(trimmedParent, target)
            }
            return isValueMatch(trimmedParent, s)
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
                if (jsonElement.asJsonPrimitive.isBoolean) {
                    return jsonElement.asBoolean
                }
                val s = jsonElement.asString.trim()
                return isValueMatch(trimmedParent, s)
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
                val trig = try { targetObj?.asString?.trim() ?: "" } catch (e: Exception) { "" }
                if (trig.isEmpty()) return true
                return !isValueMatch(trimmedParent, trig)
            }
            "contains" -> {
                val targetObj = jsonConfig.get("validation")
                val trig = try { targetObj?.asString?.trim() ?: "" } catch (e: Exception) { "" }
                if (trig.isEmpty()) return true
                return isValueMatch(trimmedParent, trig)
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
                            if (k.isEmpty()) false else isValueMatch(trimmedParent, k)
                        }
                    }

                    // If only metadata was present and targetVal was jsonConfig, it's unconditional
                    if (truthyKeys.isEmpty() && falsyKeys.isEmpty()) {
                        return true
                    }

                    if (truthyKeys.any {
                        val k = it.lowercase(Locale.ROOT)
                        k.isEmpty() || k == "*" || k == "true" || k == "any" || k == "all" ||
                        k == "not_empty" || k == "filled" || k == "default-next" ||
                        k.contains("cross-group") || k.contains("cross_group")
                    }) {
                        return true
                    }

                    val matchesTruthy = truthyKeys.any { k ->
                        isValueMatch(trimmedParent, k)
                    }

                    if (!matchesTruthy) return false

                    val matchesFalsy = falsyKeys.any { k ->
                        if (k.isEmpty()) false else isValueMatch(trimmedParent, k)
                    }

                    return !matchesFalsy
                }

                if (targetVal.isJsonArray) {
                    val arr = targetVal.asJsonArray
                    for (i in 0 until arr.size()) {
                        val s = arr.get(i).asString.trim()
                        if (isValueMatch(trimmedParent, s)) return true
                    }
                    return false
                }

                if (targetVal.isJsonPrimitive && targetVal.asJsonPrimitive.isBoolean) {
                    return targetVal.asBoolean
                }

                val strTarget = if (targetVal.isJsonPrimitive) targetVal.asString.trim() else ""
                val lowerStrTarget = strTarget.lowercase(Locale.ROOT)
                if (lowerStrTarget.isEmpty() || lowerStrTarget == "*" ||
                    lowerStrTarget == "true" || lowerStrTarget == "any" || lowerStrTarget == "all" ||
                    lowerStrTarget == "not_empty" || lowerStrTarget == "filled" ||
                    lowerStrTarget == "default-next" ||
                    lowerStrTarget.contains("cross-group") || lowerStrTarget.contains("cross_group")
                ) return true

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

                return isValueMatch(trimmedParent, strTarget)
            }
        }
    }
}
