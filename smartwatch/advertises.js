var accelData = { x: 0, y: 0, z: 0, m: 0, ad: 0 }; // Global variable to store acceleration data
var maxDiff = 0;
var stepCount = 0; // Global variable to store cumulative step count
var prevMagnitude = 0; // Variable for step detection
const threshold = 1.0; // Adjusted threshold value for detecting steps
const stepInterval = 1000; // Minimum interval between steps (in milliseconds)
var lastStepTime = 0; // Time of the last detected step

var alertActive = false; // Flag to indicate if an alert is currently active
var alertCooldown = false; // Flag to indicate if the alert is in cooldown period
var advertisementInterval = 20000; // 20 seconds interval for regular advertising
var advertisingTimeoutId; // Variable to store the timeout ID for regular advertising
var advertisingInProgress = false; // Flag to indicate if an advertisement is currently in progress

const defaultColor = "#000000"; // Default color for the screen
var buzzerIntervalId = null; // Variable to store buzzer interval ID
var localStorageData = []; // Array to store data locally when not connected to Bluetooth

const zeroHeartRateThreshold = 30000; // Duration (ms) for how long the heart rate can be zero before advertising again
var heartRateZeroStart = null; // Timestamp when heart rate first became zero

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

// Function to format time as HH:MM:SS
function formatTime(date) {
    var hours = date.getHours().toString().padStart(2, '0');
    var minutes = date.getMinutes().toString().padStart(2, '0');
    var seconds = date.getSeconds().toString().padStart(2, '0');
    return hours + ':' + minutes + ':' + seconds;
}

// Function to show alert on the screen
function showAlert(title, message) {
    console.log('Showing alert:', title, message); // Log alert details
    g.clear(); // Clear screen
    g.setColor("#ff0000"); // Set color to red
    g.setFont("6x8", 4); // Increase font size
    g.setFontAlign(0, 0); // Center text
    g.drawString(title, g.getWidth()/2, g.getHeight()/2 - 10); // Display title
    g.drawString(message, g.getWidth()/2, g.getHeight()/2 + 10); // Display message
    alertActive = true; // Set alert active flag

    // Continuous buzzing until alert dismissed
    buzzerIntervalId = setInterval(function() {
        Bangle.buzz(); // Vibrate watch
    }, 1000); // Buzz every second (adjust as needed)

    // Immediately advertise data
    advertiseImmediate();

    // Handle touch event to dismiss alert
    function handleAlertDismiss() {
        if (alertActive) {
            console.log('Alert dismissed by touch'); // Log alert dismissal
            g.clear(); // Clear screen on tap
            g.setColor(defaultColor); // Reset color to default
            g.setFont("6x8", 2); // Reset font size
            alertActive = false; // Clear alert active flag

            // Stop the buzzer
            if (buzzerIntervalId !== null) {
                clearInterval(buzzerIntervalId);
                buzzerIntervalId = null; // Reset buzzer interval ID
            }

            // Set cooldown flag
            alertCooldown = true;
            setTimeout(function() {
                alertCooldown = false; // Clear cooldown flag after cooldown period (e.g., 5 minutes)
            }, 5000); // 5 minutes cooldown period (adjust as needed)

            // Detach touch event handler to prevent multiple dismissals
            Bangle.removeListener('touch', handleAlertDismiss);
        }
    }

    // Add touch event listener for alert dismissal
    Bangle.on('touch', handleAlertDismiss);
}

