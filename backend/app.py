import datetime
import os
import pandas as pd
import numpy as np
from flask import Flask, request, jsonify
import pickle
import matplotlib.pyplot as plt
from sklearn.metrics import confusion_matrix, ConfusionMatrixDisplay
from flask_cors import CORS, cross_origin
from pymongo import MongoClient
from dotenv import load_dotenv

app = Flask(__name__)
cors = CORS(app)
app.config['CORS_HEADERS'] = 'Content-Type'

# Load environment variables
load_dotenv()

# MongoDB connection and data retrieval
mongo_url = os.getenv('MONGO_DB_CONNECTION')
client = MongoClient(mongo_url)
db = client.get_database("health_metrics")
documents = list(db.test.find())
print(f"Fetched {len(documents)} documents from MongoDB.")

# Create a new collection
predictions_collection = db.predictions  # Basic predictions
risk_collection = db.risk_assessments    # Risk assessments
explanation_collection = db.explanations  # Explanations

# Load the trained model
MODEL_PATH = 'model_heart_predictor.pkl'
if not os.path.exists(MODEL_PATH):
    raise FileNotFoundError(
        f"Model file not found at {MODEL_PATH}. Please train and save the model first.")
with open(MODEL_PATH, 'rb') as f:
    model = pickle.load(f)

# Directory to save figures
FIGURE_DIR = 'static/figures'
os.makedirs(FIGURE_DIR, exist_ok=True)

# Simulated test data for confusion matrix
# In a real scenario, load this from a file or database
X_test = pd.DataFrame({
    'age': [55, 62, 45],
    'heart_rate': [72, 85, 60],
    'lab_test_results.cholesterol': [220, 250, 180],
    'lab_test_results.blood_sugar': [90, 110, 85],
    'lab_test_results.triglycerides': [150, 200, 130],
    'current_symptoms.dizziness': [0, 1, 0],
    'current_symptoms.chest_pain_frequency': ['occasional', 'frequent', 'rare'],
    'lifestyle_factors.smoking_frequency': ['never', 'daily', 'occasional'],
    'current_symptoms.fatigue_level': ['moderate', 'severe', 'mild']
})
y_test = pd.Series([0, 1, 0])

# Helper function to get feature names from the pipeline


def get_feature_names(model):
    numerical_features = [
        'age', 'heart_rate', 'lab_test_results.cholesterol',
        'lab_test_results.blood_sugar', 'lab_test_results.triglycerides',
        'current_symptoms.dizziness'
    ]
    categorical_features = [
        'current_symptoms.chest_pain_frequency',
        'lifestyle_factors.smoking_frequency',
        'current_symptoms.fatigue_level'
    ]
    cat_encoder = model.named_steps['preprocessor'].named_transformers_[
        'categorical'].named_steps['encoder']
    cat_names = cat_encoder.get_feature_names_out(
        categorical_features).tolist()
    return numerical_features + cat_names

# Helper function to save feature importance plot


def save_feature_importance_plot(model, feature_names, user_id):
    importances = model.named_steps['classifier'].feature_importances_
    plt.figure(figsize=(10, 6))
    plt.barh(feature_names, importances)
    plt.xlabel('Importance')
    plt.title('Feature Importances for Heart Attack Risk Prediction')
    plt.tight_layout()
    plot_path = os.path.join(FIGURE_DIR, f'feature_importance_{user_id}.png')
    plt.savefig(plot_path)
    plt.close()
    return plot_path

# Helper function to save confusion matrix plot


def save_confusion_matrix_plot(model, X_test, y_test, user_id):
    y_pred = model.predict(X_test)
    cm = confusion_matrix(y_test, y_pred)
    disp = ConfusionMatrixDisplay(confusion_matrix=cm, display_labels=[
                                  'No Risk', 'High Risk'])
    disp.plot(cmap='Blues')
    plt.title('Confusion Matrix for Heart Attack Risk Model')
    plot_path = os.path.join(FIGURE_DIR, f'confusion_matrix_{user_id}.png')
    plt.savefig(plot_path)
    plt.close()
    return plot_path

