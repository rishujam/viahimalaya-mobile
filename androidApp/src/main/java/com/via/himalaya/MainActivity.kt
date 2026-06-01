package com.via.himalaya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.via.himalaya.navigation.BottomNavigation
import com.via.himalaya.navigation.Screen
import com.via.himalaya.permissions.PermissionHandler
import com.via.himalaya.screens.ExploreScreen
import com.via.himalaya.screens.ProfileScreen
import com.via.himalaya.ui.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var permissionHandler: PermissionHandler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionHandler = PermissionHandler(this)
        permissionHandler.checkAndRequestPermissions()
        
        setContent {
            MyApplicationTheme {
                ViaHimalayaApp()
            }
        }
    }
}

@Composable
fun ViaHimalayaApp() {
    var currentRoute by remember { mutableStateOf(Screen.Explore) }
    
    Scaffold(
        bottomBar = {
            BottomNavigation (
                currentRoute = Screen.Explore,
                onNavigate = { route ->
                    currentRoute = route
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentRoute) {
                Screen.Explore -> {
                    ExploreScreen(
                        onTrekClicked = { trek ->

                        }
                    )
                }
                Screen.Profile -> {
                    ProfileScreen()
                }
                else -> {}
            }
        }
    }
}

@Preview
@Composable
fun DefaultPreview() {
    MyApplicationTheme {
        ViaHimalayaApp()
    }
}
