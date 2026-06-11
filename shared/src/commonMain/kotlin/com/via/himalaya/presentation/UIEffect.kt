package com.via.himalaya.presentation

sealed class UIEffect {
    data class ShowToast(val message: String) : UIEffect()
}
