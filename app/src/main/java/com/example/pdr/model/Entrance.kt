package com.example.pdr.model

/**
 * Represents an entrance, opening, or stairwell entry/exit point.
 * Some entrances are marked with "stairs": true to indicate they are stairwell entry/exits.
 */
data class Entrance(
    val id: Int,
    val x: Float,
    val y: Float,
    val stairs: Boolean = false
)
