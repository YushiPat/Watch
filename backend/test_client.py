import requests

# Define the base URL of the Flask API
base_url = "http://127.0.0.1:5000"

# Test endpoint
test_url = f"{base_url}/api/test"

def test_endpoint():
    response = requests.get(test_url)
    if response.status_code == 200:
        print("Test endpoint response:", response.json())
    else:
        print("Failed to call test endpoint:", response.status_code)

if __name__ == "__main__":
    test_endpoint()
