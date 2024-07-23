import React, { useState, useEffect } from "react";
import { StyleSheet, Text, View, TouchableOpacity } from "react-native";
import { storeSignalData } from "../api/apiServices";

const App: React.FC = () => {
  const [pulse, setPulse] = useState<number>(70);
  const [isMonitoring, setIsMonitoring] = useState<boolean>(false);

  useEffect(() => {
    let intervalId: NodeJS.Timeout | null = null;

    if (isMonitoring) {
      intervalId = setInterval(() => {
        const newPulse = generateHeartRate(pulse);
        setPulse(newPulse);

        // Store new sensor data in the database
        const userData = {
          patient_id: "P123456789",
          heart_rate: newPulse,
          time_of_data: new Date().toISOString(),
        };
        storeSignalData(userData)
          .then(() => console.log("Data stored successfully:", userData))
          .catch((error: string) =>
            console.error("Error storing data:", error)
          );
      }, 1000);
    }

    return () => {
      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [isMonitoring, pulse]);

  const startMonitoring = () => {
    setIsMonitoring(true);
  };

  const stopMonitoring = () => {
    setIsMonitoring(false);
  };

  const generateHeartRate = (currentPulse: number): number => {
    const variation = (Math.random() - 0.5) * 1.2;
    let newPulse = currentPulse + variation;
    newPulse = Math.max(60, Math.min(75, newPulse));
    return newPulse;
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Heart Rate Monitor</Text>
      <Text style={styles.pulse}>{pulse.toFixed(1)} BPM</Text>

      <View style={styles.buttonContainer}>
        <TouchableOpacity
          style={[
            styles.button,
            isMonitoring ? styles.stopButton : styles.startButton,
          ]}
          onPress={isMonitoring ? stopMonitoring : startMonitoring}
        >
          <Text style={styles.buttonText}>
            {isMonitoring ? "Stop Monitoring" : "Start Monitoring"}
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#fff",
  },
  title: {
    fontSize: 24,
    marginBottom: 20,
  },
  pulse: {
    fontSize: 48,
    color: "red",
  },
  buttonContainer: {
    position: "absolute",
    bottom: 20,
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
  },
  button: {
    paddingVertical: 10,
    paddingHorizontal: 20,
    marginHorizontal: 10,
    borderRadius: 5,
  },
  startButton: {
    backgroundColor: "#007AFF",
  },
  stopButton: {
    backgroundColor: "#FF3B30",
  },
  buttonText: {
    fontSize: 18,
    color: "#fff",
  },
});

export default App;
