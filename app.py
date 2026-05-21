from fastapi import FastAPI, BackgroundTasks
from pydantic import BaseModel
from typing import Optional, List
import uvicorn
import pandas as pd
import threading

from model_utils import FraudScorer

app = FastAPI(title="Fraud Detection API")
scorer = FraudScorer(model_path="fraud_model.joblib")

_retrain_lock = threading.Lock()


class TxnIn(BaseModel):
    transaction_id:    str
    user_id:           str
    device_id:         Optional[str]   = None
    merchant_id:       str
    amount:            float
    currency:          Optional[str]   = "INR"
    timestamp:         str
    geo_lat:           Optional[float] = None
    geo_lon:           Optional[float] = None
    user_avg_amount:   Optional[float] = None
    merchant_freq:     Optional[int]   = None
    txn_count_last_1h: Optional[int]   = 0


class RetrainRecord(BaseModel):
    """Mirrors TransactionStore.LocalTxn on the Android side."""
    userId:      str
    merchantId:  str
    amount:      float
    timestampMs: int
    decision:    str  
    fraudScore:  float


@app.post("/score")
def score_txn(txn: TxnIn):
    tx = txn.dict()
    result = scorer.score(tx)
    return {
        "transaction_id": tx["transaction_id"],
        "fraud_score":    result["fraud_score"],
        "decision":       result["decision"],
        "ml_score":       result["ml_score"],
        "rule_flags":     result["rule_flags"],
        "top_reasons":    result["top_reasons"],
        "is_cold_start":  result["is_cold_start"],
        "model_version":  "mvp_v1",
    }


@app.post("/retrain")
def retrain(records: List[RetrainRecord], background_tasks: BackgroundTasks):
    """
    App uploads locally-labelled transactions after each session.
    The backend appends them to the training set and retrains in the background,
    then hot-reloads the model so the live API immediately uses the new version.
    This is the dynamic learning loop that improves the model over real usage.
    """
    if not records:
        return {"status": "no_data"}
    background_tasks.add_task(_run_retrain, records)
    return {"status": "queued", "records_received": len(records)}


def _run_retrain(records: List[RetrainRecord]):
    if not _retrain_lock.acquire(blocking=False):
        print("[retrain] Already running — skipping duplicate trigger")
        return
    try:
        import os, datetime
        from train_model import train

        csv_path = "transactions_sample.csv"
        rows = []
        for r in records:
            ts = datetime.datetime.fromtimestamp(r.timestampMs / 1000).isoformat()
            rows.append({
                "transaction_id":    f"app_{r.timestampMs}_{r.userId[:8]}",
                "user_id":           r.userId,
                "device_id":         None,
                "merchant_id":       r.merchantId,
                "amount":            r.amount,
                "currency":          "INR",
                "timestamp":         ts,
                "geo_lat":           28.7,
                "geo_lon":           77.1,
                "is_fraud":          1 if r.decision == "block" else 0,
                "txn_count_last_1h": 0,
            })

        new_df = pd.DataFrame(rows)

        if os.path.exists(csv_path):
            existing = pd.concat([pd.read_csv(csv_path), new_df], ignore_index=True)
        else:
            existing = new_df

        existing.to_csv(csv_path, index=False)
        print(f"[retrain] Appended {len(rows)} rows → total dataset size: {len(existing)}")

        train(path=csv_path, model_out="fraud_model.joblib")

        scorer.reload()
        print("[retrain] Model hot-reloaded successfully")

    except Exception as e:
        print(f"[retrain] Failed: {e}")
    finally:
        _retrain_lock.release()


@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": scorer.model is not None}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
