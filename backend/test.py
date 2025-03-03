import os
from socketio import Client
from pymongo import MongoClient
from dotenv import load_dotenv
import json
import time

# Create a Socket.IO client instance
sio = Client(logger=True, engineio_logger=True)
# Sample data with two patients
sample_data = {
    "data": [
        # Patient 1: Age 67, high risk (from original data)
        {
            "_id": "6798032a548d383f480010c6",
            "accelerometer_readings": 1.17,
            "age": 67,
            "current_symptoms": {
                "chest_pain_frequency": "none",
                "dizziness": 0,
                "fatigue_level": "low",
                "shortness_of_breath": 0
            },
            "environmental_factors": {"air_quality_index": 240, "outdoor_temperature": 3.0},
            "exercise_status": "exercising",
            "family_history": {"diabetes": 1, "heart_disease": 0, "stroke": 0},
            "heart_rate": 93,
            "lab_test_results": {
                "blood_sugar": 111.5283695375264,
                "cholesterol": 238.2298635267137,
                "triglycerides": 115.33879289051784
            },
            "lifestyle_factors": {
                "alcohol_consumption": "moderate",
                "diet_type": "balanced",
                "physical_activity_level": "moderate",
                "smoking_frequency": "daily"
            },
            "medical_conditions": {
                "arrhythmia_history": 1, "diabetes": 1, "heart_disease": 0,
                "high_cholesterol": 1, "hypertension": 1, "low_bp_history": 0,
                "obesity": 0, "past_heart_attack": 1, "sleep_apnea": 1,
                "smoking": 0, "stroke_history": 0
            },
            "medication_use": {
                "blood_thinners": 1, "bp_medications": 1,
                "cholesterol_medications": 1, "diabetes_medications": 0
            },
            "prior_fall_history": 0,
            "psychological_factors": {"sleep_quality": "average", "stress_level": "moderate"},
            "sex": 1,
            "time_of_day": "17:35:27",
            "timestamp": "2024-12-28T17:35:27.019222",
            "user_id": 1
        },

        # Patient 2: Age 40, low risk
        {
            "_id": "6798032a548d383f48009999",
            "accelerometer_readings": 1.0,
            "age": 40,
            "current_symptoms": {
                "chest_pain_frequency": "none",
                "dizziness": 0,
                "fatigue_level": "low",
                "shortness_of_breath": 0
            },
            "environmental_factors": {"air_quality_index": 100, "outdoor_temperature": 20.0},
            "exercise_status": "resting",
            "family_history": {"diabetes": 0, "heart_disease": 0, "stroke": 0},
            "heart_rate": 70,
            "lab_test_results": {
                "blood_sugar": 90.0,
                "cholesterol": 160.0,
                "triglycerides": 100.0
            },
            "lifestyle_factors": {
                "alcohol_consumption": "none",
                "diet_type": "balanced",
                "physical_activity_level": "high",
                "smoking_frequency": "never"
            },
            "medical_conditions": {
                "arrhythmia_history": 0, "diabetes": 0, "heart_disease": 0,
                "high_cholesterol": 0, "hypertension": 0, "low_bp_history": 0,
                "obesity": 0, "past_heart_attack": 0, "sleep_apnea": 0,
                "smoking": 0, "stroke_history": 0
            },
            "medication_use": {
                "blood_thinners": 0, "bp_medications": 0,
                "cholesterol_medications": 0, "diabetes_medications": 0
            },
            "prior_fall_history": 0,
            "psychological_factors": {"sleep_quality": "good", "stress_level": "low"},
            "sex": 0,
            "time_of_day": "10:00:00",
            "timestamp": "2025-02-20T10:00:00.000000",
            "user_id": 2
        }
    ],
    "status": "success"
}

# URL of the Flask app
BASE_URL = 'http://127.0.0.1:5000'
# Event flags and response storage
received_response = None
received_error = None

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


@sio.event
def connect():
    print("Connected to server")


@sio.event
def disconnect():
    print("Disconnected from server")


# Event flags and response storage
received_response = None
received_error = None


@sio.event
def connect():
    print("Connected to server")


@sio.event
def disconnect():
    print("Disconnected from server")


@sio.event
def prediction_result(data):
    global received_response
    received_response = data
    sio.disconnect()


@sio.event
def risk_assessment(data):
    global received_response
    received_response = data
    sio.disconnect()


@sio.event
def explanation_response(data):
    global received_response
    received_response = data
    sio.disconnect()


@sio.event
def prediction_error(data):
    global received_error
    received_error = data
    sio.disconnect()


# Modify the test_websocket_endpoint function
def test_websocket_endpoint(event_name, payload, timeout=10):
    global received_response, received_error
    received_response = None
    received_error = None

    try:
        # Force new connection each time
        if sio.connected:
            sio.disconnect()

        sio.connect('http://127.0.0.1:5000')
        sio.emit(event_name, payload)

        # Wait for response
        start_time = time.time()
        while time.time() - start_time < timeout:
            if received_response or received_error:
                break
            time.sleep(0.1)

        if received_error:
            print(f"Error: {json.dumps(received_error, indent=2)}")
        elif received_response:
            print("Success:", json.dumps(received_response, indent=2))
        else:
            print("Timeout waiting for response")

    except Exception as e:
        print(f"Error during {event_name}: {str(e)}")
    finally:
        if sio.connected:
            sio.disconnect()
        time.sleep(0.5)  # Cleanup time
        received_response = None
        received_error = None


# Add verification steps after each test
def verify_collections():
    # Basic predictions
    print("\nBasic Predictions Collection:")
    print(predictions_collection.count_documents({}))

    # Risk assessments
    print("Risk Assessments Collection:")
    print(risk_collection.count_documents({}))

    # Explanations
    print("Explanations Collection:")
    print(explanation_collection.count_documents({}))


# Test with Patient 1
print("Testing with Patient 1 (age 67, high risk):")
patient_1_payload = {"data": [sample_data["data"][0]]}

print("\nTesting predict:")
test_websocket_endpoint('predict', patient_1_payload)

print("\nTesting assess_risk:")
test_websocket_endpoint('assess_risk', patient_1_payload)

print("\nTesting request_explanation:")
test_websocket_endpoint('request_explanation', patient_1_payload)

# Test with Patient 2
print("\nTesting with Patient 2 (age 40, low risk):")
patient_2_payload = {"data": [sample_data["data"][1]]}

print("\nTesting predict:")
test_websocket_endpoint('predict', patient_2_payload)

print("\nTesting assess_risk:")
test_websocket_endpoint('assess_risk', patient_2_payload)

print("\nTesting request_explanation:")
test_websocket_endpoint('request_explanation', patient_2_payload)

# Add this at the end of the test script
verify_collections()
