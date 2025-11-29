package com.example.pdr

import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.example.pdr.sensor.HeadingDetector
import com.example.pdr.sensor.StepDetector
import com.example.pdr.ui.StepCanvas
import com.example.pdr.ui.theme.PDRTheme
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel

class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorManager
    private val stepViewModel: StepViewModel by viewModels()
    private val motionViewModel: MotionViewModel by viewModels() // Added MotionViewModel

    private var stepDetector: StepDetector? = null
    private var headingDetector: HeadingDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Pass both ViewModels to the StepDetector
        stepDetector = StepDetector(sensorManager, stepViewModel, motionViewModel)

        stepDetector?.start()

        headingDetector = HeadingDetector(sensorManager)
        headingDetector?.start()

        // Observe heading and update viewModel
        headingDetector?.heading?.observe(this) { angle ->
            stepViewModel.heading = angle
        }

        setContent {
            PDRTheme {
                // Pass both ViewModels to the StepCanvas to fix the build error
                StepCanvas(stepViewModel, motionViewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stepDetector?.stop()
        headingDetector?.stop()
        // The MotionViewModel will automatically close the classifier in onCleared()
    }

}
