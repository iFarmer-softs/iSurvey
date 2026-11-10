package asia.ifarmer.isurvey.api

import asia.ifarmer.isurvey.SurveyApplication
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var currentBaseUrl: String = ""
    private var cachedService: SurveyApiService? = null

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Synchronized
    fun getService(): SurveyApiService {
        val activeUrl = SurveyApplication.getBaseUrl()
        if (cachedService != null && currentBaseUrl == activeUrl) {
            return cachedService!!
        }

        currentBaseUrl = activeUrl
        val retrofit = Retrofit.Builder()
            .baseUrl(activeUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(SurveyApiService::class.java)
        cachedService = service
        return service
    }

    fun resetService() {
        cachedService = null
    }
}
