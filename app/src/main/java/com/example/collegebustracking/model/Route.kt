package com.example.collegebustracking.model

data class Route(
    val id: String = "",
    val name: String = "",
    val startPoint: String = "",
    val endPoint: String = "",
    val busId: String = "",
    val stops: Map<String, BusStop>? = null   // 🔥 ADD THIS
)

data class BusStop(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val expectedArrivalTime: String = ""   // e.g. "08:15 AM", set by admin
)