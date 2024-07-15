var accelData = { x: 0, y: 0, z: 0, m: 0, ad: 0 }; // Global variable to store acceleration data
var maxDiff = 0;
var stepCount = 0; // Global variable to store cumulative step count
var prevMagnitude = 0; // Variable for step detection
const threshold = 1.0; // Adjusted threshold value for detecting steps
const stepInterval = 1000; // Minimum interval between steps (in milliseconds)
var lastStepTime = 0; // Time of the last detected step

// Function to reset step count at midnight
function resetDailyStepCount() {
  var now = new Date();
  var midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 0, 0);

  // Calculate the time remaining until midnight
  var timeUntilMidnight = midnight - now;

  // Reset step count at midnight
  setTimeout(function() {
    stepCount = 0;
    console.log('Step count reset for the new day.');

    // Call the function recursively to reset at the next midnight
    resetDailyStepCount();
  }, timeUntilMidnight);
}

// Start the advertising process
function startAdvertising() {
  Bangle.setBarometerPower(1); // Turn on barometer
  Bangle.setHRMPower(1); // Turn on heart rate monitor

  // Ensure accelerometer data is available by setting up listener
  Bangle.on('accel', handleAccelData);

  Promise.all([Bangle.getPressure(), getHeartRate()]).then(function(results) {
    var pressure = results[0];
    var heartRate = results[1];

    // Shorten the timestamp format (HH:mm:ss)
    var ts = new Date().toISOString().split('T')[1].split('.')[0];

    // Round the values to whole numbers and use short forms
    var sensorData = {
      ts: ts,
      d: {
        bp: Math.round(pressure.pressure),
        bt: Math.round(pressure.temperature),
        ba: Math.round(pressure.altitude),
        hr: heartRate,
        x: Math.round(accelData.x), // Accelerometer data (X-axis)
        y: Math.round(accelData.y), // Accelerometer data (Y-axis)
        z: Math.round(accelData.z), // Accelerometer data (Z-axis)
        m: Math.round(accelData.m), // Magnitude of acceleration
        ad: Math.round(accelData.ad), // Difference in magnitude (if available)
        s: stepCount // Add cumulative step count data
      }
    };
    console.log('Sensor data:', sensorData);

    // Convert the sensor data to JSON string
    var manufacturerData = JSON.stringify(sensorData);
    var chunks = chunkString(manufacturerData, 18); // Split data into smaller chunks

    // Function to send chunks sequentially
    function advertiseChunks(chunks) {
      if (chunks.length === 0) return; // Exit if no chunks left

      var dataChunk = chunks.shift(); // Get the next chunk
      console.log('Advertising chunk:', dataChunk);

      // Set the advertisement with the chunked data
      NRF.setAdvertising({}, {
        showName: true,
        manufacturer: 0x0590,
        manufacturerData: toUTF8Array(dataChunk)
      });

      // Send the next chunk after 1 second
      setTimeout(function() {
        advertiseChunks(chunks);
      }, 1000);
    }

    // Start advertising chunks
    advertiseChunks(chunks);

  }).catch(function(error) {
    console.error('Error getting sensor data:', error);
  });
}

// Helper function to get heart rate data
function getHeartRate() {
  return new Promise(function(resolve, reject) {
    Bangle.on('HRM', function(hrm) {
      resolve(hrm.bpm);
      Bangle.removeListener('HRM', arguments.callee); // Remove listener after getting the data
    });

    setTimeout(function() {
      reject('Heart rate data not available');
      Bangle.removeListener('HRM', arguments.callee); // Remove listener after timeout
    }, 5000); // Timeout after 5 seconds
  });
}

// Helper function to handle accelerometer data
function handleAccelData(accel) {
  // Calculate magnitude of acceleration
  const magnitude = Math.sqrt(accel.x * accel.x + accel.y * accel.y + accel.z * accel.z);
  const diff = Math.abs(magnitude - prevMagnitude);

  // Get current time
  const currentTime = new Date().getTime();

  // Simple step detection logic
  if (diff > threshold && (currentTime - lastStepTime) > stepInterval) {
    stepCount++;
    lastStepTime = currentTime; // Update time of last detected step
  }

  // Update global variables
  if (accel.diff > maxDiff) {
    maxDiff = accel.diff;
  }
  
  accelData = {
    x: accel.x,
    y: accel.y,
    z: accel.z,
    m: magnitude, // Magnitude of acceleration
    ad: maxDiff // Highest difference in magnitude
  };

  prevMagnitude = magnitude; // Update previous magnitude for next calculation
}

// Helper function to chunk a string into smaller parts
function chunkString(str, length) {
  var chunks = [];
  for (var i = 0; i < str.length; i += length) {
    chunks.push(str.substring(i, i + length));
  }
  return chunks;
}

// Helper function to convert string to UTF-8 array
function toUTF8Array(str) {
  var utf8 = [];
  for (var i = 0; i < str.length; i++) {
    var charcode = str.charCodeAt(i);
    if (charcode < 0x80) utf8.push(charcode);
    else if (charcode < 0x800) {
      utf8.push(0xc0 | (charcode >> 6),
                0x80 | (charcode & 0x3f));
    }
    else if (charcode < 0xd800 || charcode >= 0xe000) {
      utf8.push(0xe0 | (charcode >> 12),
                0x80 | ((charcode>>6) & 0x3f),
                0x80 | (charcode & 0x3f));
    }
    // surrogate pair
    else {
      i++;
      charcode = 0x10000 + (((charcode & 0x3ff)<<10)
                | (str.charCodeAt(i) & 0x3ff));
      utf8.push(0xf0 | (charcode >>18),
                0x80 | ((charcode>>12) & 0x3f),
                0x80 | ((charcode>>6) & 0x3f),
                0x80 | (charcode & 0x3f));
    }
  }
  return utf8;
}

// Set up continuous advertising
function continuousAdvertising() {
  startAdvertising(); // Start advertising immediately

  // Schedule the next advertising cycle
  setInterval(function() {
    startAdvertising(); // Gather new data and advertise again
  }, 20000); // 20 seconds cycle time (send data chunks, then wait for 20 seconds)
}

// Start the continuous advertising process and set up daily reset
continuousAdvertising();
resetDailyStepCount();
