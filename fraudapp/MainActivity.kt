package com.example.fraudapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.example.fraudapp.ui.HomeScreen
import com.example.fraudapp.ui.TransactionScreen
import com.example.fraudapp.ui.theme.FraudAppTheme
import com.example.fraudapp.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FraudAppTheme {
                var screen by remember { mutableStateOf(AppScreen.HOME) }
                when (screen) {
                    AppScreen.HOME        -> HomeScreen(
                        vm        = vm,
                        onNavigate = { screen = AppScreen.TRANSACTION }
                    )
                    AppScreen.TRANSACTION -> TransactionScreen(
                        vm      = vm,
                        onBack  = { screen = AppScreen.HOME }
                    )
                }
            }
        }
    }
}
