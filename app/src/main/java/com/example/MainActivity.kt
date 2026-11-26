package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ExplorerScreen
import com.example.ui.theme.TabbedFileExplorerTheme
import com.example.viewmodel.ExplorerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val explorerViewModel: ExplorerViewModel = viewModel()
            val prefs by explorerViewModel.preferences.collectAsStateWithLifecycle()

            TabbedFileExplorerTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor
            ) {
                ExplorerScreen(viewModel = explorerViewModel)
            }
        }
    }
}
