# train_model.py
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.metrics import roc_auc_score, average_precision_score, classification_report
import xgboost as xgb
import joblib
import os

def featurize(df):
    # basic features: log_amount, hour, user_rolling_avg (simple)
    df['timestamp'] = pd.to_datetime(df['timestamp'])
    df['hour'] = df['timestamp'].dt.hour
    df['log_amount'] = np.log1p(df['amount'])
    # user avg amount over all their history (simple static)
    df['user_avg_amount'] = df.groupby('user_id')['amount'].transform('mean')
    df['amount_over_user_avg'] = df['amount'] / (df['user_avg_amount'] + 1e-9)
    # simplistic feature: distance from base (approx)
    base_lat, base_lon = 28.7, 77.1
    df['dist_from_home'] = ((df['geo_lat']-base_lat)**2 + (df['geo_lon']-base_lon)**2)**0.5
    # one-hot-ish merchant: frequency encoding
    merchant_freq = df['merchant_id'].value_counts().to_dict()
    df['merchant_freq'] = df['merchant_id'].map(merchant_freq)
    return df

def train(path="transactions_sample.csv", model_out="fraud_model.joblib"):
    df = pd.read_csv(path)
    df = featurize(df)
    feature_cols = ['log_amount','hour','amount_over_user_avg','dist_from_home','merchant_freq']
    X = df[feature_cols].fillna(0)
    y = df['is_fraud']
    X_train, X_test, y_train, y_test = train_test_split(X, y, stratify=y, test_size=0.2, random_state=42)
    scale_pos = max(1, (len(y_train)-y_train.sum()) / (y_train.sum()+1e-9))
    dtrain = xgb.DMatrix(X_train, label=y_train)
    dtest = xgb.DMatrix(X_test, label=y_test)
    params = {
        'objective':'binary:logistic',
        'eval_metric':'auc',
        'eta':0.1,
        'max_depth':6,
        'scale_pos_weight': scale_pos,
        'seed':42,
        'verbosity':0
    }
    bst = xgb.train(params, dtrain, num_boost_round=200, evals=[(dtrain,'train')], early_stopping_rounds=20, verbose_eval=False)
    # Save model and columns
    joblib.dump({"model":bst, "features":feature_cols}, model_out)
    print("Saved model to", model_out)
    # Evaluate
    y_prob = bst.predict(dtest)
    print("AUC:", roc_auc_score(y_test, y_prob))
    print("AvgPrecision:", average_precision_score(y_test, y_prob))
    y_pred = (y_prob > 0.5).astype(int)
    print(classification_report(y_test, y_pred))

if __name__ == "__main__":
    train()
