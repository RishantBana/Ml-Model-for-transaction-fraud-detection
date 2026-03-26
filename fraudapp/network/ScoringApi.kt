package com.example.fraudapp.network

import com.example.fraudapp.model.ScoreRequest
import com.example.fraudapp.model.ScoreResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ScoringApi {
    @POST("score")
    suspend fun scoreTransaction(@Body req: ScoreRequest): Response<ScoreResponse>
}