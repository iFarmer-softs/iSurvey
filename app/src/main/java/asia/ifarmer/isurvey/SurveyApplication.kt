package asia.ifarmer.isurvey

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class SurveyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Initialize default base URL if not already configured
        if (getBaseUrl().isEmpty()) {
            setBaseUrl(BuildConfig.DEFAULT_BASE_URL)
        }
    }

    companion object {
        private const val PREFS_NAME = "iSurveyPrefs"
        private const val KEY_BASE_URL = "key_base_url"
        private const val KEY_LAST_RESPONDENT_ID = "key_last_respondent_id"
        private const val KEY_LAST_SURVEYOR_ID = "key_last_surveyor_id"

        lateinit var instance: SurveyApplication
            private set

        private lateinit var prefs: SharedPreferences

        fun getBaseUrl(): String {
            return prefs.getString(KEY_BASE_URL, BuildConfig.DEFAULT_BASE_URL) ?: BuildConfig.DEFAULT_BASE_URL
        }

        fun setBaseUrl(url: String) {
            val normalized = if (url.endsWith("/")) url else "$url/"
            prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        }

        fun getLastRespondentId(): String {
            return prefs.getString(KEY_LAST_RESPONDENT_ID, "") ?: ""
        }

        fun setLastRespondentId(id: String) {
            prefs.edit().putString(KEY_LAST_RESPONDENT_ID, id).apply()
        }

        fun getLastSurveyorId(): String {
            return prefs.getString(KEY_LAST_SURVEYOR_ID, "SURVEYOR-01") ?: "SURVEYOR-01"
        }

        fun setLastSurveyorId(id: String) {
            prefs.edit().putString(KEY_LAST_SURVEYOR_ID, id).apply()
        }
    }
}

