# app.py
from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional
import uvicorn
from model_utils import FraudScorer

app = FastAPI(title="Fraud Scoring API (MVP)")
scorer = FraudScorer(model_path="fraud_model.joblib")

class TxnIn(BaseModel):
    transaction_id: str
    user_id: str
    device_id: Optional[str] = None
    merchant_id: str
    amount: float
    currency: Optional[str] = "INR"
    timestamp: str
    geo_lat: Optional[float] = None
    geo_lon: Optional[float] = None
    # optional fields helpful for demo:
    user_avg_amount: Optional[float] = None
    merchant_freq: Optional[int] = 1

@app.post("/score")
def score_txn(txn: TxnIn):
    tx = txn.dict()
    result = scorer.score(tx)
    return {
        "transaction_id": tx["transaction_id"],
        "fraud_score": result["fraud_score"],
        "decision": result["decision"],
        "ml_score": result["ml_score"],
        "rule_flags": result["rule_flags"],
        "model_version": "mvp_v1"
    }

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
