package com.via.himalaya.presentation.navigator

import com.via.himalaya.data.models.SensorData

sealed class NavigatorScreenUIEvent {

    data class StartNavigator(val name: String) : NavigatorScreenUIEvent()

    data object StopNavigator : NavigatorScreenUIEvent()

    data object PauseNavigator : NavigatorScreenUIEvent()

    data class LocationUpdate(val sensorData: SensorData) : NavigatorScreenUIEvent()

    data class NavigatorNameChanged(val name: String) : NavigatorScreenUIEvent()

}