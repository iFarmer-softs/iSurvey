# Proguard rules for iSurvey
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class asia.ifarmer.isurvey.model.** { *; }

