package com.example.fraudapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fraudapp.data.ScoringRepository
import com.example.fraudapp.data.TransactionStore
import com.example.fraudapp.model.ScoreRequest
import com.example.fraudapp.model.ScoreResponse
import com.example.fraudapp.model.TransactionHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository       = ScoringRepository()
    private val transactionStore = TransactionStore(app)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<TransactionHistoryEntry>>(emptyList())
    val history: StateFlow<List<TransactionHistoryEntry>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            _history.value = transactionStore.getRecentHistory()
        }
    }


    fun doTransaction(userId: String, merchantId: String, amount: Double) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val userAvg       = transactionStore.userAvgAmount(userId)
            val velocity      = transactionStore.txnCountLastHour(userId)
            val localTxnCount = transactionStore.txnCount(userId)

            try {
                val txnId = UUID.randomUUID().toString()
                val req = ScoreRequest(
                    transactionId  = txnId,
                    userId         = userId,
                    merchantId     = merchantId,
                    amount         = amount,
                    timestamp      = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                    userAvgAmount  = userAvg,
                    txnCountLast1h = velocity,
                )

                val response = repository.scoreTransaction(req)

                if (response.isSuccessful) {
                    val body = response.body()!!

                    transactionStore.record(
                        TransactionStore.LocalTxn(
                            transactionId = txnId,
                            userId        = userId,
                            merchantId    = merchantId,
                            amount        = amount,
                            timestampMs   = System.currentTimeMillis(),
                            decision      = body.decision,
                            fraudScore    = body.fraudScore,
                            isColdStart   = body.isColdStart ?: false,
                        )
                    )

                    val totalTxns = transactionStore.txnCount(userId)
                    if (totalTxns % 5 == 0) uploadForRetraining()

                    addToHistory(body, txnId, amount, merchantId, localTxnCount)

                    _uiState.value = when (body.decision.lowercase()) {
                        "allow"          -> UiState.Success(body)
                        "verify"         -> UiState.Verify(body)
                        "block"          -> UiState.Error("Transaction blocked", body)
                        "pending_review" -> UiState.Error("Flagged for manual review", body)
                        else             -> UiState.Error("Unknown decision: ${body.decision}", body)
                    }
                } else {
                    _uiState.value =
                        UiState.Error("Server error ${response.code()}: ${response.message()}")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Unexpected error")
            }
        }
    }

    fun uploadForRetraining() {
        viewModelScope.launch {
            try {
                val data = transactionStore.exportNewForRetraining()
                if (data.isEmpty()) return@launch
                repository.uploadRetrainingData(data)
                transactionStore.markUploaded()
            } catch (_: Exception) { }
        }
    }

    fun completeVerification(response: ScoreResponse) {
        _history.value = _history.value.map { entry ->
            if (entry.transactionId == response.transactionId)
                entry.copy(decision = "verified")
            else entry
        }
        _uiState.value = UiState.Success(response.copy(decision = "verified"))
    }

    fun reset() { _uiState.value = UiState.Idle }

    private fun addToHistory(
        response: ScoreResponse,
        txnId: String,
        amount: Double,
        merchantId: String,
        localTxnCount: Int,
    ) {
        val entry = TransactionHistoryEntry(
            transactionId = txnId,
            merchantId    = merchantId,
            amount        = amount,
            fraudScore    = response.fraudScore,
            decision      = response.decision,
            timestamp     = System.currentTimeMillis(),
            isColdStart   = response.isColdStart ?: false,
            localTxnCount = localTxnCount,
        )
        _history.value = (listOf(entry) + _history.value).take(10)
    }
}
