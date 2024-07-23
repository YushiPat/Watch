import pandas as pd
import numpy as np
import joblib

# Load the trained model and scaler
model = joblib.load('heart_attack_risk_model_logistic.joblib')
scaler = joblib.load('heart_attack_risk_scaler_logistic.joblib')

# Define the risk threshold
RISK_THRESHOLD = 0.7  # Adjust based on clinical advice or risk tolerance


def preprocess_heart_rate_data(heart_rate):
    features = {
        'age': 50,          # Example: Replace with actual data or logic
        'sex': 1,           # Example: Replace with actual data or logic
        'cp': 0,            # Example: Replace with actual data or logic
        'trestbps': 120,    # Example: Replace with actual data or logic
        'chol': 200,        # Example: Replace with actual data or logic
        'fbs': 0,           # Example: Replace with actual data or logic
        'restecg': 0,       # Example: Replace with actual data or logic
        'thalach': heart_rate,  # Heart rate from wearable device
        'exang': 0,         # Example: Replace with actual data or logic
        'oldpeak': 0,       # Example: Replace with actual data or logic
        'slope': 1,         # Example: Replace with actual data or logic
        'ca': 0,            # Example: Replace with actual data or logic
        'thal': 0           # Example: Replace with actual data or logic
    }
    return pd.DataFrame([features])


def predict_heart_attack_risk(heart_rate):
    new_data = preprocess_heart_rate_data(heart_rate)

    # Scale the new data
    new_data_scaled = scaler.transform(new_data)

    # Make prediction
    prediction = model.predict(new_data_scaled)
    probability = model.predict_proba(new_data_scaled)[:, 1]

    return prediction[0], probability[0]


def main():
    print("Welcome to the Heart Attack Risk Prediction Console")
    print("Please enter two heart rates:")
    print("The first value should be a heart rate where the user is expected to be fine.")
    print("The second value should be a heart rate where the user is expected to have a heart attack.")

    try:
        heart_rate1 = float(input("Heart Rate 1: "))
        heart_rate2 = float(input("Heart Rate 2: "))

        if heart_rate1 <= 0 or heart_rate2 <= 0:
            print("Invalid heart rate. Please enter positive numbers.")
            return

        # Predict risk for both heart rates
        prediction1, probability1 = predict_heart_attack_risk(heart_rate1)
        prediction2, probability2 = predict_heart_attack_risk(heart_rate2)

        # Print results for the first heart rate
        print(f"\nEntered Heart Rate 1: {heart_rate1}")
        print(
            f"Prediction: {'Higher risk' if prediction1 == 1 else 'Lower risk'}")
        print(f"Probability of higher heart attack risk: {probability1:.2f}")
        if probability1 >= RISK_THRESHOLD:
            print("ALERT: High risk detected! Seek medical attention immediately.")
        elif probability1 >= 0.5:
            print("Warning: Moderate risk detected. Please monitor your health closely.")
        else:
            print("Risk level is low. Continue to monitor your health regularly.")

        # Print results for the second heart rate
        print(f"\nEntered Heart Rate 2: {heart_rate2}")
        print(
            f"Prediction: {'Higher risk' if prediction2 == 1 else 'Lower risk'}")
        print(f"Probability of higher heart attack risk: {probability2:.2f}")
        if probability2 >= RISK_THRESHOLD:
            print("ALERT: High risk detected! Seek medical attention immediately.")
        elif probability2 >= 0.5:
            print("Warning: Moderate risk detected. Please monitor your health closely.")
        else:
            print("Risk level is low. Continue to monitor your health regularly.")

    except ValueError:
        print("Invalid input. Please enter numerical values for the heart rates.")


if __name__ == "__main__":
    main()
