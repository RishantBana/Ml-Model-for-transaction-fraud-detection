# data_gen.py
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import uuid
import random

def random_geo(base=(28.7,77.1), spread=0.5):
    return base[0] + np.random.randn()*spread, base[1] + np.random.randn()*spread

def generate(n=5000, fraud_ratio=0.02, seed=42):
    np.random.seed(seed)
    rows = []
    base_time = datetime.now() - timedelta(days=30)
    user_ids = [str(uuid.uuid4()) for _ in range(300)]
    merchant_ids = [f"m_{i}" for i in range(50)]
    device_ids = [str(uuid.uuid4()) for _ in range(350)]

    for i in range(n):
        user = random.choice(user_ids)
        device = random.choice(device_ids)
        merchant = random.choice(merchant_ids)
        amount = round(max(1, np.random.exponential(80)),2)  # many small txns, some big
        ts = base_time + timedelta(seconds=int(np.random.rand()*30*24*3600))
        lat, lon = random_geo()
        is_fraud = np.random.rand() < fraud_ratio

        # Inject patterns for frauds: high amounts, new device, far geo, odd hour, rapid sequence
        if is_fraud:
            amount *= (5 + np.random.rand()*20)
            if np.random.rand() < 0.6:
                lat += (np.random.choice([-1,1]) * (5 + np.random.rand()*20))  # big geo jump
            if np.random.rand() < 0.5:
                ts = base_time + timedelta(seconds=int(np.random.rand()*86400))  # odd hours
        rows.append({
            "transaction_id": str(uuid.uuid4()),
            "user_id": user,
            "device_id": device,
            "merchant_id": merchant,
            "amount": amount,
            "currency": "INR",
            "timestamp": ts.isoformat(),
            "geo_lat": lat,
            "geo_lon": lon,
            "is_fraud": int(is_fraud)
        })
    df = pd.DataFrame(rows)
    df.to_csv("transactions_sample.csv", index=False)
    print("Saved transactions_sample.csv with", len(df), "rows")

if __name__ == "__main__":
    generate(6000, fraud_ratio=0.03)
