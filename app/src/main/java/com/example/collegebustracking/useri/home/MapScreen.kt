package com.example.collegebustracking.useri.home

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.collegebustracking.model.BusStop
import com.google.firebase.database.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import kotlin.math.*
import java.text.SimpleDateFormat
import java.util.*

fun resizeDrawable(
    context: Context,
    resId: Int,
    width: Int,
    height: Int
): android.graphics.drawable.Drawable {
    val drawable = context.getDrawable(resId)!!
    val bitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
    val smallBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, false)
    return android.graphics.drawable.BitmapDrawable(context.resources, smallBitmap)
}

fun calculateDistance(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Double {
    val R = 6371e3
    val p1 = lat1 * PI / 180
    val p2 = lat2 * PI / 180
    val dp = (lat2 - lat1) * PI / 180
    val dl = (lon2 - lon1) * PI / 180
    val a = sin(dp / 2) * sin(dp / 2) +
            cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

/**
 * Calculate delay in minutes. Returns positive if delayed, 0 or negative if on time.
 */
fun calculateDelayMinutes(expectedTimeStr: String, etaMinutes: Int): Int {
    if (expectedTimeStr.isBlank()) return 0
    return try {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val expectedTime = sdf.parse(expectedTimeStr) ?: return 0

        // Build expected calendar for today
        val expectedCal = Calendar.getInstance().apply {
            val parsed = Calendar.getInstance().apply { time = expectedTime }
            set(Calendar.HOUR_OF_DAY, parsed.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, parsed.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
        }

        // Estimated arrival = now + etaMinutes
        val estimatedArrivalCal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, etaMinutes)
        }

        val diffMs = estimatedArrivalCal.timeInMillis - expectedCal.timeInMillis
        (diffMs / 60000).toInt()
    } catch (e: Exception) {
        0
    }
}

@Composable
fun MapScreen(navController: NavController, routeId: String) {

    val context = LocalContext.current

    val database = FirebaseDatabase.getInstance(
        "https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/"
    ).reference

    var stops by remember { mutableStateOf(listOf<BusStop>()) }
    var nextStop by remember { mutableStateOf<BusStop?>(null) }

    // Bus live location as Compose state
    var busLat by remember { mutableStateOf<Double?>(null) }
    var busLon by remember { mutableStateOf<Double?>(null) }
    var isTripActive by remember { mutableStateOf(false) }
    var busId by remember { mutableStateOf("") }

    var distanceToStop by remember { mutableStateOf(0.0) }
    var etaMinutes by remember { mutableStateOf(0) }

    /* ---- LOAD STOPS ---- */

    LaunchedEffect(routeId) {
        database.child("routes").child(routeId).child("stops")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<BusStop>()
                    for (child in snapshot.children) {
                        val stop = child.getValue(BusStop::class.java)
                        if (stop != null) {
                            list.add(stop.copy(id = child.key ?: ""))
                        }
                    }
                    stops = list
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /* ---- FIND BUS + LISTEN LIVE LOCATION ---- */
    // Load ALL buses, find the one matching this routeId, and track its location
    // This avoids Firebase query indexing issues

    LaunchedEffect(routeId) {
        database.child("buses")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    var foundBus = false

                    for (child in snapshot.children) {
                        val childRouteId = child.child("routeId")
                            .getValue(String::class.java) ?: ""

                        if (childRouteId == routeId) {

                            busId = child.key ?: ""
                            foundBus = true

                            // Read isTripActive
                            val tripActive = child.child("isTripActive")
                                .getValue(Boolean::class.java) ?: false

                            // Read currentLocation
                            val lat = child.child("currentLocation")
                                .child("latitude")
                                .getValue(Double::class.java)

                            val lon = child.child("currentLocation")
                                .child("longitude")
                                .getValue(Double::class.java)

                            android.util.Log.d("MAP_DEBUG",
                                "Bus=$busId tripActive=$tripActive lat=$lat lon=$lon")

                            if (tripActive && lat != null && lon != null) {
                                isTripActive = true
                                busLat = lat
                                busLon = lon

                                // Calculate nearest stop
                                val nearest = stops.minByOrNull { s ->
                                    val dx = s.latitude - lat
                                    val dy = s.longitude - lon
                                    dx * dx + dy * dy
                                }
                                nearest?.let { stop ->
                                    nextStop = stop
                                    val dist = calculateDistance(
                                        lat, lon, stop.latitude, stop.longitude
                                    )
                                    distanceToStop = dist
                                    val speedMs = 30.0 * 1000 / 3600
                                    etaMinutes = (dist / speedMs / 60).toInt()
                                }
                            } else {
                                isTripActive = false
                                busLat = null
                                busLon = null
                            }

                            break  // Found matching bus
                        }
                    }

                    if (!foundBus) {
                        android.util.Log.d("MAP_DEBUG", "No bus found for routeId=$routeId")
                        isTripActive = false
                        busLat = null
                        busLon = null
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /* ---- UI ---- */

    Column(modifier = Modifier.fillMaxSize()) {

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

                // Stop markers
                stops.forEach { stop ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(stop.latitude, stop.longitude)
                    marker.title = stop.name
                    map.overlays.add(marker)
                }

                // Route line
                if (stops.isNotEmpty()) {
                    val routeLine = Polyline()
                    routeLine.setPoints(stops.map { GeoPoint(it.latitude, it.longitude) })
                    routeLine.outlinePaint.color = android.graphics.Color.BLUE
                    routeLine.outlinePaint.strokeWidth = 8f
                    map.overlays.add(routeLine)
                }

                // Bus marker — uses Compose state so this runs on every location update
                val lat = busLat
                val lon = busLon

                if (lat != null && lon != null) {
                    val busMarker = Marker(map)
                    busMarker.position = GeoPoint(lat, lon)
                    busMarker.title = "Bus Live Location"
                    try {
                        busMarker.icon = resizeDrawable(
                            context,
                            com.example.collegebustracking.R.drawable.bus_icon,
                            60, 60
                        )
                    } catch (e: Exception) { }
                    busMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(busMarker)
                    map.controller.setCenter(GeoPoint(lat, lon))
                } else if (stops.isNotEmpty()) {
                    val first = stops.first()
                    map.controller.setCenter(GeoPoint(first.latitude, first.longitude))
                }

                map.invalidate()
            }
        )

        // Status bar
        Surface(
            color = if (isTripActive) Color(0xFF1B5E20) else Color(0xFF424242),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTripActive) "\uD83D\uDFE2 Bus is Live" else "\u23F3 Waiting for bus",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        // ETA info
        if (isTripActive && nextStop != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Next Stop: ${nextStop!!.name}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val distText = if (distanceToStop < 1000) {
                            "${distanceToStop.toInt()} m away"
                        } else {
                            "${String.format("%.1f", distanceToStop / 1000)} km away"
                        }
                        Text(text = "\uD83D\uDCCD $distText")
                        Text(text = "\u23F1 ETA: ${if (etaMinutes < 1) "< 1 min" else "$etaMinutes min"}")
                    }

                    // Expected Arrival Time + Delay Info
                    val expectedTime = nextStop!!.expectedArrivalTime
                    if (expectedTime.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val delayMin = calculateDelayMinutes(expectedTime, etaMinutes)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "\uD83D\uDD50 Expected: $expectedTime",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (delayMin > 0) {
                                Text(
                                    text = "\u26A0\uFE0F Delayed by $delayMin min",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFD32F2F)
                                )
                            } else {
                                Text(
                                    text = "\u2705 On time",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}