package com.via.himalaya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.via.himalaya.di.trekViewModelFactory
import com.via.himalaya.permissions.PermissionHandler
import com.via.himalaya.trek.TrekScreen
import com.via.himalaya.presentation.TrekScreenUIEvent
import com.via.himalaya.presentation.TrekViewModel
import com.via.himalaya.ui.MyApplicationTheme

class MainActivity : ComponentActivity() {
    
    // Initialize permission handler at activity level to avoid lifecycle issues
    private lateinit var permissionHandler: PermissionHandler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize permission handler before setContent
        permissionHandler = PermissionHandler(this)
        permissionHandler.checkAndRequestPermissions()
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Use the official KMP ViewModel with proper factory
                    val viewModel: TrekViewModel = viewModel(
                        factory = trekViewModelFactory
                    )
                    val sensorListener = SensorListener(this@MainActivity) { sensorData ->
                        viewModel.onEvent(TrekScreenUIEvent.LocationUpdate(sensorData))
                    }
                    
                    TrekScreen(viewModel::onEvent, sensorListener, viewModel.state)
                }
            }
        }
    }
}

@Preview
@Composable
fun DefaultPreview() {
    MyApplicationTheme {
        // For preview, we can't use real permission handler
        Text("Real Sensor Test Screen")
    }
}
