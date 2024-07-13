// Start continuous advertising
function startAdvertising() {
  Bangle.setBarometerPower(1); // Turn on barometer

  Bangle.getPressure().then(function(pressure) {
    var barometerData = { type: 'Barometer', timestamp: new Date().toISOString(), data: pressure };
    console.log('Barometer data:', barometerData);

    // Convert the barometer data to a JSON string
    var manufacturerDataString = JSON.stringify(barometerData.data.temperature);
    
    console.log('manufacturerDataString:', manufacturerDataString);

    // Set the advertisement with the barometer data
    NRF.setAdvertising({}, {
      showName: true,
      manufacturer: 0x0590,
      manufacturerData: manufacturerDataString
    });
  }).catch(function(error) {
    console.error('Error getting barometer data:', error);
  });
}

// Set up continuous advertising
startAdvertising(); // Start immediately
setInterval(startAdvertising, 2000); // Repeat every 2 seconds
