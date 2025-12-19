package com.example.pdr

import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import com.example.pdr.model.StepDetector
import com.example.pdr.model.HeadingDetector
import com.example.pdr.repository.PdrRepository
import com.example.pdr.repository.MotionRepository
import com.example.pdr.repository.FloorPlanRepository
import com.example.pdr.ui.MainScreen
import com.example.pdr.ui.theme.PDRTheme
import com.example.pdr.viewmodel.FloorPlanViewModel
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel

/**
 * The main entry point of the application.
 *
 * This Activity is responsible for setting up the application's UI, initializing repositories and sensor detectors,
 * and connecting them via callbacks.
 */
class MainActivity : ComponentActivity() {

    // The system service that provides access to the device's sensors.
    private lateinit var sensorManager: SensorManager
    
    // ViewModels (now pure UI state holders)
    private val stepViewModel: StepViewModel by viewModels()
    private val motionViewModel: MotionViewModel by viewModels()
    private val floorPlanViewModel: FloorPlanViewModel by viewModels()

    // Repositories (business logic layer)
    private lateinit var pdrRepository: PdrRepository
    private lateinit var motionRepository: MotionRepository
    private lateinit var floorPlanRepository: FloorPlanRepository

    // Sensor detectors (model layer)
    private var stepDetector: StepDetector? = null
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

        // Initialize repositories
        initializeRepositories()
        
        // Initialize sensor detectors and connect them to repositories
        initializeSensorDetectors()

        // Set the main UI content of the activity.
        setContent {
            PDRTheme {
                // Observe ViewModel changes and sync with detectors
                ObserveSettingsChanges()
                
                // The MainScreen composable is the root of the UI, passing in the required ViewModels.
                MainScreen(stepViewModel, motionViewModel, floorPlanViewModel)
            }
        }
    }

    /**
     * Observes ViewModel setting changes and syncs them to the detectors.
     */
    @Composable
    private fun ObserveSettingsChanges() {
        LaunchedEffect(stepViewModel.threshold, stepViewModel.windowSize, stepViewModel.debounce) {
            syncDetectorSettings()
        }
    }

    /**
     * Syncs all detector settings from ViewModel values.
     * Called whenever settings change in the UI.
     */
    private fun syncDetectorSettings() {
        stepDetector?.threshold = stepViewModel.threshold
        stepDetector?.windowSize = stepViewModel.windowSize.toInt()
        stepDetector?.debounce = stepViewModel.debounce.toLong()
    }

    /**
     * Initializes all repositories and sets up their callbacks to update ViewModels.
     */
    private fun initializeRepositories() {
        // Floor Plan Repository - loads wall data from assets
        floorPlanRepository = FloorPlanRepository(application)
        val walls = floorPlanRepository.loadFloorPlan()
        floorPlanViewModel.loadWalls(walls)

        // PDR Repository - handles path calculation
        pdrRepository = PdrRepository()
        pdrRepository.onStepDetected = { strideLengthCm, cadence, newPoint ->
            stepViewModel.onStepDetected(strideLengthCm, cadence, newPoint)
        }
        pdrRepository.onCadenceUpdated = { averageCadence ->
            stepViewModel.onCadenceUpdated(averageCadence)
        }

        // Motion Repository - handles ML classification
        motionRepository = MotionRepository(application)
        motionRepository.onMotionDetected = { motionType, confidence ->
            motionViewModel.onMotionDetected(motionType, confidence)
        }
    }

    /**
     * Initializes sensor detectors and starts listening for sensor events.
     */
    private fun initializeSensorDetectors() {
        // Initialize and start step detector
        val userHeight = stepViewModel.height.toFloatOrNull() ?: 175f
        stepDetector = StepDetector(sensorManager, userHeight, stepViewModel.kValue, stepViewModel.cValue)
        stepDetector?.threshold = stepViewModel.threshold
        stepDetector?.windowSize = stepViewModel.windowSize.toInt()
        stepDetector?.debounce = stepViewModel.debounce.toLong()
        
        // Connect StepDetector directly to repositories
        stepDetector?.onStepDetected = { strideLength, cadence ->
            pdrRepository.processStep(strideLength, cadence, stepViewModel.heading, stepViewModel.cadenceAverageSize.toInt())
        }
        stepDetector?.onSensorDataReceived = { accX, accY, accZ ->
            motionRepository.onSensorDataReceived(accX, accY, accZ)
        }
        stepDetector?.start()

        // Initialize and start heading detector
        headingDetector = HeadingDetector(sensorManager)
        headingDetector?.onHeadingChanged = { heading ->
            stepViewModel.heading = heading
        }
        headingDetector?.start()
    }

    /**
     * Called when the activity is being destroyed. This is the correct place to clean up resources.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Stop the sensor listeners to prevent battery drain and memory leaks when the app is closed.
        stepDetector?.stop()
        headingDetector?.stop()
        motionRepository.cleanup()
    }
}