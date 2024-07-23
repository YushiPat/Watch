import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import confusion_matrix, accuracy_score, classification_report
import joblib

# Load the data
print("Loading data...")
data = pd.read_csv('heart_rate.csv')

# Prepare the features and target
features = ['age', 'sex', 'cp', 'trestbps', 'chol', 'fbs', 'restecg', 'thalach',
            'exang', 'oldpeak', 'slope', 'ca', 'thal']
X = data[features]
y = data['target']

# Split the data
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42)

# Scale the features
scaler = StandardScaler()
X_train_scaled = scaler.fit_transform(X_train)
X_test_scaled = scaler.transform(X_test)

# Create and train the Logistic Regression model
print("Training model...")
m1 = 'Logistic Regression'
lr = LogisticRegression()
model = lr.fit(X_train_scaled, y_train)

# Make predictions on the test set
print("Evaluating model...")
lr_predict = lr.predict(X_test_scaled)

# Evaluate the model
lr_conf_matrix = confusion_matrix(y_test, lr_predict)
lr_acc_score = accuracy_score(y_test, lr_predict)

print("Confusion Matrix:")
print(lr_conf_matrix)
print("\n")
print("Accuracy of Logistic Regression:", lr_acc_score*100, '\n')
print(classification_report(y_test, lr_predict))

# Save the model and scaler
print("Saving model and scaler...")
joblib.dump(model, 'heart_attack_risk_model_logistic.joblib')
joblib.dump(scaler, 'heart_attack_risk_scaler_logistic.joblib')

print("\nModel and scaler saved. You can now use them for predictions on new data.")
