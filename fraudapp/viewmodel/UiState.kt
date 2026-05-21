package com.example.fraudapp.viewmodel

import com.example.fraudapp.model.ScoreResponse

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(val response: ScoreResponse) : UiState
    data class Verify(val response: ScoreResponse) : UiState
    data class Error(val message: String, val response: ScoreResponse? = null) : UiState
}
