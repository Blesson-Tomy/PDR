package com.example.pdr.model

/**
 * STATEFLOW CONCEPT: Enums for Type Safety
 *
 * Instead of passing "stair ascent" as a String, use an enum.
 * This makes it impossible to misspell, and easier to handle in when() statements.
 * The compiler will warn if you forget to handle a case.
 */
enum class MotionType {
    WALKING,
    STATIONARY,
    STAIR_ASCENT,
    STAIR_DESCENT,
    UNKNOWN
}

/**
 * STATEFLOW CONCEPT: Immutable Event Objects
 *
 * @param motionType What activity is the user doing? (from enum above)
 * @param confidence How confident is the ML model (0.0 to 1.0)?
 * @param timestamp When was this classified?
 */
data class MotionEvent(
    val motionType: MotionType,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)
