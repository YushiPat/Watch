import axios from 'axios';

const BASE_URL = 'http://127.0.0.1:5000';

export const storeSensorData = async (data: any): Promise<void> => {
  try {
    console.log("Checking");
    const response = await axios.post(`${BASE_URL}/api/sensor-data`, data);
    console.log('Data saved:', response.data);
  } catch (error) {
    console.error('Error saving data:', error);
    throw error;
  }
};

export const fetchSensorData = async (patientId: string): Promise<any> => {
  try {
    const response = await axios.get(`${BASE_URL}/api/sensor-data/${patientId}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching data:', error);
    throw error;
  }
};

export const fetchLatestSensorData = async (patientId: string): Promise<any> => {
  try {
    const response = await axios.get(`${BASE_URL}/api/sensor-data/latest/${patientId}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching latest data:', error);
    throw error;
  }
};

export const createAccount = async (data: any): Promise<string> => {
  try {
    const response = await axios.post(`${BASE_URL}/api/create_account`, data);
    return response.data.qr_code; // Assuming the response returns a QR code URL
  } catch (error) {
    console.error('Error creating account:', error);
    throw error;
  }
};

export const fetchHeartRateData = async (patientId: string): Promise<any> => {
  try {
    const response = await axios.get(`${BASE_URL}/api/heart-rate/${patientId}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching heart rate data:', error);
    throw error;
  }
};