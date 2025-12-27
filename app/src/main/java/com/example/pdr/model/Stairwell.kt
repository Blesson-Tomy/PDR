package com.example.pdr.model

/**
 * Represents a complete stairwell polygon formed by multiple stair lines with the same polygon ID.
 * The points are stored in order to form a closed polygon for rendering.
 */
data class Stairwell(
    val polygonId: Int,
    val points: List<Pair<Float, Float>>,
    val floorsConnected: List<Double>
)
