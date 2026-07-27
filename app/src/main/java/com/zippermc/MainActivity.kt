package com.zippermc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.zippermc.ui.screens.MainScreen
import com.zippermc.ui.theme.ZipperMCTheme
import com.zippermc.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            val darkTheme by viewModel.darkTheme.collectAsState()
            ZipperMCTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel, onToggleTheme = { recreate() })
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        try {
            val data = intent?.data
            if (intent?.action == Intent.ACTION_VIEW && data != null) {
                viewModel.onZipPicked(data)
            }
        } catch (_: Exception) {}
    }

    fun getViewModel(): MainViewModel = viewModel
}
