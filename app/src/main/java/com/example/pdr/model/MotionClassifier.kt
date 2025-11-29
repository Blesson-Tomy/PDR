package com.example.pdr.model

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Manages the TFLite model for motion classification.
 */
class MotionClassifier(context: Context, modelFileName: String = "model.tflite") {

    // Load metadata safely using the MotionMeta class.
    val meta: MotionMeta = MotionMeta.fromAssets(context)

    private val interpreter: Interpreter

    init {
        // Load the TFLite model from the assets folder.
        val modelBuffer = loadModelFile(context, modelFileName)
        interpreter = Interpreter(modelBuffer)
    }

    private fun loadModelFile(context: Context, modelFileName: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelFileName)
        FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    /**
     * Runs inference on a window of sensor data.
     * @param data A 2D array of sensor data with shape [windowSize, numFeatures].
     * @return A FloatArray of probabilities for each class.
     */
    fun predict(data: Array<FloatArray>): FloatArray {
        // 1. Create the input ByteBuffer
        val inputBuffer = ByteBuffer.allocateDirect(1 * meta.windowSize * 6 * 4) // 1 batch, 100 window, 6 features, 4 bytes/float
        inputBuffer.order(ByteOrder.nativeOrder())

        // 2. Normalize and fill the ByteBuffer
        for (i in 0 until meta.windowSize) {
            for (j in 0 until 6) {
                // Ensure we don't go out of bounds if data is smaller than expected
                if (i < data.size && j < data[i].size) {
                    val normalizedValue = (data[i][j] - meta.mean[j]) / meta.std[j]
                    inputBuffer.putFloat(normalizedValue)
                }
            }
        }

        // 3. Create the output buffer
        val output = Array(1) { FloatArray(meta.classNames.size) }

        // 4. Run the model
        interpreter.run(inputBuffer, output)

        return output[0]
    }

    /**
     * Closes the interpreter to release TFLite resources.
     */
    fun close() {
        interpreter.close()
    }
}
