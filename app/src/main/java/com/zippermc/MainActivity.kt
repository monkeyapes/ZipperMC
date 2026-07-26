package com.zippermc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zippermc.ui.screens.MainScreen
import com.zippermc.ui.theme.ZipperMCTheme
import com.zippermc.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZipperMCTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: MainViewModel = viewModel()

                    if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
                        viewModel.onZipPicked(intent.data!!)
                    }

                    MainScreen(viewModel)
                }
            }
        }
    }
}
