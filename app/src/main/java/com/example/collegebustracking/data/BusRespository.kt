package com.example.collegebustracking.data

import com.google.firebase.database.*
import org.osmdroid.util.GeoPoint

object BusRepository {

    fun listenToBusLocation(
        busId: String,
        onLocationChanged: (GeoPoint) -> Unit
    ) {
        val busRef = FirebaseDatabase.getInstance()
            .getReference("buses")
            .child(busId)
            .child("currentLocation")

        busRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lng = snapshot.child("longitude").getValue(Double::class.java)

                if (lat != null && lng != null) {
                    onLocationChanged(GeoPoint(lat, lng))
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}