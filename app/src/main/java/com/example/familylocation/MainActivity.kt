
package com.example.familylocation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.familylocation.ui.theme.FamilyLocationTheme

class MainActivity : ComponentActivity() {

    private val sharing = mutableStateOf(false)
    private val memberName = mutableStateOf("")

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                startLocationSharing()
            } else {
                sharing.value = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        memberName.value = getSharedPreferences(
            "family_location",
            MODE_PRIVATE
        ).getString("member_name", "") ?: ""

        setContent {
            FamilyLocationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Family Location",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedTextField(
                            value = memberName.value,
                            onValueChange = { memberName.value = it },
                            label = { Text("Your family member name") },
                            placeholder = { Text("e.g. Adi, Mother, Father") },
                            singleLine = true,
                            enabled = !sharing.value,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (sharing.value)
                                "Location sharing is switched on for ${memberName.value}"
                            else
                                "Location sharing is switched off"
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (sharing.value) {
                                    stopLocationSharing()
                                } else if (memberName.value.trim().isNotEmpty()) {
                                    getSharedPreferences(
                                        "family_location",
                                        MODE_PRIVATE
                                    ).edit()
                                        .putString(
                                            "member_name",
                                            memberName.value.trim()
                                        )
                                        .apply()

                                    requestLocationPermission()
                                }
                            }
                        ) {
                            Text(
                                text = if (sharing.value)
                                    "Stop Sharing"
                                else
                                    "Start Sharing"
                            )
                        }

                        if (!sharing.value && memberName.value.trim().isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Enter your name before starting.")
                        }
                    }
                }
            }
        }
    }

    private fun requestLocationPermission() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        locationPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startLocationSharing() {
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            sharing.value = false
            return
        }

        val serviceIntent = Intent(this, LocationService::class.java).apply {
            putExtra("member_name", memberName.value.trim())
        }

        ContextCompat.startForegroundService(this, serviceIntent)
        sharing.value = true
    }

    private fun stopLocationSharing() {
        stopService(Intent(this, LocationService::class.java))
        sharing.value = false
    }
}
