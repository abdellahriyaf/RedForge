package com.redforge.app.ui.screens.onboarding

import androidx.compose.runtime.Composable

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    AnvilDropSplashScreen(onFinished = onFinished)
}
