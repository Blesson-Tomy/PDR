package com.example.pdr.model

/**
 * Represents a single stair segment in the floor plan.
 * Multiple stair lines with the same stair_polygon_id form a complete stairwell polygon.
 */
data class StairLine(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val type: String,
    val stair_polygon_id: Int,
    val floors_connected: List<Double>
)
