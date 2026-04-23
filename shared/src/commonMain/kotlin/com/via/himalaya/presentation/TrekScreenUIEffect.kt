package com.via.himalaya.presentation

sealed class TrekScreenUIEffect {

    data class ShowToast(val message: String) : TrekScreenUIEffect()

}
