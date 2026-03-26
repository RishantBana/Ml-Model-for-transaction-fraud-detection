# model_utils.py
import joblib
import numpy as np
from datetime import datetime
import math
import os

# If xgboost is not available at import time, we'll import inside methods.
# Expected model file: fraud_model.joblib created by train_model.py

DEFAULT_BASE_LAT = 28.7
DEFAULT_BASE_LON = 77.1

class FraudScorer:
    def __init__(self, model_path="fraud_model.joblib"):
        self.model_path = model_path
        self.model = None
        self.features = None
        # try to load model if exists
        if os.path.exists(self.model_path):
            try:
                obj = joblib.load(self.model_path)
                # obj expected to be dict with keys "model" and "features"
                self.model = obj.get("model", None)
                self.features = obj.get("features", None)
                print(f"[FraudScorer] Loaded model from {self.model_path}; features={self.features}")
            except Exception as e:
                print(f"[FraudScorer] Failed to load model: {e}")
        else:
            print(f"[FraudScorer] Model file not found at {self.model_path}. ML scoring will be disabled.")

    def _featurize_one(self, txn):
        """
        Produce a numpy row matching the trained features order.
        This must match the feature engineering used during training.
        """
        # safe defaults
        amount = float(txn.get("amount", 0.0) or 0.0)
        ts_str = txn.get("timestamp")
        try:
            ts = datetime.fromisoformat(ts_str) if ts_str else datetime.utcnow()
            hour = ts.hour
        except Exception:
            hour = 0
        log_amount = math.log1p(amount)
        user_avg = float(txn.get("user_avg_amount", amount) or amount)
        amount_over_user_avg = amount / (user_avg + 1e-9)
        lat = float(txn.get("geo_lat", DEFAULT_BASE_LAT) or DEFAULT_BASE_LAT)
        lon = float(txn.get("geo_lon", DEFAULT_BASE_LON) or DEFAULT_BASE_LON)
        # simple euclidean distance approx (not haversine) — matches training featurize
        dist = ((lat - DEFAULT_BASE_LAT) ** 2 + (lon - DEFAULT_BASE_LON) ** 2) ** 0.5
        merchant_freq = float(txn.get("merchant_freq", 1) or 1)

        row = [log_amount, hour, amount_over_user_avg, dist, merchant_freq]
        return np.array(row).reshape(1, -1)

    def _ml_predict(self, X):
        """Return ml_score in [0,1]. If model missing or error, returns 0.0"""
        if self.model is None:
            return 0.0
        try:
            # lazy import to avoid import errors when xgboost not installed
            import xgboost as xgb  # noqa: F401
            try:
                # if model is xgboost Booster saved inside joblib
                if hasattr(self.model, "predict"):
                    # For scikit-like wrapper or sklearn API
                    # Some xgb models accept DMatrix or 2D array
                    try:
                        # prefer predict_proba if available
                        if hasattr(self.model, "predict_proba"):
                            prob = self.model.predict_proba(X)[:, 1]
                            return float(prob[0])
                        else:
                            # For native xgboost Booster saved in joblib, use DMatrix
                            dmat = xgb.DMatrix(X)
                            pred = self.model.predict(dmat)
                            return float(pred[0])
                    except Exception:
                        # fallback: try model.predict on numpy array
                        pred = self.model.predict(X)
                        # if prediction is probability-like or raw score
                        val = float(pred[0])
                        # clip to [0,1]
                        return max(0.0, min(1.0, val))
                else:
                    print("[FraudScorer] model loaded but has no predict attribute.")
                    return 0.0
            except Exception as e:
                print(f"[FraudScorer] ML prediction error: {e}")
                return 0.0
        except Exception:
            # xgboost not installed
            try:
                pred = self.model.predict(X)
                val = float(pred[0])
                return max(0.0, min(1.0, val))
            except Exception as e:
                print(f"[FraudScorer] ML fallback prediction error: {e}")
                return 0.0

    def score(self, txn):
        """
        txn : dict with keys like amount, timestamp, user_avg_amount, geo_lat, geo_lon, merchant_freq
        returns dict with fraud_score, decision, ml_score, rule_flags, top_reasons
        """
        # ---------- DEBUG: show incoming txn ----------
        print("[FraudScorer] Received txn:", txn)

        # normalize numeric fields
        try:
            amt = float(txn.get("amount", 0.0) or 0.0)
        except Exception:
            amt = 0.0
        try:
            user_avg = float(txn.get("user_avg_amount", amt) or amt)
        except Exception:
            user_avg = amt
        lat = float(txn.get("geo_lat", DEFAULT_BASE_LAT) or DEFAULT_BASE_LAT)
        lon = float(txn.get("geo_lon", DEFAULT_BASE_LON) or DEFAULT_BASE_LON)
        merchant_freq = float(txn.get("merchant_freq", 1) or 1)

        # ---------- HARD RULES (force-block) ----------
        # Tune these thresholds as needed for demo/production
        if amt > 50000:
            print("[FraudScorer] Forced block: extreme amount", amt)
            return {
                "fraud_score": 0.99,
                "decision": "block",
                "ml_score": None,
                "rule_flags": ["rule_extreme_amount"],
                "top_reasons": ["Extreme transaction amount"]
            }

        # very large geo jump
        if abs(lat - DEFAULT_BASE_LAT) > 5 or abs(lon - DEFAULT_BASE_LON) > 5:
            # only automatic block if also significant amount else mark verify
            if amt > 5000:
                print("[FraudScorer] Forced block: geo jump with high amount", lat, lon, amt)
                return {
                    "fraud_score": 0.95,
                    "decision": "block",
                    "ml_score": None,
                    "rule_flags": ["rule_geo_jump"],
                    "top_reasons": ["Large geographic distance from user base"]
                }
            else:
                # mark as rule but do not force block
                pass

        if amt > user_avg * 10:
            print("[FraudScorer] Forced block: amount >> user_avg", amt, user_avg)
            return {
                "fraud_score": 0.93,
                "decision": "block",
                "ml_score": None,
                "rule_flags": ["rule_amount_vs_user_avg"],
                "top_reasons": ["Amount far exceeds user's average"]
            }

        # ---------- Soft rule engine (collect rule flags) ----------
        rules = []
        # rule: high relative amount
        if amt > user_avg * 5 + 1000:
            rules.append("amount_vs_user_avg")

        # rule: odd hour (1-5 AM)
        try:
            hour = datetime.fromisoformat(txn.get("timestamp")).hour
        except Exception:
            hour = None
        if hour is not None and hour in [0,1,2,3,4,5]:
            rules.append("odd_hour")

        # rule: rapid small txns / velocity (if a velocity field exists)
        if float(txn.get("txn_count_last_1h", 0)) >= 5:
            rules.append("high_velocity")

        # rule: geo jump (soft)
        if abs(lat - DEFAULT_BASE_LAT) > 3 or abs(lon - DEFAULT_BASE_LON) > 3:
            rules.append("geo_jump")

        # rule: low merchant frequency (rare merchant)
        if merchant_freq <= 1:
            rules.append("rare_merchant")

        # ---------- ML scoring ----------
        ml_score = 0.0
        try:
            X = self._featurize_one(txn)
            ml_score = self._ml_predict(X)
        except Exception as e:
            print("[FraudScorer] Error featurizing/predicting:", e)
            ml_score = 0.0

        # ---------- Combine scores ----------
        # Convert rules list into a rule_weight between 0..1
        # more flags -> higher weight
        rule_weight = min(1.0, 0.4 * len(rules))  # each rule counts 0.4 up to 1.0
        # Combine with a bias toward rules (conservative)
        fraud_score = 0.45 * ml_score + 0.55 * rule_weight
        fraud_score = max(0.0, min(1.0, fraud_score))

        # ---------- Decision thresholds ----------
        if fraud_score < 0.3:
            decision = "allow"
        elif fraud_score < 0.7:
            decision = "verify"
        else:
            decision = "block"

        # ---------- Build reasons (plain language) ----------
        reasons = []
        for r in rules:
            if r == "amount_vs_user_avg":
                reasons.append("Amount unusually high for this user")
            elif r == "odd_hour":
                reasons.append("Transaction during unusual hour")
            elif r == "geo_jump":
                reasons.append("Transaction from distant location")
            elif r == "high_velocity":
                reasons.append("Many transactions in short time")
            elif r == "rare_merchant":
                reasons.append("Merchant rarely used by this user")

        # If ml_score is high add ML reason
        if ml_score > 0.7:
            reasons.insert(0, "ML model strongly indicates fraud")

        result = {
            "fraud_score": round(fraud_score, 4),
            "decision": decision,
            "ml_score": round(ml_score, 4) if ml_score is not None else None,
            "rule_flags": rules,
            "top_reasons": reasons
        }

        # debug output
        print("[FraudScorer] ml_score=", ml_score, "rule_flags=", rules, "combined_score=", fraud_score, "decision=", decision)
        return result


# quick local test if run as script
if __name__ == "__main__":
    s = FraudScorer()
    sample = {
        "transaction_id": "test1",
        "user_id": "user_demo",
        "amount": 95000,
        "timestamp": datetime.utcnow().isoformat(),
        "geo_lat": 51.5074,
        "geo_lon": -0.1278,
        "user_avg_amount": 200,
        "merchant_freq": 1,
        "txn_count_last_1h": 0
    }
    print(s.score(sample))
