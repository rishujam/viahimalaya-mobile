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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.via.himalaya.navigation.Route
import com.via.himalaya.navigation.bottomNavItems
import com.via.himalaya.permissions.PermissionHandler
import com.via.himalaya.presentation.auth.AuthViewModel
import com.via.himalaya.presentation.downloads.DownloadedTrekViewModel
import com.via.himalaya.presentation.explore.ExploreViewModel
import com.via.himalaya.presentation.trekDetail.TrekDetailViewModel
import com.via.himalaya.ui.MyApplicationTheme
import com.via.himalaya.ui.components.BottomNavigationBar
import com.via.himalaya.ui.screens.DownloadedTrekScreenRoot
import com.via.himalaya.ui.screens.ExploreScreenRoot
import com.via.himalaya.ui.screens.ProfileScreenRoot
import com.via.himalaya.ui.screens.SignInScreenRoot
import com.via.himalaya.ui.screens.TrekDetailScreenRoot
import org.koin.androidx.compose.koinViewModel

//TODO - Implement real location service
//TODO - Collect the sensor and location data of the trekker locally
//TODO - Pagination
//TODO - Splash Screen
//TODO - Data Entry in Backend
//TODO - Search trek implementation
//TODO - Dark Mode
//TODO - Handle process death
//TODO - Firebase analytics
//TODO - (Bug) Bottom bar color fix
//TODO - Font fix all over the app
//TODO - Handle No internet connection
//Phase 2
//TODO - Profile Page
        //User Pref: Create datastore to store profile object (email, name, treks, distance)
//TODO - If location permission is denied 2 times show a dialog to go to settings and allow permission.
//TODO - Thumbnail in trek listing payload
//TODO - Download tiles of map for offline use


class MainActivity : ComponentActivity() {

    private lateinit var permissionLauncher: PermissionHandler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher = PermissionHandler(this)
        setContent {
            MyApplicationTheme {
                ViaHimalayaApp(permissionLauncher)
            }
        }
    }
}

@Composable
fun ViaHimalayaApp(permissionHandler: PermissionHandler) {
    val navController = rememberNavController()
    val authViewModel = koinViewModel<AuthViewModel>()
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
                startDestination = Route.SignIn
            ) {
                composable<Route.SignIn> {
                    SignInScreenRoot(
                        viewModel = authViewModel,
                        onSignedIn = {
                            navController.navigate(Route.Explore) {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                }
                            }
                        }
                    )
                }
                composable<Route.Explore> {
                    val viewModel = koinViewModel<ExploreViewModel>()
                    ExploreScreenRoot(
                        onTrekClicked = { trek ->
                            navController.navigate(
                                Route.TrekDetail(trek.id, trek.coordinateUrl)
                            )
                        },
                        viewModel = viewModel
                    )
                }
                composable<Route.Profile> {
                    ProfileScreenRoot(
                        authViewModel,
                        onSignOutCompleted = {
                            navController.navigate(Route.SignIn) {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                }
                            }
                        },
                        onDownloadedTrekClicked = {
                            navController.navigate(Route.DownloadedTrek)
                        }
                    )
                }
                composable<Route.TrekDetail> { entry ->
                    val viewModel = koinViewModel<TrekDetailViewModel>()
                    val args = entry.toRoute<Route.TrekDetail>()
                    TrekDetailScreenRoot(viewModel, args.trekId, args.coordinateUrl, {
                        navController.navigateUp()
                    }, permissionHandler)
                }
                composable<Route.DownloadedTrek> {
                    val viewModel = koinViewModel<DownloadedTrekViewModel>()
                    DownloadedTrekScreenRoot(viewModel) { trek ->
                        navController.navigate(
                            Route.TrekDetail(trek.id, trek.coordinateUrl)
                        )
                    }
                }
            }
        }
    }
}
