package asia.ifarmer.isurvey.utils

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import java.util.regex.Pattern

object Constants {

    /**
     * Safely parse options into string array regardless of format:
     * comma-separated string, JSON array string, actual Array, or key-value object.
     * Compatible with parseQuestionOptions in MobilePreviewModal.
     */
    fun parseQuestionOptions(optionsRaw: Any?): List<String> {
        if (optionsRaw == null) return emptyList()
        when (optionsRaw) {
            is List<*> -> {
                return optionsRaw.mapNotNull { item ->
                    when (item) {
                        is Map<*, *> -> item["name"]?.toString() ?: item["value"]?.toString() ?: item["label"]?.toString() ?: item["title"]?.toString() ?: item.toString()
                        is JsonObject -> item.get("name")?.asString ?: item.get("value")?.asString ?: item.get("label")?.asString ?: item.get("title")?.asString ?: item.toString()
                        else -> item?.toString()?.trim()
                    }
                }.filter { it.isNotBlank() }
            }
            is JsonArray -> {
                val list = mutableListOf<String>()
                for (elem in optionsRaw) {
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        val name = obj.get("name")?.asString ?: obj.get("value")?.asString ?: obj.get("label")?.asString ?: obj.get("title")?.asString ?: ""
                        if (name.isNotBlank()) list.add(name.trim())
                    } else if (elem.isJsonPrimitive) {
                        val s = elem.asString.trim()
                        if (s.isNotBlank()) list.add(s)
                    }
                }
                return list
            }
            is JsonObject -> {
                val list = mutableListOf<String>()
                for ((_, elem) in optionsRaw.entrySet()) {
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        val name = obj.get("name")?.asString ?: obj.get("label")?.asString ?: obj.get("value")?.asString ?: ""
                        if (name.isNotBlank()) list.add(name.trim())
                    } else if (elem.isJsonPrimitive) {
                        val s = elem.asString.trim()
                        if (s.isNotBlank()) list.add(s)
                    }
                }
                return list
            }
            is String -> {
                val trimmed = optionsRaw.trim()
                if ((trimmed.startsWith("[") && trimmed.endsWith("]")) ||
                    (trimmed.startsWith("{") && trimmed.endsWith("}"))) {
                    try {
                        val parsed = JsonParser.parseString(trimmed)
                        if (parsed.isJsonArray) {
                            return parseQuestionOptions(parsed.asJsonArray)
                        } else if (parsed.isJsonObject) {
                            return parseQuestionOptions(parsed.asJsonObject)
                        }
                    } catch (e: Exception) {
                        // Fall back to comma split
                    }
                }
                var cleaned = trimmed
                if (cleaned.endsWith(",")) {
                    cleaned = cleaned.substring(0, cleaned.length - 1)
                }
                return cleaned.split(",").map {
                    it.trim().replace("\u00a0", "")
                }.filter { it.isNotEmpty() }
            }
            else -> {
                val str = optionsRaw.toString().trim()
                return if (str.isNotBlank()) listOf(str) else emptyList()
            }
        }
    }

    fun getAnswerList(ans: String?): List<String> {
        if (ans.isNullOrBlank()) return emptyList()
        return parseQuestionOptions(ans)
    }

    fun formatNumber(number: String?): String {
        if (number.isNullOrBlank()) return ""
        return try {
            val nf = NumberFormat.getNumberInstance(Locale.US)
            val df = nf as DecimalFormat
            df.applyPattern("#,##,##,##,##,##,##,##,##,##,##,##,##,###")
            val amount = number.replace(",", "").toDouble()
            df.format(amount)
        } catch (e: Exception) {
            number
        }
    }

    /**
     * Validates Bangladeshi phone numbers.
     * Matches 11-digit numbers (01[1-9]XXXXXXXX), with international prefix (+880, 880, 0088),
     * or 10-digit number without leading 0 (1[1-9]XXXXXXXX).
     */
    fun isValidPhoneNumber(number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        val clean = number.trim().replace(" ", "").replace("-", "")
        val pattern = Pattern.compile("^(?:\\+?880|00880|880)?0?(1[1-9]\\d{8})$")
        val matcher = pattern.matcher(clean)
        return matcher.matches()
    }

    /**
     * Validates integer numbers (positive or negative, optional commas).
     */
    fun isValidInteger(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val clean = value.trim().replace(",", "")
        return clean.toLongOrNull() != null
    }

    /**
     * Validates float/decimal numbers.
     */
    fun isValidFloat(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val clean = value.trim().replace(",", "")
        val d = clean.toDoubleOrNull() ?: return false
        return !d.isNaN() && !d.isInfinite()
    }

    /**
     * Validates date strings in YYYY-MM-DD format with realistic calendar bounds.
     */
    fun isValidDate(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val clean = value.trim()
        val regex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        if (!regex.matches(clean)) return false
        return try {
            val parts = clean.split("-").map { it.toInt() }
            val year = parts[0]
            val month = parts[1]
            val day = parts[2]
            year in 1900..2100 && month in 1..12 && day in 1..31
        } catch (e: Exception) {
            false
        }
    }
}

