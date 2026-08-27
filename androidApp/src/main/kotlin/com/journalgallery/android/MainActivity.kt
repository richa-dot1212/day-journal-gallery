package com.journalgallery.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.journalgallery.android.ui.DayJournalTheme
import com.journalgallery.android.ui.nav.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    DayJournalTheme {
        AppNavHost()
    }
}
