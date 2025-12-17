package com.example.pdr

import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.example.pdr.sensor.HeadingDetector
import com.example.pdr.sensor.StepDetector
import com.example.pdr.ui.MainScreen
import com.example.pdr.ui.theme.PDRTheme
import com.example.pdr.viewmodel.FloorPlanViewModel
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel

/**
 * The main entry point of the application.
 *
 * This Activity is responsible for setting up the application's UI, initializing the sensor detectors
 * (for steps and heading), and connecting them to their respective ViewModels.
 */
class MainActivity : ComponentActivity() {

    // The system service that provides access to the device's sensors.
    private lateinit var sensorManager: SensorManager
    // The ViewModel for the PDR system, accessed via the viewModels delegate.
    private val stepViewModel: StepViewModel by viewModels()
    // The ViewModel for motion classification, accessed via the viewModels delegate.
    private val motionViewModel: MotionViewModel by viewModels()
    // The ViewModel for the floor plan, accessed via the viewModels delegate.
    private val floorPlanViewModel: FloorPlanViewModel by viewModels()

    // The custom class that handles step detection logic.
    private var stepDetector: StepDetector? = null
    // The custom class that handles heading (compass) logic.
    private var headingDetector: HeadingDetector? = null

    /**
     * Called when the activity is first created. This is where most of the initialization happens.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This allows the app to draw behind the system bars (status bar, navigation bar).
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // Get an instance of the SensorManager.
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Initialize the StepDetector and start listening for accelerometer events.
        stepDetector = StepDetector(sensorManager, stepViewModel, motionViewModel)
        stepDetector?.start()

        // Initialize the HeadingDetector and start listening for orientation sensor events.
        headingDetector = HeadingDetector(sensorManager)
        headingDetector?.start()

        // Observe the LiveData `heading` from the HeadingDetector. When it changes,
        // update the `heading` property in the StepViewModel.
        headingDetector?.heading?.observe(this) { angle ->
            stepViewModel.heading = angle
        }

        // Set the main UI content of the activity.
        setContent {
            PDRTheme {
                // The MainScreen composable is the root of the UI, passing in the required ViewModels.
                MainScreen(stepViewModel, motionViewModel, floorPlanViewModel)
            }
        }
    }

    /**
     * Called when the activity is being destroyed. This is the correct place to clean up resources.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Stop the sensor listeners to prevent battery drain and memory leaks when the app is closed.
        stepDetector?.stop()
        headingDetector?.stop()
    }

}
