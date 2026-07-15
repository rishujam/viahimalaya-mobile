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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.via.himalaya.util.NetworkUtil
import org.koin.androidx.compose.koinViewModel

//TODO - Collect the sensor and location data of the trekker locally
        //Create table to store path followed by user mapped to trek id - Done need to test

//TODO - Data Entry in Backend
//TODO - Add loading while loading signinProfiles
//TODO - Download tiles of map for offline use
        //(Bug) If its the last trek in downloaded list and we try to delete it the UI dosent gets updated
        //On notification allow start downloading
        //Foreground Notification while downloading
        //If incomplete remove the half downloaded file

//Phase 2
//TODO - SignInScreen Video
//TODO - (Bug) Coming back to explore page the state of isFocused in not maintained
//TODO - Add view on google maps (the destination)
//TODO - SignIn Page improved video branding
//TODO - Add span in LazyColumn
//TODO - Listen to location only when device is moving - optimize battery
//TODO - Font fix all over the app
//TODO - Profile Page
        //User Pref: Create datastore to store profile object (email, name, treks, distance)
//TODO - If location permission is denied 2 times show a dialog to go to settings and allow permission.
//TODO - Currently we check user login with local pref not with firebase auth need to keep in sync with firebase auth
//TODO - Before showing the list in downloaded treks make sure trek is fully downloaded if not remove the trek meta data
//TODO - On search image of trek is not showing


class MainActivity : ComponentActivity() {

    private lateinit var permissionLauncher: PermissionHandler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        permissionLauncher = PermissionHandler(this)
        var keepSplashScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        setContent {
            MyApplicationTheme {
                ViaHimalayaApp(
                    permissionHandler = permissionLauncher,
                    onAuthCheckComplete = { keepSplashScreen = false }
                )
            }
        }
    }
}

@Composable
fun ViaHimalayaApp(
    permissionHandler: PermissionHandler,
    onAuthCheckComplete: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val authViewModel = koinViewModel<AuthViewModel>()
    val authState by authViewModel.state.collectAsState()
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
    if (!authState.initialAuthCheckRunning) {
        onAuthCheckComplete()
        val isInternetAvailable = NetworkUtil.isInternetAvailable(context)
        val isLoggedIn = authState.userProfile?.email != null
        
        val startDestination = when {
            !isLoggedIn -> Route.SignIn
            isInternetAvailable -> Route.Explore
            else -> Route.Profile // Offline and logged in -> go to Profile
        }
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
                    startDestination = startDestination
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
                        DownloadedTrekScreenRoot(
                            viewModel = viewModel,
                            onTrekClicked = { trek ->
                                navController.navigate(
                                    Route.TrekDetail(trek.id, trek.coordinateUrl)
                                )
                            },
                            onDeleteTrek = { trek ->
                                viewModel.deleteDownloadedTrek(trek)
                            }
                        )
                    }
                }
            }
        }
    }
}
