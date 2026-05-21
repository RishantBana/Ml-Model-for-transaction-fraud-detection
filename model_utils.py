import joblib
import numpy as np
from datetime import datetime
import math
import os

DEFAULT_BASE_LAT = 28.7
DEFAULT_BASE_LON = 77.1
COLD_START_THRESHOLD = 3


class FraudScorer:
    def __init__(self, model_path="fraud_model.joblib"):
        self.model_path      = model_path
        self.model           = None
        self.features        = None
        self.threshold       = 0.5
        self.user_avg_lookup = {}
        self.merchant_freq   = {}
        self.user_txn_count  = {}

        if os.path.exists(self.model_path):
            self._load()
        else:
            print(f"[FraudScorer] Model not found at {self.model_path}. ML disabled.")

    def _load(self):
        try:
            obj = joblib.load(self.model_path)
            self.model           = obj.get("model", None)
            self.features        = obj.get("features", None)
            self.threshold       = obj.get("threshold", 0.5)
            self.user_avg_lookup = obj.get("user_avg_lookup", {})
            self.merchant_freq   = (
                obj.get("merchant_freq_lookup") or obj.get("merchant_freq", {})
            )
            self.user_txn_count  = obj.get("user_txn_count", {})
            print(
                f"[FraudScorer] Loaded | features={self.features} | "
                f"threshold={self.threshold:.4f} | "
                f"users={len(self.user_avg_lookup)} | "
                f"merchants={len(self.merchant_freq)}"
            )
        except Exception as e:
            print(f"[FraudScorer] Load failed: {e}")

    def reload(self):
        print("[FraudScorer] Reloading model from disk…")
        self._load()

    def _is_cold_start(self, txn: dict) -> bool:
        """
        PRIMARY signal: if the app sent a non-null user_avg_amount, it means
        TransactionStore has >= COLD_START_THRESHOLD local records for this user
        → definitely NOT cold start, regardless of whether they're in training data.

        This fixes users like "abc" / "def" who are real users but not in the
        synthetic training CSV (which only has random UUIDs).

        FALLBACK: check the training-data txn count for users who were in training.
        """
        if txn.get("user_avg_amount") is not None:
            return False
        user_id = txn.get("user_id", "")
        return self.user_txn_count.get(user_id, 0) < COLD_START_THRESHOLD

    def _cold_start_adj(self, txn: dict) -> float:
        if not self._is_cold_start(txn):
            return 0.0
        user_id = txn.get("user_id", "")
        count = self.user_txn_count.get(user_id, 0)
        return 0.15 if count == 0 else 0.07

    def _get_user_avg(self, txn):
        if txn.get("user_avg_amount") is not None:
            return float(txn["user_avg_amount"])
        uid = txn.get("user_id")
        if uid and uid in self.user_avg_lookup:
            return float(self.user_avg_lookup[uid])
        return float(txn.get("amount", 0.0) or 0.0)

    def _get_merchant_freq(self, txn):
        if txn.get("merchant_freq") is not None:
            return float(txn["merchant_freq"])
        mid = txn.get("merchant_id")
        if mid and mid in self.merchant_freq:
            return float(self.merchant_freq[mid])
        return None

    def _featurize_one(self, txn):
        amount = float(txn.get("amount", 0.0) or 0.0)
        try:
            ts_str = txn.get("timestamp")
            hour = datetime.fromisoformat(ts_str).hour if ts_str else datetime.utcnow().hour
        except Exception:
            hour = 0

        log_amount           = math.log1p(amount)
        user_avg             = self._get_user_avg(txn)
        amount_over_user_avg = amount / (user_avg + 1e-9)

        lat  = float(txn.get("geo_lat", DEFAULT_BASE_LAT) or DEFAULT_BASE_LAT)
        lon  = float(txn.get("geo_lon", DEFAULT_BASE_LON) or DEFAULT_BASE_LON)
        dist = ((lat - DEFAULT_BASE_LAT) ** 2 + (lon - DEFAULT_BASE_LON) ** 2) ** 0.5

        merchant_freq     = self._get_merchant_freq(txn) or 1.0
        txn_count_last_1h = float(txn.get("txn_count_last_1h", 0) or 0)

        return np.array([
            log_amount, hour, amount_over_user_avg,
            dist, merchant_freq, txn_count_last_1h
        ]).reshape(1, -1)


    def _ml_predict(self, X) -> float:
        if self.model is None:
            return 0.0
        try:
            import xgboost as xgb
            if hasattr(self.model, "predict_proba"):
                return float(self.model.predict_proba(X)[:, 1][0])
            dmat = xgb.DMatrix(X, feature_names=self.features)
            return float(self.model.predict(dmat)[0])
        except Exception as e:
            print(f"[FraudScorer] ML error: {e}")
            return 0.0


    def score(self, txn: dict) -> dict:
        try:
            amt = float(txn.get("amount", 0.0) or 0.0)
        except Exception:
            amt = 0.0

        user_avg      = self._get_user_avg(txn)
        merchant_freq = self._get_merchant_freq(txn)
        cold_start    = self._is_cold_start(txn)
        cold_adj      = self._cold_start_adj(txn)

        def hard_block(score, flags, reasons):
            return {
                "fraud_score": score, "decision": "block",
                "ml_score": None, "rule_flags": flags, "top_reasons": reasons,
                "is_cold_start": cold_start,
            }

        if amt > 50000:
            return hard_block(0.99, ["rule_extreme_amount"], ["Extreme transaction amount"])
        if amt > user_avg * 10 and user_avg > 0:
            return hard_block(0.93, ["rule_amount_vs_user_avg"], ["Amount far exceeds user's average"])

        rules = []
        if amt > user_avg * 5 + 1000 and user_avg > 0:
            rules.append("amount_vs_user_avg")
        try:
            hour = datetime.fromisoformat(txn.get("timestamp", "")).hour
            if hour in range(0, 6):
                rules.append("odd_hour")
        except Exception:
            pass
        if float(txn.get("txn_count_last_1h", 0)) >= 5:
            rules.append("high_velocity")
        if merchant_freq is not None and merchant_freq <= 1:
            rules.append("rare_merchant")
        if cold_start:
            rules.append("cold_start_user")

        ml_score = 0.0
        try:
            X = self._featurize_one(txn)
            ml_score = self._ml_predict(X)
        except Exception as e:
            print(f"[FraudScorer] Featurize error: {e}")

        rule_weight = min(1.0, 0.4 * len([r for r in rules if r != "cold_start_user"]))
        fraud_score = max(0.0, min(1.0, 0.6 * ml_score + 0.4 * rule_weight))

        BLOCK_THRESH  = 0.60
        VERIFY_THRESH = 0.30

        effective_block  = BLOCK_THRESH  - cold_adj
        effective_verify = VERIFY_THRESH - (cold_adj * 0.5)

        if fraud_score < effective_verify:
            decision = "allow"
        elif fraud_score < effective_block:
            decision = "verify"
        else:
            decision = "block"
        reason_map = {
            "amount_vs_user_avg": "Amount unusually high for this user",
            "odd_hour":           "Transaction at an unusual hour",
            "high_velocity":      "Many transactions in a short period",
            "rare_merchant":      "Merchant not commonly used by this user",
            "cold_start_user":    "New user — conservative thresholds applied",
        }
        reasons = [reason_map[r] for r in rules if r in reason_map]
        if ml_score > 0.7:
            reasons.insert(0, "ML model strongly indicates fraud")

        return {
            "fraud_score":   round(fraud_score, 4),
            "decision":      decision,
            "ml_score":      round(ml_score, 4),
            "rule_flags":    rules,
            "top_reasons":   reasons,
            "is_cold_start": cold_start,
        }