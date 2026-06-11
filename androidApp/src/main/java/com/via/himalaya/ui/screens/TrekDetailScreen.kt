package com.via.himalaya.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun TrekDetailScreen() {
    Column (modifier = Modifier.fillMaxSize().background(Color.Cyan), verticalArrangement = Arrangement.Bottom) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f).background(Color.Green)) {

        }
    }
}