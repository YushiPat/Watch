from flask import Flask, request, jsonify
from pymongo import MongoClient
from bson.json_util import dumps
import base64
import qrcode
import io

app = Flask(__name__)

# MongoDB setup (Replace with your MongoDB connection string)
client = MongoClient('mongodb://localhost:27017/')
db = client['sensor_database']

# Endpoint to receive and store sensor data
@app.route('/api/sensor-data', methods=['POST'])
def store_sensor_data():
    data = request.json
    db.sensor_data.insert_one(data)
    return jsonify({"response": "Data saved"}), 201

# Endpoint to fetch all sensor data for a specific user
@app.route('/api/sensor-data/<user_id>', methods=['GET'])
def get_sensor_data(user_id):
    data = db.sensor_data.find({"user_id": user_id})
    return dumps(data), 200

# Endpoint to fetch the latest sensor data for a specific user
@app.route('/api/sensor-data/latest/<user_id>', methods=['GET'])
def get_latest_sensor_data(user_id):
    data = db.sensor_data.find({"user_id": user_id}).sort("time_of_data", -1).limit(1)
    return dumps(data[0]) if data.count() > 0 else jsonify({}), 200

# Endpoint to fetch prediction data for a specific user
@app.route('/api/predictions/<user_id>', methods=['GET'])
def get_prediction_data(user_id):
    data = db.predictions.find({"user_id": user_id})
    return dumps(data), 200

# Endpoint to create a new user account and generate a QR code
@app.route('/api/create_account', methods=['POST'])
def create_account():
    data = request.json
    user_id = data.get("user_id")
    db.users.insert_one(data)

    # Generate a QR code
    qr = qrcode.QRCode(version=1, box_size=10, border=5)
    qr.add_data(user_id)
    qr.make(fit=True)
    img = qr.make_image(fill='black', back_color='white')

    # Convert the image to a base64 string
    buffered = io.BytesIO()
    img.save(buffered, format="PNG")
    img_str = base64.b64encode(buffered.getvalue()).decode('utf-8')

    return jsonify({"qr_code": f"data:image/png;base64,{img_str}"}), 201

if __name__ == '__main__':
    app.run(debug=True)
