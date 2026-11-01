
package com.example.familylocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class LocationService : Service() {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val networkExecutor = Executors.newSingleThreadExecutor()
    private var memberName: String = ""
    private lateinit var locationCallback: LocationCallback

    private val serverUrl =
        "https://family-location-app-bbsv.onrender.com/api/android/location"

    override fun onCreate() {
        super.onCreate()

        val channelId = "family_location_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Family location sharing",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("Family location sharing")
                .setContentText("Location sharing is active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build()

        startForeground(1001, notification)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                Log.d(
                    "FamilyLocation",
                    "Location received: ${location.latitude}, ${location.longitude}"
                )

                uploadLocation(
                    location.latitude,
                    location.longitude,
                    location.accuracy.toDouble()
                )
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        memberName = intent?.getStringExtra("member_name")
            ?.trim()
            .orEmpty()

        if (memberName.isBlank()) {
            Log.e("FamilyLocation", "No family member name provided")
            stopSelf()
            return START_NOT_STICKY
        }

        startLocationUpdates()
        return START_NOT_STICKY
    }

    private fun uploadLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Double
    ) {
        networkExecutor.execute {
            var connection: HttpURLConnection? = null

            try {
                val token = BuildConfig.ANDROID_APP_TOKEN

                if (token.isBlank()) {
                    Log.e("FamilyLocation", "Android token is not configured")
                    return@execute
                }

                connection =
                    (URL(serverUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15000
                        readTimeout = 15000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Authorization", "Bearer $token")
                    }

                val payload = JSONObject().apply {
                    put("name", memberName)
                    put("latitude", latitude)
                    put("longitude", longitude)
                    put("accuracy", accuracy)
                }

                connection.outputStream.use { output ->
                    output.write(
                        payload.toString().toByteArray(Charsets.UTF_8)
                    )
                }

                val status = connection.responseCode

                if (status in 200..299) {
                    Log.d(
                        "FamilyLocation",
                        "Location uploaded successfully for $memberName"
                    )
                } else {
                    Log.e(
                        "FamilyLocation",
                        "Location upload failed: HTTP $status"
                    )
                }
            } catch (e: Exception) {
                Log.e("FamilyLocation", "Could not upload location", e)
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun startLocationUpdates() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            60_000L
        )
            .setMinUpdateIntervalMillis(30_000L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper
            )
        } catch (e: SecurityException) {
            Log.e("FamilyLocation", "Location permission error", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }

        networkExecutor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
