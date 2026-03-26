package com.example.fraudapp.ui

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fraudapp.data.ScoringRepository
import com.example.fraudapp.model.ScoreRequest
import com.example.fraudapp.model.ScoreResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.*

sealed class UiState {
    object Idle: UiState()
    object Loading: UiState()
    data class Success(val response: ScoreResponse): UiState()
    data class Error(val message: String): UiState()
    data class Verify(val response: ScoreResponse): UiState()
}

class MainViewModel(
    private val repo: ScoringRepository = ScoringRepository()
): ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    fun submitTransaction(
        userId: String,
        merchantId: String,
        amount: Double,
        geoLat: Double?,
        geoLon: Double?,
        userAvg: Double?
    ) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val txnId = UUID.randomUUID().toString()
                val req = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ScoreRequest(
                        transactionId = txnId,
                        userId = userId,
                        merchantId = merchantId,
                        amount = amount,
                        timestamp = Instant.now().toString(),
                        geoLat = geoLat,
                        geoLon = geoLon,
                        userAvgAmount = userAvg
                    )
                } else {
                    TODO("VERSION.SDK_INT < O")
                }
                val resp = repo.scoreTransaction(req)
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    when (body.decision.lowercase()) {
                        "allow" -> _uiState.value = UiState.Success(body)
                        "verify" -> _uiState.value = UiState.Verify(body)
                        "block" -> _uiState.value = UiState.Error("Transaction blocked")
                        else -> _uiState.value = UiState.Error("Unknown decision: ${body.decision}")
                    }
                } else {
                    val err = resp.errorBody()?.string() ?: resp.message()
                    _uiState.value = UiState.Error("API error: $err")
                }

            } catch (e: Exception) {
                _uiState.value = UiState.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    // Call this after OTP simulated verification: treat as success
    fun completeVerification(response: ScoreResponse) {
        _uiState.value = UiState.Success(response)
    }

    fun reset() { _uiState.value = UiState.Idle }
}
