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
    @SerializedName("target_group_id")
    val targetGroupId: Int,

    @SerializedName("group_title")
    val groupTitle: String
) : Serializable

data class SubmitResponseResult(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String?,

    @SerializedName("responseId")
    val responseId: String? = null
) : Serializable

