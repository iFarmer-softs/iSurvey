package asia.ifarmer.isurvey.api

import asia.ifarmer.isurvey.model.SubmitResponseResult
import asia.ifarmer.isurvey.model.SurveyHeaderDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface SurveyApiService {

    @GET("surveys")
    suspend fun getSurveys(): Response<List<SurveyHeaderDto>>

    @GET("surveys/{surveyId}/groups/{groupNumber}")
    suspend fun getSurveyGroup(
        @Path("surveyId") surveyId: String,
        @Path("groupNumber") groupNumber: Int,
        @Query("respondentId") respondentId: String? = null
    ): Response<ResponseBody>

    @Multipart
    @POST("surveys/{surveyId}/responses")
    suspend fun submitResponse(
        @Path("surveyId") surveyId: String,
        @Part("farmerId") farmerId: RequestBody,
        @Part("surveyorId") surveyorId: RequestBody?,
        @Part("group") group: RequestBody,
        @Part("data") data: RequestBody,
        @Part("location") location: RequestBody?,
        @Part images: List<MultipartBody.Part>
    ): Response<SubmitResponseResult>
}
