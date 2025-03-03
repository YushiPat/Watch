import pandas as pd
import numpy as np
import logging
from pymongo import MongoClient
from dotenv import load_dotenv
import os
from sklearn.model_selection import train_test_split, GridSearchCV
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.impute import SimpleImputer
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
import xgboost as xgb
import pickle
from sklearn.model_selection import ParameterGrid

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()

# MongoDB connection and data retrieval
mongo_url = os.getenv('MONGO_DB_CONNECTION')
client = MongoClient(mongo_url)
db = client.get_database("health_metrics")
documents = list(db.test.find())
logger.info(f"Fetched {len(documents)} documents from MongoDB.")

# Flatten nested JSON data
df = pd.json_normalize(documents)
logger.info(f"Columns in raw data: {df.columns.tolist()}")

# Define columns. Used top 10 best features for prediction
FEATURES = [
    'age',
    'heart_rate',
    'lab_test_results.cholesterol',
    'lab_test_results.blood_sugar',
    'lab_test_results.triglycerides',
    'current_symptoms.chest_pain_frequency',
    'lifestyle_factors.smoking_frequency',
    'current_symptoms.dizziness',
    'current_symptoms.fatigue_level',
    'medical_conditions.past_heart_attack'
]

# Filter dataframes to obtain only required data
df = df[FEATURES]
logger.info(f"Columns after filtering to top features: {df.columns.tolist()}")

# Set the target variable
target_column = "medical_conditions.past_heart_attack"
if target_column not in df.columns:
    logger.error(f"Target column '{target_column}' not found in data.")
    raise Exception("Target column missing from data.")

# Handle missing values in target
if df[target_column].isnull().any():
    logger.warning(
        "Target variable contains missing values. Dropping those rows.")
    df = df.dropna(subset=[target_column])

# Separate features and target
X = df.drop(columns=[target_column])
y = df[target_column]

# Ensure target binary requirements
if y.dtype == 'object':
    y = y.map({'yes': 1, 'no': 0, 'Yes': 1, 'No': 0, True: 1, False: 0})
    if y.isnull().any():
        logger.error("Target contains unmapped values after conversion.")
        raise ValueError("Target must be binary after mapping.")
else:
    y = y.astype(int)

# Split into training and test sets with stratification
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y
)

# Log class distribution and calculate weights in position
class_dist = pd.Series(y_train).value_counts(normalize=True)
logger.info(f"Class distribution in y_train: {class_dist}")
scale_pos_weight = class_dist[0] / class_dist[1] if class_dist[1] > 0 else 1
logger.info(f"Calculated scale_pos_weight: {scale_pos_weight:.2f}")

# Explicitly define numerical and categorical features
numerical_features = [
    'age',
    'heart_rate',
    'lab_test_results.cholesterol',
    'lab_test_results.blood_sugar',
    'lab_test_results.triglycerides',
    'current_symptoms.dizziness'
]
categorical_features = [
    'current_symptoms.chest_pain_frequency',
    'lifestyle_factors.smoking_frequency',
    'current_symptoms.fatigue_level'
]

# Define preprocessing pipelines
numerical_preprocessor = Pipeline(steps=[
    ('imputer', SimpleImputer(strategy='median')),
    ('scaler', StandardScaler())
])

categorical_preprocessor = Pipeline(steps=[
    ('imputer', SimpleImputer(strategy='most_frequent')),
    ('encoder', OneHotEncoder(handle_unknown='ignore', sparse_output=False))
])

preprocessor = ColumnTransformer(
    transformers=[
        ('numerical', numerical_preprocessor, numerical_features),
        ('categorical', categorical_preprocessor, categorical_features)
    ]
)

# Define the full pipeline
pipeline = Pipeline(steps=[
    ('preprocessor', preprocessor),
    ('classifier', xgb.XGBClassifier(
        objective='binary:logistic',
        eval_metric='logloss',
        scale_pos_weight=scale_pos_weight,
        random_state=42
    ))
])

# Hyperparameter grid
param_grid = {
    'classifier__n_estimators': [100, 200, 300],
    'classifier__max_depth': [5, 7, 9],
    'classifier__learning_rate': [0.01, 0.05, 0.1],
    'classifier__subsample': [0.8, 1.0],
    'classifier__colsample_bytree': [0.8, 1.0]
}

# Calculate total iterations
cv_folds = 5
total_iterations = len(list(ParameterGrid(param_grid))) * cv_folds
logger.info(f"Total GridSearchCV iterations: {total_iterations}")

# Perform grid search
grid_search = GridSearchCV(
    pipeline,
    param_grid,
    cv=cv_folds,
    scoring='accuracy',
    n_jobs=-1,
    verbose=2
)

logger.info("Starting GridSearchCV...")
grid_search.fit(X_train, y_train)

# Log results
logger.info(f"Best parameters: {grid_search.best_params_}")
logger.info(f"Best CV accuracy: {grid_search.best_score_:.2%}")

# Evaluate on test set
best_model = grid_search.best_estimator_
test_accuracy = best_model.score(X_test, y_test)
logger.info(f"Test accuracy: {test_accuracy:.2%}")

# Save the entire pipeline
model_output_path = 'model_heart_predictor.pkl'
with open(model_output_path, 'wb') as f:
    pickle.dump(best_model, f)
logger.info(f"Trained model pipeline saved to {model_output_path}")

# Log feature importances
X_train_preprocessed = best_model.named_steps['preprocessor'].transform(
    X_train)
feature_names = (
    numerical_features +
    best_model.named_steps['preprocessor']
    .named_transformers_['categorical']
    .named_steps['encoder']
    .get_feature_names_out(categorical_features).tolist()
)
importances = best_model.named_steps['classifier'].feature_importances_

# Sort features by importance
feature_importances = sorted(
    zip(feature_names, importances), key=lambda x: x[1], reverse=True)
logger.info("Top feature importances:")
for feat, imp in feature_importances:
    logger.info(f"{feat}: {imp:.4f}")
