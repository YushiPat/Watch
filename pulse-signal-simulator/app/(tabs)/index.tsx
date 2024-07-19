import React, { useState, useEffect } from 'react';
import { StyleSheet, Text, View, TouchableOpacity } from 'react-native';
import { storeSensorData } from '../api/apiServices'; // Updated to import only storeSensorData

const App: React.FC = () => {
  const [pulse, setPulse] = useState<number>(70); // Starting with a lower pulse for an older person
  const [isMonitoring, setIsMonitoring] = useState<boolean>(false); // State to track monitoring status

  useEffect(() => {
    let intervalId: NodeJS.Timeout | null = null;

    if (isMonitoring) {
      intervalId = setInterval(() => {
        const newPulse = generateHeartRate(pulse);
        setPulse(newPulse);

        // Store new sensor data in the database
        const userData = {
          patient_id: 'P123456789', // Replace with actual patient ID
          heart_rate: newPulse,
          time_of_data: new Date().toISOString(),
          // Add other sensor data fields as needed
        };
        storeSensorData(userData)
          .then(() => console.log('Data stored successfully:', userData))
          .catch(error => console.error('Error storing data:', error));
      }, 1000);
    }

    return () => {
      if (intervalId) {
        clearInterval(intervalId); // Clear interval on component unmount or when monitoring stops
      }
    };
  }, [isMonitoring, pulse]);

  const startMonitoring = () => {
    setIsMonitoring(true); // Start monitoring
  };

  const stopMonitoring = () => {
    setIsMonitoring(false); // Stop monitoring
  };

  const generateHeartRate = (currentPulse: number): number => {
    const variation = (Math.random() - 0.5) * 1.2; // Adjust variability for older person
    let newPulse = currentPulse + variation;
    newPulse = Math.max(60, Math.min(75, newPulse)); // Adjust range for older person (60-75 BPM)
    return newPulse;
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Heart Rate Monitor</Text>
      <Text style={styles.pulse}>{pulse.toFixed(1)} BPM</Text>

      <View style={styles.buttonContainer}>
        <TouchableOpacity style={[styles.button, isMonitoring ? styles.stopButton : styles.startButton]} onPress={isMonitoring ? stopMonitoring : startMonitoring}>
          <Text style={styles.buttonText}>{isMonitoring ? 'Stop Monitoring' : 'Start Monitoring'}</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff',
  },
  title: {
    fontSize: 24,
    marginBottom: 20,
  },
  pulse: {
    fontSize: 48,
    color: 'red',
  },
  buttonContainer: {
    position: 'absolute',
    bottom: 20,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  button: {
    paddingVertical: 10,
    paddingHorizontal: 20,
    marginHorizontal: 10,
    borderRadius: 5,
  },
  startButton: {
    backgroundColor: '#007AFF',
  },
  stopButton: {
    backgroundColor: '#FF3B30',
  },
  buttonText: {
    fontSize: 18,
    color: '#fff',
  },
});

export default App;