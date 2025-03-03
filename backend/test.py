import requests
import json

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
                "chest_pain_frequency": "occasional",
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


def test_endpoint(endpoint, payload):
    response = requests.post(f"{BASE_URL}{endpoint}", json=payload)
    print(f"\nTesting {endpoint}:")
    if response.status_code == 200:
        print("Success:", json.dumps(response.json(), indent=2))
    else:
        print("Error:", response.status_code, response.json()['error'])


# Test with Patient 1
patient_1_payload = {"data": [sample_data["data"][0]], "status": "success"}
print("Testing with Patient 1 (age 67, high risk):")
test_endpoint('/api/predict', patient_1_payload)
test_endpoint('/api/predict/risk', patient_1_payload)
test_endpoint('/api/predict/explain', patient_1_payload)

# Test with Patient 2
patient_2_payload = {"data": [sample_data["data"][1]], "status": "success"}
print("\nTesting with Patient 2 (age 40, low risk):")
test_endpoint('/api/predict', patient_2_payload)
test_endpoint('/api/predict/risk', patient_2_payload)
test_endpoint('/api/predict/explain', patient_2_payload)
