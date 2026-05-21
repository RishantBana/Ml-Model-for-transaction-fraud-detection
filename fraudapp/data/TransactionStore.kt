package com.example.fraudapp.data

import android.content.Context
import com.example.fraudapp.model.TransactionHistoryEntry
import com.example.fraudapp.ui.normaliseScore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class TransactionStore(context: Context) {

    companion object {
        private const val PREFS_NAME       = "txn_store"
        private const val KEY_RECORDS      = "txn_records"
        private const val KEY_UPLOAD_INDEX = "upload_index"
        private const val MAX_RECORDS      = 200
        private const val ONE_HOUR_MS      = 3_600_000L
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson  = Gson()



    data class LocalTxn(
        val transactionId: String = "",
        val userId:        String,
        val merchantId:    String,
        val amount:        Double,
        val timestampMs:   Long,
        val decision:      String,
        val fraudScore:    Double,
        val isColdStart:   Boolean = false,
    )



    private fun load(): MutableList<LocalTxn> {
        val json = prefs.getString(KEY_RECORDS, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<LocalTxn>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun save(records: List<LocalTxn>) {
        prefs.edit().putString(KEY_RECORDS, gson.toJson(records)).apply()
    }

    fun record(txn: LocalTxn) {
        val records = load()
        records.add(0, txn)
        save(records.take(MAX_RECORDS))
    }



    fun getRecentHistory(limit: Int = 10): List<TransactionHistoryEntry> =
        load().take(limit).map { txn ->
            TransactionHistoryEntry(
                transactionId = txn.transactionId,
                merchantId    = txn.merchantId,
                amount        = txn.amount,
                fraudScore    = txn.fraudScore,
                decision      = txn.decision,
                timestamp     = txn.timestampMs,
                isColdStart   = txn.isColdStart,
                localTxnCount = txnCount(txn.userId),
            )
        }



    fun userAvgAmount(userId: String): Double? {
        val txns = load().filter { it.userId == userId }
        if (txns.isEmpty()) return null
        return txns.map { it.amount }.average()
    }

    fun txnCountLastHour(userId: String): Int {
        val cutoff = System.currentTimeMillis() - ONE_HOUR_MS
        return load().count { it.userId == userId && it.timestampMs >= cutoff }
    }

    fun txnCount(userId: String): Int =
        load().count { it.userId == userId }



    fun exportNewForRetraining(): List<LocalTxn> {
        val all           = load()
        val uploadedCount = prefs.getInt(KEY_UPLOAD_INDEX, 0)

        val newCount = (all.size - uploadedCount).coerceAtLeast(0)
        if (newCount == 0) return emptyList()
        return all
            .take(newCount)
            .filter { it.fraudScore >= 0.3 || it.decision == "block" }
    }


    fun markUploaded() {
        val total = load().size
        prefs.edit().putInt(KEY_UPLOAD_INDEX, total).apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_RECORDS)
            .remove(KEY_UPLOAD_INDEX)
            .apply()
    }
}
