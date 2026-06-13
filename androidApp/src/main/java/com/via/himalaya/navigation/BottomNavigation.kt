package com.via.himalaya.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Deprecated("Use BottomNavigationBar instead")
@Composable
fun BottomNavigation(
    currentRoute: Route,
    onNavigate: (Route) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Explore"
                )
            },
            label = { Text("Explore") },
            selected = currentRoute == Route.Explore,
            onClick = { onNavigate(Route.Explore) }
        )

        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile"
                )
            },
            label = { Text("Profile") },
            selected = currentRoute == Route.Profile,
            onClick = { onNavigate(Route.Profile) }
        )
    }
}