# Preprocess incoming data to match training format


def preprocess_data(data):
    df = pd.json_normalize(data['data'])
    features = [
        'age', 'heart_rate', 'lab_test_results.cholesterol',
        'lab_test_results.blood_sugar', 'lab_test_results.triglycerides',
        'current_symptoms.dizziness', 'current_symptoms.chest_pain_frequency',
        'lifestyle_factors.smoking_frequency', 'current_symptoms.fatigue_level'
    ]
    missing_features = [f for f in features if f not in df.columns]
    if missing_features:
        raise ValueError(f"Missing features in input data: {missing_features}")
    return df[features]

# Endpoint 1: Basic prediction


@app.route('/api/predict', methods=['POST'])
@cross_origin()
def predict():
    try:
        data = request.get_json()
        if not data or 'data' not in data:
            return jsonify({'error': 'No data provided'}), 400

        X_input = preprocess_data(data)
        prediction = model.predict(X_input)
        probability = model.predict_proba(X_input)[:, 1][0]

        # Create a new collection to save the elements
        prediction_doc = {
            'user_id': data['data'][0]['user_id'],
            'prediction': int(prediction[0]),
            'probability': float(probability),
            'timestamp': datetime.datetime.utcnow(),
            'input_data': data['data']
        }

        predictions_collection.insert_one(prediction_doc)

        return jsonify({
            'prediction': int(prediction[0]),
            'probability': float(probability),
            'status': 'success'
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# Endpoint 2: Risk assessment with alert


@app.route('/api/predict/risk', methods=['POST'])
@cross_origin()
def predict_risk():
    try:
        data = request.get_json()
        if not data or 'data' not in data:
            return jsonify({'error': 'No data provided'}), 400

        X_input = preprocess_data(data)
        probability = model.predict_proba(X_input)[:, 1][0]
        user_id = data['data'][0]['user_id']

        if probability > 0.7:  # High-risk threshold
            alert = {
                'message': 'High risk of past heart attack detected. Consult a healthcare professional.',
                'time': data['data'][0]['timestamp'],
                'details': {
                    'heart_rate': data['data'][0]['heart_rate'],
                    'cholesterol': data['data'][0]['lab_test_results']['cholesterol']
                }
            }

            # Saving structure for risk assesment
            risk_doc = {
                'user_id': user_id,
                'probability': float(probability),
                'risk_level': 'high' if probability > 0.7 else 'low',
                'alert': alert if probability > 0.7 else None,
                'timestamp': datetime.datetime.utcnow(),
                'input_data': data['data']
            }

            # Insert all using the same collection
            risk_collection.insert_one(risk_doc)

            return jsonify({'risk': 'high', 'alert': alert, 'probability': float(probability)})
        else:
            return jsonify({'risk': 'low', 'probability': float(probability)})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# Endpoint 3: Prediction with explanation and images


@app.route('/api/predict/explain', methods=['POST'])
@cross_origin()
def predict_explain():
    try:
        data = request.get_json()
        if not data or 'data' not in data:
            return jsonify({'error': 'No data provided'}), 400

        X_input = preprocess_data(data)
        user_id = data['data'][0]['user_id']
        probability = model.predict_proba(X_input)[:, 1][0]

        # Get feature names and importances (without plots)
        feature_names = get_feature_names(model)
        importances = model.named_steps['classifier'].feature_importances_

        # Convert numpy types to native Python types
        feature_importance = {
            k: float(v) for k, v in zip(feature_names, importances)
        }

        explanation_doc = {
            'user_id': user_id,
            'probability': float(probability),
            'feature_importance': feature_importance,
            'timestamp': datetime.datetime.utcnow(),
            'input_data': data['data']
        }

        explanation_collection.insert_one(explanation_doc)

        response = {
            'probability': float(probability),
            'feature_importance': feature_importance,
            'status': 'success'
        }
        return jsonify(response)
    except Exception as e:
        return jsonify({'error': str(e)}), 500


if __name__ == '__main__':
    app.run(debug=True, host='127.0.0.1', port=5000)
