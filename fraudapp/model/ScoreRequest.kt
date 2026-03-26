package com.example.fraudapp.model

import com.google.gson.annotations.SerializedName

data class ScoreRequest(
    @SerializedName("transaction_id") val transactionId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("merchant_id") val merchantId: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("geo_lat") val geoLat: Double? = null,
    @SerializedName("geo_lon") val geoLon: Double? = null,
    // optional helpful demo fields:
    @SerializedName("user_avg_amount") val userAvgAmount: Double? = null,
    @SerializedName("merchant_freq") val merchantFreq: Int? = 1
)

data class ScoreResponse(
    @SerializedName("transaction_id") val transactionId: String,
    @SerializedName("fraud_score") val fraudScore: Double,
    @SerializedName("decision") val decision: String, // allow, verify, block, pending_review
    @SerializedName("ml_score") val mlScore: Double? = null,
    @SerializedName("rule_flags") val ruleFlags: List<String>? = null,
    @SerializedName("model_version") val modelVersion: String? = null
)

