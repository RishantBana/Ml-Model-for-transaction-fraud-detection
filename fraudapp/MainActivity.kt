package com.example.fraudapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.fraudapp.ui.TransactionScreen
import com.example.fraudapp.ui.theme.FraudAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FraudAppTheme {
                TransactionScreen()
            }
        }
    }
}
