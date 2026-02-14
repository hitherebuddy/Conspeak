package com.conspeak.desktop

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.conspeak.desktop.ui.DesktopApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Conspeak - Phone as Microphone",
        state = WindowState(width = 500.dp, height = 700.dp)
    ) {
        DesktopApp()
    }
}
