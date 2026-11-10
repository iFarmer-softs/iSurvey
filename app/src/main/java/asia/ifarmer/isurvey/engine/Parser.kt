package asia.ifarmer.isurvey.engine

import android.util.SparseArray
import asia.ifarmer.isurvey.model.GroupJumpDto
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Parser(mainData: String) {

    private val KEY_DATA = "data"
    private val KEY_NEXT = "next"
    private val KEY_QUESTION_ID = "question_id"
    private val KEY_QUESTION = "question"
    private val KEY_QUESTION_BN = "question_bn"
    private val KEY_MANDATORY = "mandatory"
    private val KEY_ANSWER_TYPE = "answer_type"
    private val KEY_VALUE = "value"
    private val KEY_ENABLED = "editable"
    private val KEY_OPTIONS = "options"
    private val KEY_PLACEHOLDER = "placeholder"
    private val KEY_DEPENDS_ON = "depends_on"
    private val KEY_VALIDATION = "validation"
    private val KEY_TOTAL_GROUP = "total_groups"
    private val KEY_CURRENT_GROUP = "current_group"
    private val KEY_HINT = "hint"
    private val KEY_INSTRUCTION = "instruction"
    private val KEY_GROUPS_TO_JUMP = "groups_to_jump"

    private val joMain: JSONObject = JSONObject(mainData)
    private val dataArray: JSONArray = joMain.optJSONArray(KEY_DATA) ?: JSONArray()
    private val questionCache = SparseArray<JSONObject>()

    private fun getQuestionObj(index: Int): JSONObject {
        var obj = questionCache.get(index)
        if (obj == null) {
            obj = dataArray.getJSONObject(index)
            questionCache.put(index, obj)
        }
        return obj
    }

    val dataLength: Int
        get() = dataArray.length()

    val totalGroups: Int
        get() = joMain.optInt(KEY_TOTAL_GROUP, 1)

    val currentGroup: Int
        get() = joMain.optInt(KEY_CURRENT_GROUP, 1)

    val instruction: String
        get() = joMain.optString(KEY_INSTRUCTION, "")

    val isNextGroupAvailable: Boolean
        get() = joMain.optBoolean(KEY_NEXT, false)

    fun getQuestionId(index: Int): Int {
        return getQuestionObj(index).getInt(KEY_QUESTION_ID)
    }

    fun getQuestion(index: Int): String {
        return getQuestionObj(index).optString(KEY_QUESTION, "")
    }

    fun getQuestionBn(index: Int): String {
        val bn = getQuestionObj(index).optString(KEY_QUESTION_BN, "")
        return if (bn.isNotBlank() && bn != "null") bn else getQuestion(index)
    }

    fun getAnswerType(index: Int): String {
        return getQuestionObj(index).optString(KEY_ANSWER_TYPE, "string")
    }

    fun getPlaceHolder(index: Int): String {
        val ph = getQuestionObj(index).optString(KEY_PLACEHOLDER, "")
        return if (ph == "null") "" else ph
    }

    fun isEnabled(index: Int): Boolean {
        val obj = getQuestionObj(index)
        if (!obj.has(KEY_ENABLED) || obj.optString(KEY_ENABLED) == "null") {
            return true
        }
        return obj.optBoolean(KEY_ENABLED, true)
    }

    fun getOptions(index: Int): String {
        val options = getQuestionObj(index).optString(KEY_OPTIONS, "").trim()
        return if (options.endsWith(",")) {
            options.substring(0, options.length - 1)
        } else {
            options
        }
    }

    fun getMandatory(index: Int): Boolean {
        return getQuestionObj(index).optBoolean(KEY_MANDATORY, false)
    }

    fun getValue(index: Int): String? {
        val obj = getQuestionObj(index)
        if (!obj.has(KEY_VALUE) || obj.isNull(KEY_VALUE) || obj.optString(KEY_VALUE) == "null") {
            return null
        }
        return obj.optString(KEY_VALUE)
    }

    fun getHint(index: Int): String? {
        val obj = getQuestionObj(index)
        if (!obj.has(KEY_HINT) || obj.isNull(KEY_HINT) || obj.optString(KEY_HINT) == "null") {
            return null
        }
        return obj.optString(KEY_HINT)
    }

    fun getDependsOn(index: Int): List<Int> {
        val ids = mutableListOf<Int>()
        val obj = getQuestionObj(index)
        if (obj.has(KEY_DEPENDS_ON) && obj.optString(KEY_DEPENDS_ON) != "null") {
            val deps = obj.optJSONArray(KEY_DEPENDS_ON)
            if (deps != null) {
                for (i in 0 until deps.length()) {
                    ids.add(deps.getInt(i))
                }
            }
        }
        return ids
    }

    fun getValidation(index: Int): JSONObject? {
        val obj = getQuestionObj(index)
        if (obj.has(KEY_VALIDATION) && obj.optString(KEY_VALIDATION) != "null") {
            return obj.optJSONObject(KEY_VALIDATION)
        }
        return null
    }

    fun getGroupsToJump(): List<GroupJumpDto> {
        val list = mutableListOf<GroupJumpDto>()
        val jumps = joMain.optJSONArray(KEY_GROUPS_TO_JUMP) ?: return list
        for (i in 0 until jumps.length()) {
            val item = jumps.getJSONObject(i)
            list.add(
                GroupJumpDto(
                    targetGroupId = item.getInt("target_group_id"),
                    groupTitle = item.getString("group_title")
                )
            )
        }
        return list
    }
}

