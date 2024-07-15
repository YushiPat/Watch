import requests

# Define the base URL of the Flask API
base_url = "http://127.0.0.1:5000"

# Test endpoint
test_url = f"{base_url}/api/test"
# Latest sensor data endpoint for a specific patient
latest_sensor_data_url = f"{base_url}/api/sensor-data/latest/P987654321"

def test_endpoint():
    response = requests.get(test_url)
    if response.status_code == 200:
        print("Test endpoint response:", response.json())
    else:
        print("Failed to call test endpoint:", response.status_code)

def test_latest_sensor_data():
    response = requests.get(latest_sensor_data_url)
    if response.status_code == 200:
        print("Latest sensor data response:", response.json())
    else:
        print("Failed to get latest sensor data:", response.status_code)

if __name__ == "__main__":
    # test_endpoint()
    test_latest_sensor_data()
