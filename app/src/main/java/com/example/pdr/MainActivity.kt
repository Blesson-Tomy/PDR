package com.example.pdr

import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.example.pdr.model.StepDetector
import com.example.pdr.model.HeadingDetector
import com.example.pdr.model.MotionType
import com.example.pdr.repository.PdrRepository
import com.example.pdr.repository.MotionRepository
import com.example.pdr.repository.FloorPlanRepository
import com.example.pdr.ui.MainScreen
import com.example.pdr.ui.components.BuildingSelector
import com.example.pdr.ui.theme.PDRTheme
import com.example.pdr.viewmodel.FloorPlanViewModel
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel

/**
 * The main entry point of the application.
 *
 * STATEFLOW ARCHITECTURE:
 * 1. Repositories emit events/state via StateFlows
 * 2. ViewModels collect from StateFlows and expose Compose state
 * 3. MainActivity wires everything together during initialization
 * 4. Composables read ViewModel state (no need to know about repositories)
 */
class MainActivity : ComponentActivity() {

    // The system service that provides access to the device's sensors.
    private lateinit var sensorManager: SensorManager
    
    // ViewModels (now pure UI state holders + StateFlow collectors)
    private val stepViewModel: StepViewModel by viewModels()
    private val motionViewModel: MotionViewModel by viewModels()
    private val floorPlanViewModel: FloorPlanViewModel by viewModels()

    // Repositories (business logic layer - emit via StateFlows)
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

        // Initialize repositories (these emit StateFlows)
        initializeRepositories()
        
        // Initialize sensor detectors and wire to repositories
        initializeSensorDetectors()

        // Set the main UI content of the activity.
        setContent {
            PDRTheme {
                // Track if data has been loaded
                val isDataLoaded = remember { mutableStateOf(floorPlanViewModel.isDataLoaded) }
                
                // Observe floor plan data loading state
                LaunchedEffect(floorPlanViewModel.isDataLoaded) {
                    isDataLoaded.value = floorPlanViewModel.isDataLoaded
                }
                
                if (isDataLoaded.value) {
                    // Show main app with data loaded
                    // Observe ViewModel changes and sync with detectors
                    ObserveSettingsChanges()
                    
                    // NEW: Observe motion events for floor transitions
                    ObserveMotionEvents()
                    
                    // The MainScreen composable is the root of the UI, passing in the required ViewModels.
                    MainScreen(stepViewModel, motionViewModel, floorPlanViewModel)
                } else {
                    // Show building selector to load data from Firestore
                    BuildingSelector(floorPlanViewModel) {
                        isDataLoaded.value = true
                    }
                }
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
     * STATEFLOW IN ACTION:
     * Observes motion events from the repository and handles floor transitions.
     * 
     * This replaces the old callback spaghetti. Now:
     * - MotionRepository emits MotionEvent to motionEvents StateFlow
     * - This composable collects the StateFlow
     * - When stair is detected, we handle the floor transition
     * - When floor changes, other parts of the app react via their own flows
     */
    @Composable
    private fun ObserveMotionEvents() {
        val motionEvent = motionRepository.motionEvents.collectAsState()
        
        LaunchedEffect(motionEvent.value) {
            motionEvent.value?.let { event ->
                when (event.motionType) {
                    MotionType.STAIR_ASCENT -> {
                        // User is going up
                        // TODO: Implement floor change logic
                    }
                    MotionType.STAIR_DESCENT -> {
                        // User is going down
                        // TODO: Implement floor change logic
                    }
                    else -> {
                        // Walking, stationary, etc.
                    }
                }
            }
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
     * Initializes all repositories and sets up their StateFlow emission.
     * 
     * WIRING:
     * - Repositories create and hold StateFlows
     * - ViewModels get injected with repository references
     * - ViewModels use viewModelScope to collect from StateFlows
     * - UI never directly accesses repositories (only ViewModels)
     */
    private fun initializeRepositories() {
        // Floor Plan Repository - loads wall, stairwell, and entrance data from Firestore
        floorPlanRepository = FloorPlanRepository(application)
        floorPlanViewModel.floorPlanRepository = floorPlanRepository
        
        // NOTE: Walls, stairs, and entrances are now loaded from Firestore
        // when user selects a building/floor in BuildingSelector

        // PDR Repository - handles path calculation
        pdrRepository = PdrRepository()
        // Inject repository reference into ViewModel
        // ViewModel will automatically start collecting from repository's StateFlows
        stepViewModel.pdrRepository = pdrRepository

        // Motion Repository - handles ML classification
        motionRepository = MotionRepository(application)
        // Inject repository reference into ViewModel
        motionViewModel.motionRepository = motionRepository
    }

    /**
     * Initializes sensor detectors and starts listening for sensor events.
     * 
     * DATA FLOW:
     * StepDetector (sensor) 
     *   → PdrRepository (process step, emit to StateFlow)
     *   → StepViewModel (collect and display)
     *   → PdrScreen (read ViewModel state)
     * 
     * No callbacks needed - StateFlows handle everything!
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