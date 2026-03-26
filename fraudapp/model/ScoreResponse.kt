package com.example.fraudapp.data

import com.example.fraudapp.model.ScoreRequest
import com.example.fraudapp.model.ScoreResponse
import com.example.fraudapp.network.ApiModule
import retrofit2.Response

class ScoringRepository {
    private val api = ApiModule.scoringApi

    suspend fun scoreTransaction(req: ScoreRequest): Response<ScoreResponse> {
        return api.scoreTransaction(req)
    }
}