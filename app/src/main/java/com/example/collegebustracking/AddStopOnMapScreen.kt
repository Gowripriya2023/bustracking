package com.example.collegebustracking

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.collegebustracking.model.BusStop
import com.example.collegebustracking.useri.home.calculateDistance
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun AddStopOnMapScreen(
    navController: NavController,
    routeId: String
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val database = FirebaseDatabase
        .getInstance("https://collegebustracking-f21c0-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .reference

    var selectedLat by remember { mutableStateOf(0.0) }
    var selectedLon by remember { mutableStateOf(0.0) }
    var stopName by remember { mutableStateOf("") }
    var expectedArrivalTime by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {

        // 🔎 SEARCH BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Location") },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = {

                if (searchQuery.isNotBlank()) {

                    scope.launch {
                        val geoPoint = searchLocation(searchQuery)

                        geoPoint?.let {

                            mapViewRef?.controller?.setZoom(17.0)
                            mapViewRef?.controller?.setCenter(it)

                            mapViewRef?.overlays?.removeAll { it is Marker }

                            val marker = Marker(mapViewRef)
                            marker.position = it
                            marker.title = "Search Result"
                            mapViewRef?.overlays?.add(marker)

                            mapViewRef?.invalidate()

                            selectedLat = it.latitude
                            selectedLon = it.longitude
                        } ?: run {
                            Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

            }) {
                Text("Go")
            }
        }

        // 🗺 MAP VIEW
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
        AndroidView(
            factory = {

                Configuration.getInstance()
                    .load(context, context.getSharedPreferences("osmdroid", 0))

                val map = MapView(context)
                map.setMultiTouchControls(true)
                map.setBuiltInZoomControls(true)
                map.controller.setZoom(15.0)

                val defaultPoint = GeoPoint(10.8505, 76.2711) // Kerala default
                map.controller.setCenter(defaultPoint)

                mapViewRef = map

                // Use MapEventsOverlay for tap handling (allows zoom gestures to pass through)
                val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (p == null) return false

                        selectedLat = p.latitude
                        selectedLon = p.longitude

                        // Keep the tap overlay, clear only markers
                        map.overlays.removeAll { it is Marker }

                        val marker = Marker(map)
                        marker.position = p
                        marker.title = "Selected Stop"
                        map.overlays.add(marker)

                        map.invalidate()
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                })
                map.overlays.add(tapOverlay)

                map
            },
            modifier = Modifier.fillMaxSize()
        )
        } // end Box

        // 📝 STOP NAME FIELD
        OutlinedTextField(
            value = stopName,
            onValueChange = { stopName = it },
            label = { Text("Stop Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )

        // 🕐 EXPECTED ARRIVAL TIME FIELD
        OutlinedTextField(
            value = expectedArrivalTime,
            onValueChange = { expectedArrivalTime = it },
            label = { Text("Expected Arrival Time (e.g. 08:15 AM)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            singleLine = true
        )

        // 💾 SAVE BUTTON
        Button(
            onClick = {

                if (stopName.isNotBlank() && selectedLat != 0.0) {

                    val stopsRef = database.child("routes")
                        .child(routeId)
                        .child("stops")

                    val stopId = stopsRef.push().key!!

                    val stop = BusStop(
                        id = stopId,
                        name = stopName,
                        latitude = selectedLat,
                        longitude = selectedLon,
                        expectedArrivalTime = expectedArrivalTime.trim(),
                        order = 0
                    )

                    // Save the new stop first
                    stopsRef.child(stopId).setValue(stop)
                        .addOnSuccessListener {
                            // After saving, fetch all stops and re-sort by distance from the first stop
                            stopsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    val allStops = mutableListOf<Pair<String, BusStop>>()
                                    for (child in snapshot.children) {
                                        val s = child.getValue(BusStop::class.java)
                                        if (s != null) {
                                            allStops.add(child.key!! to s)
                                        }
                                    }

                                    if (allStops.size >= 2) {
                                        // Use the first stop (by current order or first added) as the origin
                                        val origin = allStops.minByOrNull { it.second.order }?.second
                                            ?: allStops.first().second

                                        // Sort all stops by distance from the origin
                                        val sorted = allStops.sortedBy { (_, s) ->
                                            calculateDistance(
                                                origin.latitude, origin.longitude,
                                                s.latitude, s.longitude
                                            )
                                        }

                                        // Re-save each stop with updated order
                                        sorted.forEachIndexed { index, (key, s) ->
                                            stopsRef.child(key).child("order").setValue(index)
                                        }
                                    }

                                    Toast.makeText(context, "Stop Added & Route Sorted!", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Toast.makeText(context, "Stop added but sort failed", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            })
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }

                } else {
                    Toast.makeText(context, "Select location & enter name", Toast.LENGTH_SHORT).show()
                }

            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("Save Stop")
        }
    }
}

/*
 🔎 Nominatim Search Function
*/
suspend fun searchLocation(query: String): GeoPoint? {
    return withContext(Dispatchers.IO) {
        try {
            val url =
                URL("https://nominatim.openstreetmap.org/search?q=$query&format=json&limit=1")

            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty(
                "User-Agent",
                "CollegeBusTrackingApp"
            )

            val response = connection.inputStream.bufferedReader().readText()
            val jsonArray = JSONArray(response)

            if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                val lat = obj.getString("lat").toDouble()
                val lon = obj.getString("lon").toDouble()
                GeoPoint(lat, lon)
            } else null

        } catch (e: Exception) {
            null
        }
    }
}