package com.example.pdr.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.InputStreamReader

/**
 * A data class that represents the structure of the `model_meta.json` file.
 * Using @SerializedName ensures that the JSON fields are correctly mapped to the Kotlin properties,
 * which helps prevent null pointer exceptions if the names don't match exactly.
 */
data class MotionMeta(
    val mean: List<Float>,
    val std: List<Float>,
    @SerializedName("classes")
    val classNames: List<String>,
    @SerializedName("window_size")
    val windowSize: Int,
    @SerializedName("step_size")
    val stepSize: Int
) {
    companion object {
        /**
         * Loads and parses the metadata file from the assets folder.
         * The `use` block automatically closes the streams to prevent resource leaks.
         */
        fun fromAssets(context: Context, fileName: String = "model_meta.json"): MotionMeta {
            context.assets.open(fileName).use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    return Gson().fromJson(reader, MotionMeta::class.java)
                }
            }
        }
    }
}
