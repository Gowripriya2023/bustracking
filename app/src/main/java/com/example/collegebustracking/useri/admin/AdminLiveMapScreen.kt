package com.example.collegebustracking.useri.admin

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.collegebustracking.model.Bus
import com.example.collegebustracking.model.Route
import com.google.firebase.database.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class LiveBus(
    val bus: Bus,
    val routeName: String,
    val lat: Double,
    val lon: Double,
    val isTripActive: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLiveMapScreen(navController: NavController) {

    val context = LocalContext.current

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference

    var liveBuses by remember { mutableStateOf(listOf<LiveBus>()) }
    var routes by remember { mutableStateOf(mapOf<String, String>()) } // routeId -> routeName

    // Load routes for name lookup
    LaunchedEffect(Unit) {
        database.child("routes")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val map = mutableMapOf<String, String>()
                    for (child in snapshot.children) {
                        val name = child.child("name").getValue(String::class.java) ?: ""
                        map[child.key ?: ""] = name
                    }
                    routes = map
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // Listen to all buses for live location
    LaunchedEffect(Unit) {
        database.child("buses")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<LiveBus>()

                    for (child in snapshot.children) {
                        try {
                            val bus = child.getValue(Bus::class.java) ?: continue
                            val busWithId = bus.copy(id = child.key ?: "")

                            val isTripActive = child.child("isTripActive")
                                .getValue(Boolean::class.java) ?: false

                            val lat = child.child("currentLocation")
                                .child("latitude")
                                .getValue(Double::class.java)

                            val lon = child.child("currentLocation")
                                .child("longitude")
                                .getValue(Double::class.java)

                            if (isTripActive && lat != null && lon != null) {
                                list.add(
                                    LiveBus(
                                        bus = busWithId,
                                        routeName = routes[bus.routeId] ?: "Unknown Route",
                                        lat = lat,
                                        lon = lon,
                                        isTripActive = true
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // Skip invalid entries
                        }
                    }

                    liveBuses = list
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Bus Tracking") }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // Active bus count
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "  🟢 ${liveBuses.size} Active Bus${if (liveBuses.size != 1) "es" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Map
            AndroidView(
                modifier = Modifier.weight(1f),
                factory = { ctx ->

                    Configuration.getInstance().load(
                        ctx,
                        ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                    )

                    val map = MapView(ctx)
                    map.setTileSource(TileSourceFactory.MAPNIK)
                    map.setMultiTouchControls(true)
                    map.controller.setZoom(12.0)

                    // Default center (Kerala)
                    map.controller.setCenter(GeoPoint(10.8505, 76.2711))

                    map
                },
                update = { map ->

                    map.overlays.clear()

                    liveBuses.forEach { liveBus ->

                        val marker = Marker(map)
                        marker.position = GeoPoint(liveBus.lat, liveBus.lon)
                        marker.title = "Bus: ${liveBus.bus.busNumber}"
                        marker.snippet = "Route: ${liveBus.routeName}"
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        map.overlays.add(marker)
                    }

                    // Center on first active bus if available
                    if (liveBuses.isNotEmpty()) {
                        val first = liveBuses.first()
                        map.controller.setCenter(GeoPoint(first.lat, first.lon))
                    }

                    map.invalidate()
                }
            )

            // Bus list below map
            if (liveBuses.isNotEmpty()) {
                liveBuses.forEach { liveBus ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🚌 ${liveBus.bus.busNumber}")
                            Text(liveBus.routeName)
                            Text("📍 Live")
                        }
                    }
                }
            } else {
                Text(
                    text = "No buses are currently active",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
