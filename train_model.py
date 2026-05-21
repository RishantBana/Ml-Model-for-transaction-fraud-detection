import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split, StratifiedKFold
from sklearn.metrics import (
    roc_auc_score, average_precision_score,
    classification_report, precision_recall_curve
)
import xgboost as xgb
import joblib


def featurize(df, user_avg_lookup=None, merchant_freq_lookup=None, is_train=False):
    df = df.copy()
    df['timestamp'] = pd.to_datetime(df['timestamp'])
    df['hour'] = df['timestamp'].dt.hour
    df['log_amount'] = np.log1p(df['amount'])

    if is_train:
        user_avg_lookup = df.groupby('user_id')['amount'].mean().to_dict()
    global_avg = np.mean(list(user_avg_lookup.values())) if user_avg_lookup else 0.0
    df['user_avg_amount'] = df['user_id'].map(user_avg_lookup).fillna(global_avg)
    df['amount_over_user_avg'] = df['amount'] / (df['user_avg_amount'] + 1e-9)

    base_lat, base_lon = 28.7, 77.1
    df['dist_from_home'] = (
        (df['geo_lat'] - base_lat) ** 2 + (df['geo_lon'] - base_lon) ** 2
    ) ** 0.5

    if is_train:
        merchant_freq_lookup = df['merchant_id'].value_counts().to_dict()
    global_freq = np.mean(list(merchant_freq_lookup.values())) if merchant_freq_lookup else 1.0
    df['merchant_freq'] = df['merchant_id'].map(merchant_freq_lookup).fillna(global_freq)

    if 'txn_count_last_1h' in df.columns:
        df['txn_count_last_1h'] = df['txn_count_last_1h'].fillna(0).astype(float)
    else:
        df['txn_count_last_1h'] = 0.0

    return df, user_avg_lookup, merchant_freq_lookup


def find_best_threshold_cv(y_true, y_prob, beta=1.0):
    precision, recall, thresholds = precision_recall_curve(y_true, y_prob)
    precision, recall = precision[:-1], recall[:-1]
    f_beta = (
        (1 + beta**2) * precision * recall
        / (beta**2 * precision + recall + 1e-9)
    )
    best_idx = np.argmax(f_beta)
    return float(thresholds[best_idx]), float(f_beta[best_idx])


def cross_validated_threshold(df, feature_cols, params, n_splits=5, beta=1.0):
    skf = StratifiedKFold(n_splits=n_splits, shuffle=True, random_state=42)
    X = df[feature_cols].fillna(0).values
    y = df['is_fraud'].values
    thresholds = []

    for fold, (tr_idx, val_idx) in enumerate(skf.split(X, y)):
        X_tr, X_val = X[tr_idx], X[val_idx]
        y_tr, y_val = y[tr_idx], y[val_idx]

        scale_pos = max(1, (y_tr == 0).sum() / (y_tr == 1).sum())
        p = {**params, 'scale_pos_weight': scale_pos}

        dtrain = xgb.DMatrix(X_tr, label=y_tr)
        dval   = xgb.DMatrix(X_val, label=y_val)

        bst = xgb.train(
            p, dtrain,
            num_boost_round=500,
            evals=[(dval, 'eval')],
            early_stopping_rounds=20,
            verbose_eval=False,
        )
        y_prob = bst.predict(dval)
        thresh, f = find_best_threshold_cv(y_val, y_prob, beta=beta)
        thresholds.append(thresh)
        print(f"  fold {fold+1}: threshold={thresh:.4f}  F{beta}={f:.4f}")

    mean_thresh = float(np.mean(thresholds))
    print(f"  -> CV mean threshold: {mean_thresh:.4f}")
    return mean_thresh


def train(path="transactions_sample.csv", model_out="fraud_model.joblib"):
    df = pd.read_csv(path)

    train_df, test_df = train_test_split(
        df, stratify=df['is_fraud'], test_size=0.2, random_state=42
    )

    train_df, user_avg_lookup, merchant_freq_lookup = featurize(train_df, is_train=True)
    test_df, _, _ = featurize(
        test_df,
        user_avg_lookup=user_avg_lookup,
        merchant_freq_lookup=merchant_freq_lookup,
        is_train=False,
    )

    feature_cols = [
        'log_amount', 'hour', 'amount_over_user_avg',
        'dist_from_home', 'merchant_freq', 'txn_count_last_1h',
    ]

    X_train = train_df[feature_cols].fillna(0)
    y_train = train_df['is_fraud']
    X_test  = test_df[feature_cols].fillna(0)
    y_test  = test_df['is_fraud']

    scale_pos = max(1, (len(y_train) - y_train.sum()) / (y_train.sum() + 1e-9))

    params = {
        'objective':        'binary:logistic',
        'eval_metric':      'auc',
        'eta':              0.05,
        'max_depth':        4,
        'min_child_weight': 5,
        'subsample':        0.8,
        'colsample_bytree': 0.8,
        'gamma':            1.0,
        'reg_lambda':       2.0,
        'scale_pos_weight': scale_pos,
        'seed':             42,
        'verbosity':        0,
    }

    dtrain = xgb.DMatrix(X_train, label=y_train)
    dtest  = xgb.DMatrix(X_test,  label=y_test)

    bst = xgb.train(
        params, dtrain,
        num_boost_round=500,
        evals=[(dtrain, 'train'), (dtest, 'eval')],
        early_stopping_rounds=20,
        verbose_eval=False,
    )
    print(f"Best iteration: {bst.best_iteration}")

    y_prob = bst.predict(dtest)
    auc = roc_auc_score(y_test, y_prob)
    ap  = average_precision_score(y_test, y_prob)
    print(f"\nAUC: {auc:.4f}  |  AvgPrecision: {ap:.4f}")

    print("\nEstimating threshold via cross-validation...")
    cv_threshold = cross_validated_threshold(train_df, feature_cols, params)

    single_thresh, single_f1 = find_best_threshold_cv(y_test.values, y_prob)
    print(f"\nSingle-split threshold: {single_thresh:.4f}  F1={single_f1:.4f}")
    print(f"CV threshold (used):    {cv_threshold:.4f}")

    y_pred = (y_prob >= cv_threshold).astype(int)
    print(f"\nClassification report at CV threshold ({cv_threshold:.4f}):")
    print(classification_report(y_test, y_pred))

    user_txn_count = train_df.groupby('user_id').size().to_dict()

    joblib.dump({
        "model":                bst,
        "features":             feature_cols,
        "threshold":            cv_threshold,
        "user_avg_lookup":      user_avg_lookup,
        "merchant_freq_lookup": merchant_freq_lookup,
        "user_txn_count":       user_txn_count,
    }, model_out)
    print(f"Saved model + metadata to {model_out}")


if __name__ == "__main__":
    train()
