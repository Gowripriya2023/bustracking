package com.example.collegebustracking.useri.home

import android.content.Context
import androidx.compose.foundation.background
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

fun resizeDrawable(
    context: Context,
    resId: Int,
    width: Int,
    height: Int
): android.graphics.drawable.Drawable {

    val drawable = context.getDrawable(resId)!!
    val bitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap

    val smallBitmap = android.graphics.Bitmap.createScaledBitmap(
        bitmap,
        width,
        height,
        false
    )

    return android.graphics.drawable.BitmapDrawable(context.resources, smallBitmap)
}

fun calculateDistance(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {

    val R = 6371e3
    val p1 = lat1 * PI / 180
    val p2 = lat2 * PI / 180
    val dp = (lat2 - lat1) * PI / 180
    val dl = (lon2 - lon1) * PI / 180

    val a = sin(dp / 2) * sin(dp / 2) +
            cos(p1) * cos(p2) *
            sin(dl / 2) * sin(dl / 2)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return R * c
}

@Composable
fun MapScreen(navController: NavController, routeId: String) {

    val context = LocalContext.current

    val database = FirebaseDatabase.getInstance(
        "https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/"
    ).reference

    var stops by remember { mutableStateOf(listOf<BusStop>()) }

    var nextStop by remember { mutableStateOf<BusStop?>(null) }
    var passedStops by remember { mutableStateOf(setOf<String>()) }

    var busId by remember { mutableStateOf("") }

    // Bus location as Compose state — triggers recomposition on every update
    var busLat by remember { mutableStateOf<Double?>(null) }
    var busLon by remember { mutableStateOf<Double?>(null) }

    var distanceToStop by remember { mutableStateOf(0.0) }
    var etaMinutes by remember { mutableStateOf(0) }
    var isTripActive by remember { mutableStateOf(false) }

    /* ---------------- LOAD STOPS (real-time) ---------------- */

    LaunchedEffect(routeId) {
        database.child("routes")
            .child(routeId)
            .child("stops")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val stopList = mutableListOf<BusStop>()
                    snapshot.children.forEach { child ->
                        val stop = child.getValue(BusStop::class.java)
                        stop?.let {
                            stopList.add(it.copy(id = child.key ?: ""))
                        }
                    }
                    stops = stopList
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /* ---------------- FIND BUS BY ROUTE (real-time) ---------------- */

    LaunchedEffect(routeId) {
        database.child("buses")
            .orderByChild("routeId")
            .equalTo(routeId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (busSnap in snapshot.children) {
                        val newBusId = busSnap.key ?: ""
                        if (newBusId != busId) {
                            busId = newBusId
                        }
                        break
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /* ---------------- BUS LIVE LOCATION (real-time) ---------------- */

    LaunchedEffect(busId) {

        if (busId.isEmpty()) return@LaunchedEffect

        // Listen to entire bus node for location + trip status
        database.child("buses")
            .child(busId)
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    val tripActive = snapshot.child("isTripActive")
                        .getValue(Boolean::class.java) ?: false

                    val lat = snapshot.child("currentLocation")
                        .child("latitude")
                        .getValue(Double::class.java)

                    val lon = snapshot.child("currentLocation")
                        .child("longitude")
                        .getValue(Double::class.java)

                    if (tripActive && lat != null && lon != null) {

                        isTripActive = true
                        busLat = lat
                        busLon = lon

                        // Find nearest stop
                        val nearest = stops.minByOrNull { stop ->
                            val dx = stop.latitude - lat
                            val dy = stop.longitude - lon
                            dx * dx + dy * dy
                        }

                        nearest?.let { stop ->

                            nextStop = stop

                            val distance = calculateDistance(
                                lat, lon,
                                stop.latitude, stop.longitude
                            )

                            distanceToStop = distance

                            val speed = 30.0
                            val speedMs = speed * 1000 / 3600
                            etaMinutes = (distance / speedMs / 60).toInt()

                            val newPassed = passedStops.toMutableSet()
                            stops.forEach { s ->
                                val dx = s.latitude - lat
                                val dy = s.longitude - lon
                                val d = dx * dx + dy * dy
                                if (d < 0.0001) {
                                    newPassed.add(s.id)
                                }
                            }
                            passedStops = newPassed
                        }

                    } else {
                        isTripActive = false
                        busLat = null
                        busLon = null
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /* ---------------- UI ---------------- */

    Column(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->

                Configuration.getInstance().load(
                    ctx,
                    ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                )

                val mapView = MapView(ctx)
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.setMultiTouchControls(true)
                mapView.controller.setZoom(16.5)

                // Default center (Kerala)
                mapView.controller.setCenter(GeoPoint(10.8505, 76.2711))

                mapView
            },

            update = { map ->

                map.overlays.clear()

                // Draw stop markers
                stops.forEach { stop ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(stop.latitude, stop.longitude)
                    marker.title = stop.name
                    map.overlays.add(marker)
                }

                // Draw route line
                if (stops.isNotEmpty()) {
                    val routeLine = Polyline()
                    val points = stops.map { GeoPoint(it.latitude, it.longitude) }
                    routeLine.setPoints(points)
                    routeLine.outlinePaint.color = android.graphics.Color.BLUE
                    routeLine.outlinePaint.strokeWidth = 8f
                    map.overlays.add(routeLine)
                }

                // Draw bus marker at live position
                val lat = busLat
                val lon = busLon

                if (lat != null && lon != null) {

                    val busMarker = Marker(map)
                    busMarker.position = GeoPoint(lat, lon)
                    busMarker.title = "Bus Location"

                    try {
                        busMarker.icon = resizeDrawable(
                            context,
                            com.example.collegebustracking.R.drawable.bus_icon,
                            60, 60
                        )
                    } catch (e: Exception) {
                        // Use default marker if bus_icon not found
                    }

                    busMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(busMarker)

                    // Center map on bus
                    map.controller.setCenter(GeoPoint(lat, lon))

                } else if (stops.isNotEmpty()) {
                    // No live location — center on first stop
                    val firstStop = stops.first()
                    map.controller.setCenter(
                        GeoPoint(firstStop.latitude, firstStop.longitude)
                    )
                }

                map.invalidate()
            }
        )

        // Live Info Bar
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

        if (isTripActive && nextStop != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
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
                            "${"%,.1f".format(distanceToStop / 1000)} km away"
                        }
                        Text(text = "\uD83D\uDCCD $distText")
                        Text(text = "\u23F1 ETA: ${if (etaMinutes < 1) "< 1 min" else "$etaMinutes min"}")
                    }
                }
            }
        }
    }
}