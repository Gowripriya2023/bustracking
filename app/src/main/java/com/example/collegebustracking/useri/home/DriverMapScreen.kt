package com.example.collegebustracking.useri.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.example.collegebustracking.model.Bus

import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@SuppressLint("MissingPermission")
@Composable
fun DriverMapScreen(navController: NavController) {

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""

    val context = LocalContext.current

    var assignedBus by remember { mutableStateOf<Bus?>(null) }
    var isTripActive by remember { mutableStateOf(false) }

    // Driver's current GPS as Compose state
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLon by remember { mutableStateOf<Double?>(null) }
    var statusMessage by remember { mutableStateOf("Loading...") }

    // Location permission
    var hasLocationPermission by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationRequest = remember {
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000
        ).setMinUpdateIntervalMillis(2000)
         .setWaitForAccurateLocation(false)
         .build()
    }

    // Use rememberUpdatedState to capture latest state in the callback
    val currentAssignedBus by rememberUpdatedState(assignedBus)
    val currentTripActive by rememberUpdatedState(isTripActive)

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {

                val location = locationResult.lastLocation ?: return

                // Update Compose state so the map redraws
                currentLat = location.latitude
                currentLon = location.longitude

                android.util.Log.d("DRIVER_GPS",
                    "GPS: lat=${location.latitude}, lon=${location.longitude}, " +
                    "tripActive=$currentTripActive, bus=${currentAssignedBus?.id}")

                if (currentTripActive && currentAssignedBus != null) {

                    val busId = currentAssignedBus!!.id

                    database.child("buses")
                        .child(busId)
                        .child("currentLocation")
                        .setValue(
                            mapOf(
                                "latitude" to location.latitude,
                                "longitude" to location.longitude,
                                "timestamp" to System.currentTimeMillis()
                            )
                        )
                        .addOnSuccessListener {
                            statusMessage = "GPS sent: ${location.latitude}, ${location.longitude}"
                            android.util.Log.d("DRIVER_GPS", "Firebase push SUCCESS")
                        }
                        .addOnFailureListener { e ->
                            statusMessage = "Firebase error: ${e.message}"
                            android.util.Log.e("DRIVER_GPS", "Firebase push FAILED: ${e.message}")
                        }
                } else {
                    statusMessage = "GPS received but not pushing (trip=${currentTripActive}, bus=${currentAssignedBus?.id})"
                }
            }
        }
    }

    // Find the bus assigned to this driver
    LaunchedEffect(currentUserId) {
        if (currentUserId.isEmpty()) {
            statusMessage = "Not logged in"
            return@LaunchedEffect
        }

        statusMessage = "Finding your assigned bus..."

        database.child("buses")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    var found = false

                    for (child in snapshot.children) {
                        val driverId = child.child("driverId")
                            .getValue(String::class.java) ?: ""

                        if (driverId == currentUserId) {
                            val busNumber = child.child("busNumber")
                                .getValue(String::class.java) ?: ""
                            val routeId = child.child("routeId")
                                .getValue(String::class.java) ?: ""
                            val id = child.key ?: ""

                            assignedBus = Bus(
                                id = id,
                                busNumber = busNumber,
                                routeId = routeId,
                                driverId = driverId
                            )

                            // Check if trip was already active
                            val tripActive = child.child("isTripActive")
                                .getValue(Boolean::class.java) ?: false
                            isTripActive = tripActive

                            found = true
                            statusMessage = "Bus found: $busNumber"

                            android.util.Log.d("DRIVER_GPS",
                                "Assigned bus: id=$id, number=$busNumber, routeId=$routeId")
                            break
                        }
                    }

                    if (!found) {
                        statusMessage = "No bus assigned to you"
                        android.util.Log.d("DRIVER_GPS",
                            "No bus found for driver UID: $currentUserId")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    statusMessage = "Error: ${error.message}"
                }
            })
    }

    // Request permission on launch if not granted
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Clean up location updates when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    /* ---- UI ---- */

    Column(modifier = Modifier.fillMaxSize()) {

        // Status bar
        Surface(
            color = if (isTripActive) Color(0xFF1B5E20) else Color(0xFF424242),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isTripActive) "\uD83D\uDFE2 Trip Active" else "\u23F8 Trip Stopped",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                assignedBus?.let { bus ->
                    Text(
                        text = "Bus: ${bus.busNumber}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = statusMessage,
                    color = Color(0xFFB0BEC5),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Map showing driver's own location
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->
                Configuration.getInstance().load(
                    ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                )
                val mapView = MapView(ctx)
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.setMultiTouchControls(true)
                mapView.controller.setZoom(16.5)
                mapView.controller.setCenter(GeoPoint(10.8505, 76.2711))
                mapView
            },
            update = { map ->
                map.overlays.clear()

                val lat = currentLat
                val lon = currentLon

                if (lat != null && lon != null) {
                    val marker = Marker(map)
                    marker.position = GeoPoint(lat, lon)
                    marker.title = "Your Location"
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(marker)
                    map.controller.setCenter(GeoPoint(lat, lon))
                }

                map.invalidate()
            }
        )

        // Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // START TRIP
            Button(
                onClick = {
                    if (!hasLocationPermission) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        return@Button
                    }

                    val bus = assignedBus
                    if (bus == null) {
                        Toast.makeText(context, "No bus assigned!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Set trip active in Firebase
                    database.child("buses")
                        .child(bus.id)
                        .child("isTripActive")
                        .setValue(true)

                    isTripActive = true
                    statusMessage = "Starting GPS tracking..."

                    // Start location updates
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    )

                    Toast.makeText(context, "Trip Started!", Toast.LENGTH_SHORT).show()
                },
                enabled = !isTripActive && assignedBus != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                )
            ) {
                Text("Start Trip")
            }

            // STOP TRIP
            Button(
                onClick = {
                    val bus = assignedBus
                    if (bus != null) {
                        database.child("buses")
                            .child(bus.id)
                            .child("isTripActive")
                            .setValue(false)

                        // Clear the current location
                        database.child("buses")
                            .child(bus.id)
                            .child("currentLocation")
                            .removeValue()
                    }

                    isTripActive = false
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                    statusMessage = "Trip stopped"

                    Toast.makeText(context, "Trip Stopped!", Toast.LENGTH_SHORT).show()
                },
                enabled = isTripActive,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC62828)
                )
            ) {
                Text("Stop Trip")
            }
        }

        // GPS coordinates display
        if (currentLat != null && currentLon != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "GPS: ${currentLat}, ${currentLon}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}