package asia.ifarmer.isurvey.engine

import android.util.SparseArray
import asia.ifarmer.isurvey.model.GroupJumpDto
import asia.ifarmer.isurvey.utils.Constants
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
        get() = when {
            joMain.has(KEY_TOTAL_GROUP) -> joMain.optInt(KEY_TOTAL_GROUP, 1)
            joMain.has("totalGroups") -> joMain.optInt("totalGroups", 1)
            else -> 1
        }

    val currentGroup: Int
        get() = when {
            joMain.has(KEY_CURRENT_GROUP) -> joMain.optInt(KEY_CURRENT_GROUP, 1)
            joMain.has("currentGroup") -> joMain.optInt("currentGroup", 1)
            else -> 1
        }

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
        val raw = getQuestionObj(index).opt(KEY_OPTIONS)
        val list = Constants.parseQuestionOptions(raw)
        return list.joinToString(", ")
    }

    fun getOptionsList(index: Int): List<String> {
        val raw = getQuestionObj(index).opt(KEY_OPTIONS)
        return Constants.parseQuestionOptions(raw)
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
            } else {
                val single = obj.optInt(KEY_DEPENDS_ON, -1)
                if (single != -1) ids.add(single)
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

    fun getSubsequentQuestions(index: Int): List<Int> {
        val ids = mutableListOf<Int>()
        val obj = getQuestionObj(index)
        val arr = obj.optJSONArray("subsequent_questions")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                ids.add(arr.optInt(i))
            }
        }
        return ids
    }

    fun getGroup(index: Int): Int {
        val obj = getQuestionObj(index)
        val g = obj.optInt("group", currentGroup)
        return if (g > 0) g else currentGroup
    }

    fun isCrossGroupDependency(index: Int): Boolean {
        val obj = getQuestionObj(index)
        val v = obj.opt("cross_group_dependency")
        if (v is Boolean) return v
        if (v is JSONArray) return v.length() > 0
        return obj.optBoolean("cross_group_dependency", false)
    }

    fun getGroupsToJump(): List<GroupJumpDto> {
        val list = mutableListOf<GroupJumpDto>()
        val jumps = joMain.optJSONArray(KEY_GROUPS_TO_JUMP)
            ?: joMain.optJSONArray("groupsToJump")
            ?: joMain.optJSONArray("groups")
            ?: return list
        for (i in 0 until jumps.length()) {
            val item = jumps.optJSONObject(i) ?: continue
            val rawId = when {
                item.has("group_id") -> item.optString("group_id")
                item.has("groupId") -> item.optString("groupId")
                item.has("target_group_id") -> item.optString("target_group_id")
                item.has("targetGroupId") -> item.optString("targetGroupId")
                item.has("target_group") -> item.optString("target_group")
                item.has("targetGroup") -> item.optString("targetGroup")
                else -> ""
            }
            val id = rawId.trim().toIntOrNull() ?: continue
            val title = when {
                item.has("group_title") && item.optString("group_title").isNotBlank() -> item.optString("group_title")
                item.has("groupTitle") && item.optString("groupTitle").isNotBlank() -> item.optString("groupTitle")
                item.has("title") && item.optString("title").isNotBlank() -> item.optString("title")
                item.has("name") && item.optString("name").isNotBlank() -> item.optString("name")
                else -> "Group $id"
            }
            val srcQId = when {
                item.has("source_question_id") && !item.isNull("source_question_id") -> item.optInt("source_question_id")
                item.has("sourceQuestionId") && !item.isNull("sourceQuestionId") -> item.optInt("sourceQuestionId")
                else -> null
            }
            val trigVal = when {
                item.has("trigger_value") && !item.isNull("trigger_value") -> item.optString("trigger_value")
                item.has("triggerValue") && !item.isNull("triggerValue") -> item.optString("triggerValue")
                else -> null
            }
            val trigType = when {
                item.has("trigger_type") && !item.isNull("trigger_type") -> item.optString("trigger_type")
                item.has("triggerType") && !item.isNull("triggerType") -> item.optString("triggerType")
                else -> null
            }
            val srcHandle = when {
                item.has("source_handle") && !item.isNull("source_handle") -> item.optString("source_handle")
                item.has("sourceHandle") && !item.isNull("sourceHandle") -> item.optString("sourceHandle")
                else -> null
            }
            val srcGroup = when {
                item.has("source_group") && !item.isNull("source_group") -> item.optInt("source_group")
                item.has("sourceGroup") && !item.isNull("sourceGroup") -> item.optInt("sourceGroup")
                else -> null
            }
            val targetQId = when {
                item.has("target_question_id") && !item.isNull("target_question_id") -> item.optInt("target_question_id")
                item.has("targetQuestionId") && !item.isNull("targetQuestionId") -> item.optInt("targetQuestionId")
                else -> null
            }

            list.add(
                GroupJumpDto(
                    targetGroupId = id,
                    groupTitle = title,
                    sourceQuestionId = srcQId,
                    triggerValue = trigVal,
                    triggerType = trigType,
                    sourceHandle = srcHandle,
                    sourceGroup = srcGroup,
                    targetQuestionId = targetQId
                )
            )
        }
        return list
    }
}

