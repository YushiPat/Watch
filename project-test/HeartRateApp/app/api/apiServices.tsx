import axios from 'axios';

export const storeSignalData = async (signalData: { patient_id: string, heart_rate: number, time_of_data: string }) => {
  try {
    const response = await axios.post('http://127.0.0.1:5000/api/store-signal', signalData);
    console.log('Response:', response.data);
  } catch (error) {
    console.error('Error storing signal data:', error);
  }
};