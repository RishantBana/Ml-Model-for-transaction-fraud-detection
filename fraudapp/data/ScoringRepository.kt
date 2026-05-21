package com.example.fraudapp.data

import com.example.fraudapp.model.ScoreRequest
import com.example.fraudapp.model.ScoreResponse
import com.example.fraudapp.network.ApiModule
import retrofit2.Response

class ScoringRepository {
    private val api = ApiModule.scoringApi

    suspend fun scoreTransaction(req: ScoreRequest): Response<ScoreResponse> =
        api.scoreTransaction(req)

    suspend fun uploadRetrainingData(data: List<TransactionStore.LocalTxn>) {
        try {
            api.uploadRetrainingData(data)
        } catch (_: Exception) { /* best-effort */ }
    }
}
