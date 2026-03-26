package com.example.fraudapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fraudapp.R // Assuming you have string resources
import com.example.fraudapp.model.ScoreResponse

// MainViewModel and UiState would be defined elsewhere in your project
// class MainViewModel : ViewModel() { ... }
// sealed interface UiState { ... }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(vm: MainViewModel = viewModel()) {
    // Observe the UI state from the ViewModel
    val uiState by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Fraud Detection Demo", style = MaterialTheme.typography.titleLarge) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            // The form is only shown in the Idle state
            if (uiState is UiState.Idle) {
                TransactionForm(onSubmit = vm::submitTransaction)
            }

            // Handle the different UI states
            when (val s = uiState) {
                is UiState.Loading -> {
                    CircularProgressIndicator()
                }
                is UiState.Success -> {
                    ResultCard(
                        title = "Allowed",
                        subtitle = "Transaction successful",
                        response = s.response,
                        onReset = vm::reset
                    )
                }
                is UiState.Verify -> {
                    OTPVerificationDialog(
                        response = s.response,
                        onVerified = vm::completeVerification,
                        onCancel = vm::reset
                    )
                }
                is UiState.Error -> {
                    ResultCard(
                        title = "Blocked / Error",
                        subtitle = s.message,
                        response = null,
                        onReset = vm::reset
                    )
                }
                is UiState.Idle -> { /* The form is already shown */ }
            }
        }
    }
}

@Composable
private fun TransactionForm(
    onSubmit: (userId: String, merchantId: String, amount: Double, lat: Double?, lon: Double?, userAvg: Double?) -> Unit
) {
    // Form state is now encapsulated within this composable
    var userId by remember { mutableStateOf("user_demo_1") }
    var merchantId by remember { mutableStateOf("m_1") }
    var amountText by remember { mutableStateOf("500.0") }
    var latText by remember { mutableStateOf("28.7") }
    var lonText by remember { mutableStateOf("77.1") }
    var userAvgText by remember { mutableStateOf("200.0") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()) // Make form scrollable
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Simulated Payment", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        // Reusable composable for text fields to reduce boilerplate
        FormTextField(
            value = userId,
            onValueChange = { userId = it },
            label = "User ID"
        )
        FormTextField(
            value = merchantId,
            onValueChange = { merchantId = it },
            label = "Merchant ID"
        )
        FormTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = "Amount",
            isNumeric = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormTextField(
                value = latText,
                onValueChange = { latText = it },
                label = "Lat",
                modifier = Modifier.weight(1f),
                isNumeric = true
            )
            FormTextField(
                value = lonText,
                onValueChange = { lonText = it },
                label = "Lon",
                modifier = Modifier.weight(1f),
                isNumeric = true
            )
        }

        FormTextField(
            value = userAvgText,
            onValueChange = { userAvgText = it },
            label = "User Avg (optional)",
            isNumeric = true
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val amt = amountText.toDoubleOrNull() ?: 0.0
                val lat = latText.toDoubleOrNull()
                val lon = lonText.toDoubleOrNull()
                val ua = userAvgText.toDoubleOrNull()
                onSubmit(userId, merchantId, amt, lat, lon, ua)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pay")
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isNumeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = if (isNumeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp) // Consistent spacing
    )
}

// ResultCard and OTPVerificationDialog remain largely the same, but with minor improvements.

@Composable
private fun ResultCard(title: String, subtitle: String, response: ScoreResponse?, onReset: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(), // Ensure button takes full width
            horizontalAlignment = Alignment.CenterHorizontally // Center the button
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(subtitle)
            response?.let {
                Spacer(Modifier.height(8.dp))
                Text("Fraud score: ${it.fraudScore}")
                Text("Decision: ${it.decision}")
                Text("Model: ${it.modelVersion ?: "n/a"}")
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onReset, modifier = Modifier.fillMaxWidth(0.8f)) {
                Text("New Transaction")
            }
        }
    }
}

@Composable
private fun OTPVerificationDialog(response: ScoreResponse, onVerified: (ScoreResponse) -> Unit, onCancel: () -> Unit) {
    var otp by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("An OTP has been sent to your registered phone (simulated).") }

    AlertDialog(
        onDismissRequest = { /* block dismiss for demo */ },
        confirmButton = {
            TextButton(onClick = {
                if (otp.isNotEmpty()) {
                    onVerified(response)
                } else {
                    info = "Enter a valid OTP to continue (for demo, any digits)."
                }
            }) { Text("Verify") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
        title = { Text("Verify Transaction") },
        text = {
            Column {
                Text(info)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it },
                    label = { Text("Enter OTP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
