package asia.ifarmer.isurvey.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class SurveyHeaderDto(
    @SerializedName("id")
    val id: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String?,

    @SerializedName("version")
    val version: Int = 1,

    @SerializedName("totalGroups")
    val totalGroups: Int = 0,

    @SerializedName("createdAt")
    val createdAt: String? = null
) : Serializable

data class GroupJumpDto(
    @SerializedName(value = "target_group_id", alternate = ["group_id", "target_group"])
    val targetGroupId: Int,

    @SerializedName(value = "group_title", alternate = ["title", "label"])
    val groupTitle: String = "",

    @SerializedName(value = "source_question_id", alternate = ["sourceQuestionId"])
    val sourceQuestionId: Int? = null,

    @SerializedName(value = "trigger_value", alternate = ["triggerValue"])
    val triggerValue: String? = null,

    @SerializedName(value = "trigger_type", alternate = ["triggerType"])
    val triggerType: String? = null,

    @SerializedName(value = "source_handle", alternate = ["sourceHandle"])
    val sourceHandle: String? = null,

    @SerializedName(value = "source_group", alternate = ["sourceGroup"])
    val sourceGroup: Int? = null,

    @SerializedName(value = "target_question_id", alternate = ["targetQuestionId"])
    val targetQuestionId: Int? = null
) : Serializable

data class SubmitResponseResult(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String?,

    @SerializedName("responseId")
    val responseId: String? = null
) : Serializable

