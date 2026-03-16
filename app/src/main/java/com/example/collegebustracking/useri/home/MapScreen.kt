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
    val φ1 = lat1 * PI / 180
    val φ2 = lat2 * PI / 180
    val Δφ = (lat2 - lat1) * PI / 180
    val Δλ = (lon2 - lon1) * PI / 180

    val a = sin(Δφ / 2) * sin(Δφ / 2) +
            cos(φ1) * cos(φ2) *
            sin(Δλ / 2) * sin(Δλ / 2)

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

    val mapView = remember { MapView(context) }
    val busMarker = remember { Marker(mapView) }

    var busId by remember { mutableStateOf("") }

    var distanceToStop by remember { mutableStateOf(0.0) }
    var etaMinutes by remember { mutableStateOf(0) }
    var isTripActive by remember { mutableStateOf(false) }

    /* ---------------- LOAD ROUTE + BUS ---------------- */

    LaunchedEffect(routeId) {

        // Load stops
        database.child("routes")
            .child(routeId)
            .child("stops")
            .get()
            .addOnSuccessListener { snapshot ->

                val stopList = mutableListOf<BusStop>()

                snapshot.children.forEach { child ->

                    val stop = child.getValue(BusStop::class.java)

                    stop?.let {
                        stopList.add(it.copy(id = child.key ?: ""))
                    }
                }

                stops = stopList
            }

        // Find bus using routeId
        database.child("buses")
            .orderByChild("routeId")
            .equalTo(routeId)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    for (busSnap in snapshot.children) {

                        busId = busSnap.key ?: ""
                        break
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /* ---------------- BUS LIVE LOCATION ---------------- */

    LaunchedEffect(busId) {

        if (busId.isEmpty()) return@LaunchedEffect

        database.child("buses")
            .child(busId)
            .child("currentLocation")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    val lat = snapshot.child("latitude").getValue(Double::class.java)
                    val lon = snapshot.child("longitude").getValue(Double::class.java)

                    if (lat != null && lon != null) {

                        isTripActive = true
                        val busLocation = GeoPoint(lat, lon)

                        busMarker.position = busLocation
                        mapView.controller.setCenter(busLocation)

                        // Find nearest stop
                        val nearest = stops.minByOrNull { stop ->

                            val dx = stop.latitude - lat
                            val dy = stop.longitude - lon
                            dx * dx + dy * dy
                        }

                        nearest?.let { stop ->

                            nextStop = stop

                            val distance = calculateDistance(
                                lat,
                                lon,
                                stop.latitude,
                                stop.longitude
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

                        // Trip not started → show first stop
                        if (stops.isNotEmpty()) {

                            val firstStop = stops.first()

                            val startLocation = GeoPoint(
                                firstStop.latitude,
                                firstStop.longitude
                            )

                            busMarker.position = startLocation
                            mapView.controller.setCenter(startLocation)
                        }
                    }

                    mapView.invalidate()
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

                mapView.apply {

                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(16.5)

                    busMarker.icon = resizeDrawable(
                        ctx,
                        com.example.collegebustracking.R.drawable.bus_icon,
                        60,
                        60
                    )

                    busMarker.setAnchor(
                        Marker.ANCHOR_CENTER,
                        Marker.ANCHOR_BOTTOM
                    )

                    overlays.add(busMarker)
                }
            },

            update = { map ->

                map.overlays.clear()

                // Stops
                stops.forEach { stop ->

                    val marker = Marker(map)
                    marker.position = GeoPoint(stop.latitude, stop.longitude)
                    marker.title = stop.name

                    map.overlays.add(marker)
                }

                // Route line
                val routeLine = Polyline()

                val points = stops.map {
                    GeoPoint(it.latitude, it.longitude)
                }

                routeLine.setPoints(points)
                routeLine.outlinePaint.color = android.graphics.Color.BLUE
                routeLine.outlinePaint.strokeWidth = 8f

                map.overlays.add(routeLine)

                // Bus
                map.overlays.add(busMarker)
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
                    text = if (isTripActive) "🟢 Bus is Live" else "⏳ Waiting for bus",
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
                        Text(text = "📍 $distText")
                        Text(text = "⏱ ETA: ${if (etaMinutes < 1) "< 1 min" else "$etaMinutes min"}")
                    }
                }
            }
        }
    }
}