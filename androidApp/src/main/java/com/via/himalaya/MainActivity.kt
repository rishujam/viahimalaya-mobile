package com.via.himalaya

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.via.himalaya.domain.repo.AppConfigRepository
import com.via.himalaya.navigation.Route
import com.via.himalaya.navigation.bottomNavItems
import com.via.himalaya.presentation.auth.AuthViewModel
import com.via.himalaya.presentation.downloads.DownloadedTrekViewModel
import com.via.himalaya.presentation.explore.ExploreViewModel
import com.via.himalaya.presentation.trekDetail.TrekDetailViewModel
import com.via.himalaya.presentation.trekPlan.TrekPlanViewModel
import com.via.himalaya.ui.MyApplicationTheme
import com.via.himalaya.ui.components.BottomNavigationBar
import com.via.himalaya.ui.screens.AboutScreenRoot
import com.via.himalaya.ui.screens.DownloadedTrekScreenRoot
import com.via.himalaya.ui.screens.ExploreScreenRoot
import com.via.himalaya.ui.screens.ProfileScreenRoot
import com.via.himalaya.ui.screens.SignInScreenRoot
import com.via.himalaya.ui.screens.TrekDetailScreenRoot
import com.via.himalaya.ui.screens.TrekPlanScreenRoot
import com.via.himalaya.util.NetworkUtil
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

//Features
//TODO - Local Notifications while trekking to let them know how much left - P3
//TODO - Sun position using DEM - P3
//TODO - Campsite using DEM - P2
//TODO - Users can add POI - P4
//TODO - Filter treks based on no of days it will take
//TODO - A different version of Explore page which will show map of treks just like google map for treks - P3
//Features End

//TODO - Collect the sensor and location data of the trekker locally
        //Create table to store path followed by user mapped to trek id - Done need to test
//TODO - Add view on google maps for a POI (the destination)
//TODO - When notification permission denied 2 times navigate to settings
//TODO - When notification permission allowed start the download instead of toast
//TODO - Rate and comment on viewpoint - offline first sync it later when internet is there.
        //Create api for comment/rate POI
//TODO - Create test trek
//TODO - SignIn Page improved video branding
//TODO - Listen to location only when device is moving - optimize battery
//TODO - Font fix all over the app
//TODO - Profile Page
        //User Pref: Create datastore to store profile object (email, name, treks, distance)
//TODO - Before showing the list in downloaded treks make sure trek is fully downloaded if not remove the trek meta data
//TODO - Thumbnail is not downloaded when trek is downloaded
//TODO - AI Enabled search using local model if possible
//TODO - GeoFencing for sending notifications

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        var keepSplashScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        setContent {
            MyApplicationTheme {
                ViaHimalayaApp(
                    onAuthCheckComplete = { keepSplashScreen = false }
                )
            }
        }
    }
}

@Composable
fun ViaHimalayaApp(
    onAuthCheckComplete: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    // Remote config, fetched once per process.
    //
    // Keyed on Unit rather than on auth or connectivity: /api/app-config takes
    // no token, and this must not sit behind the sign-in gate or wait on the
    // auth check that gates the splash screen. It cannot fail loudly — the
    // repository swallows everything — so nothing downstream depends on it
    // finishing, or finishing before anything else.
    val appConfigRepository = koinInject<AppConfigRepository>()
    LaunchedEffect(Unit) {
        appConfigRepository.refresh()
    }

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
                            },
                            onAboutClicked = {
                                navController.navigate(Route.About)
                            }
                        )
                    }
                    composable<Route.About> {
                        AboutScreenRoot(onBack = { navController.popBackStack() })
                    }
                    composable<Route.TrekDetail> { entry ->
                        val viewModel = koinViewModel<TrekDetailViewModel>()
                        val args = entry.toRoute<Route.TrekDetail>()
                        TrekDetailScreenRoot(
                            viewModel,
                            args.trekId,
                            args.coordinateUrl,
                            onBackClick = { navController.navigateUp() },
                            onPlanClick = {
                                navController.navigate(
                                    Route.TrekPlan(args.trekId, args.coordinateUrl)
                                )
                            }
                        )
                    }
                    composable<Route.TrekPlan> { entry ->
                        // Its own ViewModel instance, scoped to this back stack
                        // entry - planning state should not leak back into the
                        // detail screen when the user returns.
                        val viewModel = koinViewModel<TrekDetailViewModel>()
                        val planViewModel = koinViewModel<TrekPlanViewModel>()
                        val args = entry.toRoute<Route.TrekPlan>()
                        TrekPlanScreenRoot(
                            viewModel = viewModel,
                            planViewModel = planViewModel,
                            trekId = args.trekId,
                            coordinateUrl = args.coordinateUrl,
                            onBackClick = { navController.navigateUp() }
                        )
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
