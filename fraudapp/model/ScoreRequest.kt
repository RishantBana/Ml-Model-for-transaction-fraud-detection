package com.example.fraudapp.model

import com.google.gson.annotations.SerializedName

data class ScoreRequest(
    @SerializedName("transaction_id")    val transactionId: String,
    @SerializedName("user_id")           val userId: String,
    @SerializedName("device_id")         val deviceId: String? = null,
    @SerializedName("merchant_id")       val merchantId: String,
    @SerializedName("amount")            val amount: Double,
    @SerializedName("currency")          val currency: String = "INR",
    @SerializedName("timestamp")         val timestamp: String,
    @SerializedName("geo_lat")           val geoLat: Double? = null,
    @SerializedName("geo_lon")           val geoLon: Double? = null,
    @SerializedName("user_avg_amount")   val userAvgAmount: Double? = null,
    @SerializedName("merchant_freq")     val merchantFreq: Int? = null,
    @SerializedName("txn_count_last_1h") val txnCountLast1h: Int = 0,
)