// Function to check heart rate and trigger alert if abnormal and non-zero
function checkHeartRate(heartRate) {
    //console.log('Checking heart rate:', heartRate); // Log heart rate check
    if (heartRate === 0) {
        if (heartRateZeroStart === null) {
            heartRateZeroStart = Date.now(); // Record start time when heart rate becomes zero
        } else if (Date.now() - heartRateZeroStart > zeroHeartRateThreshold) {
            console.log('Heart rate has been zero for too long. Pausing advertising.');
            return; // Exit if heart rate has been zero for too long
        }
    } else {
        heartRateZeroStart = null; // Reset the start time if heart rate is non-zero
    }

    if (!alertActive && !alertCooldown && heartRate > 0 && (heartRate < -1 || heartRate > 120)) {
        showAlert("","Abno-\nrmal\nHeart\nRate");
        // Record abnormal heart rate data
        var timestamp = new Date().toISOString();
        var abnormalData = { type: 'Heart Rate', timestamp: timestamp, value: heartRate };
        console.log('Abnormal heart rate recorded:', abnormalData);
    }
}

// Function to immediately advertise the current sensor data
function advertiseImmediate() {
    if (advertisingInProgress) return; // Exit if advertising is already in progress
    advertisingInProgress = true;

    // Fetch pressure and heart rate data asynchronously
    Promise.all([Bangle.getPressure(), getHeartRate()]).then(function(results) {
        var pressure = results[0];
        var heartRate = results[1];

        // Get the current sensor data
        var sensorData = {
            ts: formatTime(new Date()), // Use formatTime function to format the time
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
        };
        //console.log('Immediate advertisement data:', sensorData);

        // Convert the sensor data to JSON string
        var manufacturerData = JSON.stringify(sensorData);
        var chunks = chunkString(manufacturerData, 18); // Split data into smaller chunks

        // Function to send chunks sequentially
        function advertiseChunks(chunks) {
            if (chunks.length === 0) {
                advertisingInProgress = false; // Reset flag after advertising is done
                return; // Exit if no chunks left
            }

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
        advertisingInProgress = false; // Reset flag on error
    });
}

// Function to store data locally in a file
function storeDataLocally() {
    // Fetch pressure and heart rate data asynchronously
    Promise.all([Bangle.getPressure(), getHeartRate()]).then(function(results) {
        var pressure = results[0];
        var heartRate = results[1];

        // Get the current sensor data
        var sensorData = {
            ts: formatTime(new Date()), // Use formatTime function to format the time
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
        };

        // Add data to local storage array
        localStorageData.push(sensorData);
        console.log('Data stored locally:', sensorData);

        // Save data to file
        var jsonData = JSON.stringify(localStorageData);
        try {
            require('Storage').write('sensor_data.json', jsonData);
            console.log('Data successfully written to sensor_data.json');
        } catch (e) {
            console.error('Error writing data to file:', e);
        }

        // Optionally clear local storage data if necessary
        // localStorageData = []; // Uncomment if you want to clear data after writing
    }).catch(function(error) {
        console.error('Error getting sensor data:', error);
    });
}

// Function to draw the clock face
function drawClock() {
    var now = new Date();
    g.clear(); // Clear the screen
    g.setFont("6x8"); // Set font size
    g.setFontAlign(0, 0); // Center align
    g.drawString(formatTime(now), g.getWidth()/2, g.getHeight()/2); // Display time
}

// Function to start the clock
function startClock() {
    // Draw the clock every second
    setInterval(drawClock, 1000);
}

// Function to start the advertising process
function startAdvertising() {
    Bangle.setBarometerPower(1); // Turn on barometer
    Bangle.setHRMPower(1); // Turn on heart rate monitor

    // Ensure accelerometer data is available by setting up listener
    Bangle.on('accel', handleAccelData);

    // Periodic advertising every 20 seconds
    function periodicAdvertising() {
        if (!alertActive) {
            checkBluetoothConnection(); // Check Bluetooth connection status before advertising
            if (NRF.getSecurityStatus().connected) {
                advertiseImmediate(); // Advertise immediately if connected
            } else {
                storeDataLocally(); // Store data locally if not connected
            }
        }
        advertisingTimeoutId = setTimeout(periodicAdvertising, advertisementInterval);
    }

    periodicAdvertising(); // Start the periodic advertising loop
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

// Set up continuous heart rate monitoring
function startHeartRateMonitoring() {
    Bangle.on('HRM', function(hrm) {
        var heartRate = hrm.bpm;
        //console.log('Heart rate:', heartRate); // Log heart rate
        checkHeartRate(heartRate); // Check heart rate for abnormalities
    });
}

// Function to handle accelerometer data
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
    if (diff > maxDiff) {
        maxDiff = diff;
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
                      0x80 | ((charcode >> 6) & 0x3f),
                      0x80 | (charcode & 0x3f));
        }
        else { // surrogate pairs
            i++;
            charcode = 0x10000 + (((charcode & 0x3ff) << 10)
                      | (str.charCodeAt(i) & 0x3ff));
            utf8.push(0xf0 | (charcode >> 18),
                      0x80 | ((charcode >> 12) & 0x3f),
                      0x80 | ((charcode >> 6) & 0x3f),
                      0x80 | (charcode & 0x3f));
        }
    }
    return new Uint8Array(utf8);
}


