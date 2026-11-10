package asia.ifarmer.isurvey.engine

import android.view.View
import android.widget.EditText
import android.widget.Spinner
import asia.ifarmer.isurvey.R
import asia.ifarmer.isurvey.utils.Constants
import com.google.android.material.chip.ChipGroup
import org.json.JSONObject
import java.util.regex.Pattern

class ValidationInformar(
    private val validation: JSONObject,
    private val view: View,
    private val answers: MutableMap<String, String>,
    private val isMandatory: MutableMap<String, Boolean>,
    private val onClearAnswer: ((questionKey: String) -> Unit)? = null
) {
    private val digitPattern = Pattern.compile("^[0-9]+$")

    fun showOrHideView() {
        val showOrNot = mutableListOf<Boolean>()
        val keys = validation.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            if (answers.containsKey(key)) {
                val config = validation.optJSONObject(key) ?: continue
                val type = config.optString("validation_type", "")
                var currentAnswer = answers[key] ?: ""

                when (type) {
                    "match" -> {
                        val matches = config.optJSONObject("validation")
                        if (matches != null) {
                            val validationKeys = matches.keys()
                            var matchFound = false
                            val answerList = Constants.getAnswerList(currentAnswer)
                            while (validationKeys.hasNext()) {
                                val vKey = validationKeys.next()
                                if (answerList.contains(vKey) && matches.optBoolean(vKey, false)) {
                                    matchFound = true
                                    break
                                }
                            }
                            showOrNot.add(matchFound)
                        } else {
                            showOrNot.add(false)
                        }
                    }
                    "greater", "less" -> {
                        val threshold = config.optLong("validation", 0L)
                        val matcher = digitPattern.matcher(currentAnswer.trim())
                        if (matcher.matches()) {
                            val valLong = currentAnswer.trim().toLongOrNull() ?: 0L
                            val passes = if (type == "greater") valLong > threshold else valLong < threshold
                            showOrNot.add(passes)
                        } else {
                            showOrNot.add(false)
                        }
                    }
                }
            }
        }

        val questionKey = view.tag?.toString() ?: return

        // If there are no failed conditions, show view and restore mandatory flag
        if (!showOrNot.contains(false)) {
            view.visibility = View.VISIBLE
            isMandatory[questionKey] = true
        } else {
            // Condition not met: hide view, mark not mandatory, clear answers
            view.visibility = View.GONE
            isMandatory[questionKey] = false
            answers[questionKey] = ""
            onClearAnswer?.invoke(questionKey)

            val et: EditText? = view.findViewById(R.id.etValue)
            et?.setText("")

            val etDate: EditText? = view.findViewById(R.id.etDate)
            etDate?.setText("")

            val cg: ChipGroup? = view.findViewById(R.id.cg)
            cg?.clearCheck()

            val sp: Spinner? = view.findViewById(R.id.spDropdown)
            sp?.setSelection(0)
        }
    }
}

