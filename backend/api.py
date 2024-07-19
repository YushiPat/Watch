from flask import Flask, request, jsonify
from flask_cors import CORS
from pymongo import MongoClient
from bson.json_util import dumps
import base64
import qrcode
import io
from models import PatientInfo, HeartRateData

app = Flask(__name__)
CORS(app)

# MongoDB setup
client = MongoClient('mongodb+srv://kpnaranj:opCYKM8BE8TlE1Kl@animecluster.5gqnvde.mongodb.net/anime_database?retryWrites=true&w=majority')
db = client['health_metrics']

user_account_records = db.user_accounts
prediction_records = db.heart_rate

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
    data = list(health_metrics_records.find({"patient_id": patient_id}).sort("time_of_data", -1).limit(1))
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

# New endpoint to fetch the 1000 most recent heart rate data for a specific user
@app.route('/api/heart-rate/<patient_id>', methods=['GET'])
def get_heart_rate_data(patient_id):
    data = health_metrics_records.find(
        {"patient_id": patient_id},
        {"heart_rate": 1, "time_of_data": 1}
    ).sort("time_of_data", -1).limit(1000)
    
    heart_rate_data = [{"heart_rate": record["heart_rate"], "time_of_data": record["time_of_data"]} for record in data]
    return jsonify(heart_rate_data), 200

if __name__ == '__main__':
    app.run(debug=True)