// Function to start scanning and discover characteristics
function checkBluetoothConnection() {
    var status = NRF.getSecurityStatus();
    g.clear(); // Clear the screen

    // Set text color and font size
    g.setColor("#000000"); // Black color for text
    g.setFont("6x8"); // Font size
    g.setFontAlign(0, 0); // Center text alignment

    // Display connected address
    g.drawString('Connected Addr:', g.getWidth()/2, g.getHeight()/2 - 20);
    g.drawString(status.connected_addr, g.getWidth()/2, g.getHeight()/2);
    

    if (status.connected) {
        // Start scanning for devices with the connected address filter
        NRF.setScan(function(d) {
            g.clear(); // Clear the screen
            g.setColor("#000000"); // Black color for text
            g.setFont("6x8"); // Font size
            g.setFontAlign(0, 0); // Center text alignment
            
            // Display scanned device data
            var scanResult = JSON.stringify(d, null, 2);
            //g.drawString(scanResult, g.getWidth()/2, 10);
            
            // Log the scan result for debugging
            console.log('Scanned device:', d);
        });
      
      if(!status.bonded){
      NRF.startBonding().then(function() {
                    console.log('Bonding started');
                }).catch(function(error) {
                    console.log('Error starting bonding:', error);
                });
         
      }else{
      g.drawString('Resolved Addr:', g.getWidth()/2, g.getHeight()/2 + 40);
      g.drawString(NRF.resolveAddress(NRF.getSecurityStatus().connected_addr), g.getWidth()/2, g.getHeight()/2 +60);
      
        
      }
            
    } else {
        g.drawString('No device connected.', g.getWidth()/2, g.getHeight()/2);
    }

    // Log the status for debugging
    console.log('Bluetooth status:', status);
}







function concatenatePrefix(chunks, prefix) {
    return chunks.map(chunk => prefix + chunk);
}

// Function to advertise all stored data if available
function advertiseStoredData() {
    try {
        var jsonData = require('Storage').read('sensor_data.json');
        if (jsonData) {
            var storedData = JSON.parse(jsonData);
            if (storedData.length > 0) {
                var chunks = chunkString(JSON.stringify(storedData), 17); // Split data into smaller chunks
                chunks = concatenatePrefix(chunks, '*');

                function advertiseChunks(chunks) {
                    if (chunks.length === 0) {
                        return; // Exit if no chunks left
                    }

                    var dataChunk = chunks.shift(); // Get the next chunk
                    console.log('Advertising stored chunk:', dataChunk);

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
            }
        }
    } catch (e) {
        console.error('Error reading or advertising stored data:', e);
    }
}

// Initialize the watch
function init() {
    // Start the reset function to handle daily step count reset
    resetDailyStepCount();

    // Check if stored data is available and advertise it
    advertiseStoredData();

    // Start advertising and heart rate monitoring
    startAdvertising();
    startHeartRateMonitoring();
    //startClock(); // Start the clock face display

    // Periodically check Bluetooth connection status
    setInterval(checkBluetoothConnection, 10000); // Check every 10 seconds
}

// Run initialization
init();
