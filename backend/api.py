from flask import Flask, request, jsonify
from flask_cors import CORS
from pymongo import MongoClient
from bson.json_util import dumps
import base64
import qrcode
import io
import joblib
import numpy as np
import pandas as pd  
from models import PatientInfo, HeartRateData


# Load the trained model and scaler
model = joblib.load('heart_attack_risk_model_logistic.joblib')
scaler = joblib.load('heart_attack_risk_scaler_logistic.joblib')

app = Flask(__name__)
CORS(app)

# MongoDB setup
client = MongoClient(
    'mongodb+srv://lmaoking0:zlIsFpEOJ7l78Y8W@cluster0.9yuhxy8.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0')
db = client['health_metrics']
health_metrics_signals = db.health_metrics_signals
user_account_records = db.user_accounts
prediction_records = db.predictions

# Test endpoint


@app.route('/api/test', methods=['GET'])
def test_response():
    return jsonify({"response": "Test endpoint called!"}), 200

# Endpoint to receive and store health metrics sensor data to MongoDB


@app.route('/api/sensor-data', methods=['POST'])
def store_sensor_data():
    data = request.json
    health_metrics_records.insert_one(data)
    return jsonify({"response": "Data saved"}), 200

# Endpoint to fetch all sensor data for a specific user


@app.route('/api/sensor-data/<patient_id>', methods=['GET'])
def get_sensor_data(patient_id):
    data = health_metrics_records.find({"patient_id": patient_id})
    return dumps(data), 200

# Endpoint to fetch the latest sensor data for a specific user


@app.route('/api/sensor-data/latest/<patient_id>', methods=['GET'])
def get_latest_sensor_data(patient_id):
    data = list(health_metrics_records.find(
        {"patient_id": patient_id}).sort("time_of_data", -1).limit(1))
    return dumps(data[0]) if data else jsonify({}), 200

# Endpoint to fetch prediction data for a specific user


@app.route('/api/predictions/<patient_id>', methods=['GET'])
def get_prediction_data(patient_id):
    data = prediction_records.find({"patient_id": patient_id})
    return dumps(data), 200

# Endpoint to create a new user account and generate a QR code


@app.route('/api/create_account', methods=['POST'])
def create_account():
    data = request.json
    patient_id = data.get("patient_id")
    user_account_records.insert_one(data)

    # Generate a QR code
    qr = qrcode.QRCode(version=1, box_size=10, border=5)
    qr.add_data(patient_id)
    qr.make(fit=True)
    img = qr.make_image(fill='black', back_color='white')

    # Convert the image to a base64 string
    buffered = io.BytesIO()
    img.save(buffered, format="PNG")
    img_str = base64.b64encode(buffered.getvalue()).decode('utf-8')

    return jsonify({"qr_code": f"data:image/png;base64,{img_str}"}), 200

# New endpoint to store continuous heart rate signals


@app.route('/api/store-signal', methods=['POST'])
def store_signal():
    data = request.json
    patient_id = data.get('patient_id')
    heart_rate = data.get('heart_rate')
    time_of_data = data.get('time_of_data')

    if not patient_id or not heart_rate or not time_of_data:
        return jsonify({"error": "Missing required parameters"}), 400

    signal_data = {
        "patient_id": patient_id,
        "heart_rate": heart_rate,
        "time_of_data": time_of_data
    }

    health_metrics_signals.insert_one(signal_data)
    return jsonify({"response": "Signal data saved"}), 200

# Endpoint to fetch the 1000 most recent heart rate data for a specific user


@app.route('/api/heart-rate/<patient_id>', methods=['GET'])
def get_heart_rate_data(patient_id):
    data = health_metrics_signals.find(
        {"patient_id": patient_id},
        {"heart_rate": 1, "time_of_data": 1}
    ).sort("time_of_data", -1).limit(1000)

    heart_rate_data = [{"heart_rate": record["heart_rate"],
                        "time_of_data": record["time_of_data"]} for record in data]
    return jsonify(heart_rate_data), 200

def assess_heart_attack_risk(heart_rate, patient_id):
    dummy_features = [
        50,   # age (e.g., 50 years old)
        0,    # sex (0 = female, 1 = male)
        0,    # cp (chest pain type: 0 = typical angina, 1 = atypical angina, 2 = non-anginal pain, 3 = asymptomatic)
        120,  # trestbps (resting blood pressure in mm Hg)
        200,  # chol (serum cholesterol in mg/dl)
        0,    # fbs (fasting blood sugar > 120 mg/dl: 0 = false, 1 = true)
        0,    # restecg (resting electrocardiographic results: 0 = normal, 1 = having ST-T wave abnormality, 2 = showing left ventricular hypertrophy)
        heart_rate,  # thalach (maximum heart rate achieved)
        0,    # exang (exercise induced angina: 0 = no, 1 = yes)
        1.0,  # oldpeak (depression induced by exercise relative to rest)
        1,    # slope (slope of the peak exercise ST segment: 0 = upsloping, 1 = flat, 2 = downsloping)
        0,    # ca (number of major vessels colored by fluoroscopy)
        1     # thal (thalassemia: 0 = normal, 1 = fixed defect, 2 = reversible defect, 3 = unknown)
    ]
    
    # Create DataFrame for prediction
    new_data = pd.DataFrame([dummy_features], columns=[
        'age', 'sex', 'cp', 'trestbps', 'chol', 'fbs', 'restecg', 'thalach', 
        'exang', 'oldpeak', 'slope', 'ca', 'thal'
    ])

    # Scale the new data
    new_data_scaled = scaler.transform(new_data)
    
    # Make prediction
    prediction = model.predict(new_data_scaled)
    probability = model.predict_proba(new_data_scaled)[:, 1]
    
    # Define risk level based on prediction and probability
    risk_level = 'Low'
    flag = 0

    if prediction[0] == 1:
        if probability[0] > 0.7:
            risk_level = 'High'
            flag = 1
        elif probability[0] > 0.4:
            risk_level = 'Moderate'
    else:
        risk_level = 'Low'

    # Return response data
    return {
        "patient_id": patient_id,
        "prediction": int(prediction[0]),  # Ensure prediction is an int
        "risk_level": risk_level,
        "probability": float(probability[0]),  # Ensure probability is a float
        "flag": int(flag)  # Ensure flag is an int
    }

# Endpoint to receive and predict based on continuous heart rate data
@app.route('/api/predict-heart-attack', methods=['POST'])
def predict_heart_attack():
    data = request.json
    heart_rate = data.get('heart_rate')
    patient_id = data.get('patient_id')

    if heart_rate is None or patient_id is None:
        return jsonify({"error": "Missing required parameters"}), 400

    result = assess_heart_attack_risk(heart_rate, patient_id)
    return jsonify(result), 200


if __name__ == '__main__':
    app.run(debug=True)
