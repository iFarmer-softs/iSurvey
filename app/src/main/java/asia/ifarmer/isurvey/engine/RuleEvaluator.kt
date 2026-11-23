package asia.ifarmer.isurvey.engine

import asia.ifarmer.isurvey.model.GroupJumpDto
import org.json.JSONArray
import org.json.JSONObject
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

        // If ruleConfig is raw string or number
        if (ruleConfig is String || ruleConfig is Number) {
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

        val jsonConfig = when (ruleConfig) {
            is JSONObject -> ruleConfig
            is Map<*, *> -> JSONObject(ruleConfig)
            else -> return true
        }

        val vType = jsonConfig.optString("validation_type", "match").lowercase(Locale.ROOT)

        when (vType) {
            "greater" -> {
                val num = trimmedParent.toDoubleOrNull()
                val threshold = jsonConfig.optDouble("validation", Double.NaN)
                return num != null && !threshold.isNaN() && num > threshold
            }
            "less" -> {
                val num = trimmedParent.toDoubleOrNull()
                val threshold = jsonConfig.optDouble("validation", Double.NaN)
                return num != null && !threshold.isNaN() && num < threshold
            }
            "greater_or_equal" -> {
                val num = trimmedParent.toDoubleOrNull()
                val threshold = jsonConfig.optDouble("validation", Double.NaN)
                return num != null && !threshold.isNaN() && num >= threshold
            }
            "less_or_equal" -> {
                val num = trimmedParent.toDoubleOrNull()
                val threshold = jsonConfig.optDouble("validation", Double.NaN)
                return num != null && !threshold.isNaN() && num <= threshold
            }
            "not_match" -> {
                val targetObj = jsonConfig.opt("validation")
                val trig = targetObj?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                return lowerParent != trig && !lowerParent.contains(trig)
            }
            "contains" -> {
                val targetObj = jsonConfig.opt("validation")
                val trig = targetObj?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                return lowerParent.contains(trig)
            }
            else -> { // match
                if (!jsonConfig.has("validation") || jsonConfig.isNull("validation")) return true
                val targetVal = jsonConfig.opt("validation")

                if (targetVal is JSONObject) {
                    val falsyKeys = mutableListOf<String>()
                    val truthyKeys = mutableListOf<String>()
                    val keys = targetVal.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (targetVal.optBoolean(k, false)) {
                            truthyKeys.add(k.trim())
                        } else {
                            falsyKeys.add(k.trim())
                        }
                    }

                    // If pure negation/else (e.g. { "Yes": false } without truthy keys)
                    if (truthyKeys.isEmpty() && falsyKeys.isNotEmpty()) {
                        return falsyKeys.none { k ->
                            val lowerKey = k.lowercase(Locale.ROOT)
                            lowerParent == lowerKey || lowerParent.contains(lowerKey)
                        }
                    }

                    if (truthyKeys.isEmpty()) return false
                    if (truthyKeys.contains("")) return true // Wildcard non-empty match

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

                if (targetVal is JSONArray) {
                    for (i in 0 until targetVal.length()) {
                        val s = targetVal.optString(i, "").trim().lowercase(Locale.ROOT)
                        if (lowerParent == s || lowerParent.contains(s)) return true
                    }
                    return false
                }

                val strTarget = targetVal?.toString()?.trim() ?: ""
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
