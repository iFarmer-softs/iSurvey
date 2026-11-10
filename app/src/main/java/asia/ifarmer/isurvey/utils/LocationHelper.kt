package asia.ifarmer.isurvey.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

data class Coordinates(
    val lat: Double,
    val lng: Double
)

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Coordinates? {
        return try {
            val lastLocation: Location? = fusedLocationClient.lastLocation.await()
            if (lastLocation != null) {
                Coordinates(lastLocation.latitude, lastLocation.longitude)
            } else {
                val cts = CancellationTokenSource()
                val freshLocation: Location? = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.token
                ).await()
                if (freshLocation != null) {
                    Coordinates(freshLocation.latitude, freshLocation.longitude)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}

