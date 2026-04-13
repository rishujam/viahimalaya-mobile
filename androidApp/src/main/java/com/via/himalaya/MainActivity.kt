package com.via.himalaya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.via.himalaya.permissions.PermissionHandler
import com.via.himalaya.ui.MyApplicationTheme

class MainActivity : ComponentActivity() {
    
    // Initialize permission handler at activity level to avoid lifecycle issues
    private lateinit var permissionHandler: PermissionHandler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize permission handler before setContent
        permissionHandler = PermissionHandler(this)
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RealSensorTestScreen(permissionHandler = permissionHandler)
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
