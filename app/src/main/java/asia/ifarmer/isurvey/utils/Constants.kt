package asia.ifarmer.isurvey.utils

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

object Constants {

    fun getAnswerList(ans: String?): List<String> {
        if (ans.isNullOrBlank()) return emptyList()
        var cleaned = ans.trim()
        if (cleaned.endsWith(",")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }
        return cleaned.split(",").map {
            it.trim().replace(",", "").replace("\u00a0", "")
        }.filter { it.isNotEmpty() }
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
}

