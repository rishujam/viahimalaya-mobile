package com.via.himalaya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.via.himalaya.navigation.Route
import com.via.himalaya.navigation.bottomNavItems
import com.via.himalaya.permissions.PermissionHandler
import com.via.himalaya.presentation.explore.ExploreViewModel
import com.via.himalaya.ui.MyApplicationTheme
import com.via.himalaya.ui.components.BottomNavigationBar
import com.via.himalaya.ui.screens.ExploreScreenRoot
import com.via.himalaya.ui.screens.ProfileScreen
import com.via.himalaya.ui.screens.TrekDetailScreen
import org.koin.androidx.compose.koinViewModel

//TODO - Pagination
//TODO - Splash Screen
//TODO - Detail Screen
//TODO - Trek Screen
//TODO - Data Entry in Backend
//TODO - Authentication
//TODO - On Process death location permission is asked and explore list is not loaded

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
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute?.let { route ->
        bottomNavItems.any { item ->
            when (item.route) {
                is Route.Explore -> route.contains("Explore")
                is Route.Profile -> route.contains("Profile")
                else -> false
            }
        }
    } ?: false

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(navController = navController)
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Route.ViaHimalayaGraph,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            navigation<Route.ViaHimalayaGraph>(
                startDestination = Route.Explore
            ) {
                composable<Route.Explore> {
                    val viewModel = koinViewModel<ExploreViewModel>()
                    ExploreScreenRoot(
                        onTrekClicked = { trek ->
                            navController.navigate(
                                Route.TrekDetail(trek.id)
                            )
                        },
                        viewModel = viewModel
                    )
                }
                composable<Route.Profile> {
                    ProfileScreen()
                }
                composable<Route.TrekDetail> { entry ->
                    val args = entry.toRoute<Route.TrekDetail>()
                    TrekDetailScreen()
                }
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
