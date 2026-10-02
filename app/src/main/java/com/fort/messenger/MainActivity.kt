package com.fort.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fort.messenger.ui.navigation.FortNavGraph
import com.fort.messenger.ui.theme.FortTheme
import com.fort.messenger.viewmodel.FortMainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: FortMainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FortTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FortNavGraph(viewModel = viewModel)
                }
            }
        }
    }
}
