package com.example.fraudapp.model

import com.google.gson.annotations.SerializedName

data class ScoreResponse(
    @SerializedName("transaction_id")        val transactionId: String,
    @SerializedName("fraud_score")           val fraudScore: Double,
    @SerializedName("decision")              val decision: String,
    @SerializedName("ml_score")              val mlScore: Double? = null,
    @SerializedName("rule_flags")            val ruleFlags: List<String>? = null,
    @SerializedName("model_version")         val modelVersion: String? = null,
    @SerializedName("confidence")            val confidence: Double? = null,
    @SerializedName("feature_contributions") val featureContributions: Map<String, Double>? = null,
    @SerializedName("review_reason")         val reviewReason: String? = null,
    @SerializedName("top_reasons")           val topReasons: List<String>? = null,
    @SerializedName("is_cold_start")         val isColdStart: Boolean? = null,
)

data class TransactionHistoryEntry(
    val transactionId: String,
    val merchantId:    String,
    val amount:        Double,
    val fraudScore:    Double,
    val decision:      String,
    val timestamp:     Long,
    val isColdStart:   Boolean = false,
    val localTxnCount: Int = 0,
)
