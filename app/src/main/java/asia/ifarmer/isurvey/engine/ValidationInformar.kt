package asia.ifarmer.isurvey.engine

import android.view.View
import android.widget.EditText
import android.widget.Spinner
import asia.ifarmer.isurvey.R
import com.google.android.material.chip.ChipGroup
import org.json.JSONObject
import java.util.Locale

class ValidationInformar(
    private val validation: JSONObject,
    private val view: View,
    private val answers: MutableMap<String, String>,
    private val isMandatory: MutableMap<String, Boolean>,
    private val originalMandatory: Boolean = true,
    private val dependsOn: List<Int> = emptyList(),
    private val onClearAnswer: ((questionKey: String) -> Unit)? = null
) {

    fun showOrHideView() {
        val questionKey = view.tag?.toString() ?: return

        val logicalOperator = validation.optString("logical_operator", "ALL").uppercase(Locale.ROOT)
        val isAny = logicalOperator == "ANY"

        // Collect all parent keys
        val parentKeys = mutableListOf<String>()
        if (dependsOn.isNotEmpty()) {
            for (dep in dependsOn) {
                parentKeys.add(dep.toString())
            }
        } else {
            val metaKeys = setOf("logical_operator", "validation_type", "is_same_group", "group", "validation")
            val keys = validation.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (!metaKeys.contains(k.lowercase(Locale.ROOT))) {
                    parentKeys.add(k)
                }
            }
        }

        val shouldShow: Boolean

        if (parentKeys.isEmpty()) {
            shouldShow = true
        } else if (isAny && parentKeys.size > 1) {
            var hasAnyAnswered = false
            var anySatisfied = false

            for (pKey in parentKeys) {
                val ans = answers[pKey]
                if (!ans.isNullOrBlank()) {
                    hasAnyAnswered = true
                    val rule = validation.opt(pKey) ?: if (validation.has("validation_type") || validation.has("validation")) validation else null
                    if (rule == null || RuleEvaluator.evaluateRule(rule, ans)) {
                        anySatisfied = true
                        break
                    }
                }
            }
            shouldShow = hasAnyAnswered && anySatisfied
        } else {
            // ALL must be satisfied
            var allPassed = true
            for (pKey in parentKeys) {
                val ans = answers[pKey]
                if (ans.isNullOrBlank()) {
                    allPassed = false
                    break
                }
                val rule = validation.opt(pKey) ?: if (validation.has("validation_type") || validation.has("validation")) validation else null
                if (rule != null && !RuleEvaluator.evaluateRule(rule, ans)) {
                    allPassed = false
                    break
                }
            }
            shouldShow = allPassed
        }

        if (shouldShow) {
            view.visibility = View.VISIBLE
            isMandatory[questionKey] = originalMandatory
        } else {
            val wasVisible = view.visibility == View.VISIBLE
            view.visibility = View.GONE
            isMandatory[questionKey] = false
            val hadAnswer = !answers[questionKey].isNullOrEmpty()
            answers[questionKey] = ""

            val et: EditText? = view.findViewById(R.id.etValue)
            et?.setText("")

            val etDate: EditText? = view.findViewById(R.id.etDate)
            etDate?.setText("")

            val cg: ChipGroup? = view.findViewById(R.id.cg)
            cg?.clearCheck()

            val sp: Spinner? = view.findViewById(R.id.spDropdown)
            sp?.setSelection(0)

            if (wasVisible || hadAnswer) {
                onClearAnswer?.invoke(questionKey)
            }
        }
    }
}

