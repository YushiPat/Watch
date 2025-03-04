import os
import json
import time
import requests
from bson import ObjectId
from pymongo import MongoClient
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()

# MongoDB connection and data retrieval
mongo_url = os.getenv('MONGO_DB_CONNECTION')
client = MongoClient(mongo_url)
db = client.get_database("health_metrics")
documents = list(db.test.find())
print(f"Fetched {len(documents)} documents from MongoDB.")

# Base URL of the Flask app
BASE_URL = 'http://127.0.0.1:5000'

def convert_objectids(item):
    """
    Recursively convert ObjectId fields in dictionaries/lists to strings.
    """
    if isinstance(item, dict):
        return {key: convert_objectids(value) for key, value in item.items()}
    elif isinstance(item, list):
        return [convert_objectids(element) for element in item]
    elif isinstance(item, ObjectId):
        return str(item)
    return item

def test_endpoint(endpoint, payload):
    """Make a POST request to a given endpoint with the provided payload."""
    try:
        response = requests.post(f"{BASE_URL}{endpoint}", json=payload)
        print(f"\nTesting endpoint: {endpoint}")
        if response.status_code == 200:
            print("Success:", json.dumps(response.json(), indent=2))
        else:
            error_message = response.json().get('error', 'No error message provided')
            print("Error:", response.status_code, error_message)
    except requests.exceptions.RequestException as req_err:
        print(f"Request failed for endpoint {endpoint}: {req_err}")

# List of endpoints to test
endpoints = ['/api/predict', '/api/predict/risk', '/api/predict/explain']

# Iterate over each document and test all endpoints
for i, data_entry in enumerate(documents):
    # Convert any ObjectId instances to strings in the current document
    safe_data_entry = convert_objectids(data_entry)
    
    # Create a payload for the current patient record
    patient_payload = {"data": [safe_data_entry], "status": "success"}
    print(f"\nProcessing Patient {i+1} payload:")
    
    for endpoint in endpoints:
        test_endpoint(endpoint, patient_payload)
        # Pause briefly between requests to avoid overwhelming the server
        time.sleep(0.5)
