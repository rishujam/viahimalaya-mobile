package com.via.himalaya.presentation.navigator

sealed class NavigatorScreenUIEffect {

    data class ShowToast(val message: String) : NavigatorScreenUIEffect()

}
