package com.punchthrough.blestarterappandroid

import android.bluetooth.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class DeviceDetailsActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private lateinit var device: BluetoothDevice

    private lateinit var connectionStatusTextView: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var recentDataTextView: TextView
    private lateinit var lineChart: LineChart

    // Buffers used to handle partial JSON messages
    private val dataBuffer = StringBuilder()
    private val charBuffer = StringBuilder()
    private val charNoSpaceBuffer = StringBuilder()

    // Historical data storage for the chart
    private val recentDataPoints = mutableListOf<Entry>()
    private var currentIndex = 0

    // Store the last valid JSON so it remains displayed until new valid data arrives
    private var lastReceivedData: JSONObject? = null

    // WebSocket-related variables for receiving (mock) prediction data
    private var webSocket: WebSocket? = null
    private val predictionHandler = Handler(Looper.getMainLooper())
    private val predictionRunnable = object : Runnable {
        override fun run() {
            if (isBleConnected) {
                // Generate a mock prediction message
                val mockPrediction = "Prediction: ${getRandomPrediction()}"
                showPredictionPopup(mockPrediction)
                // Schedule the next mock prediction in 10 seconds
                predictionHandler.postDelayed(this, 10000)
            }
        }
    }

    // Flag to track BLE connection status
    private var isBleConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_details)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        connectionStatusTextView = findViewById(R.id.connection_status)
        connectButton = findViewById(R.id.connect_button)
        disconnectButton = findViewById(R.id.disconnect_button)
        recentDataTextView = findViewById(R.id.recent_data_value)
        lineChart = findViewById(R.id.line_chart)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        device = bluetoothAdapter.getRemoteDevice(MainActivity.TARGET_DEVICE_ID)

        connectButton.setOnClickListener { connectToDevice() }
        disconnectButton.setOnClickListener { disconnectFromDevice() }

        setupLineChart()
        updateConnectionStatus(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectWebSocket()
        bluetoothGatt?.close()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun connectToDevice() {
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private fun disconnectFromDevice() {
        bluetoothGatt?.disconnect()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread {
                    updateConnectionStatus(true)
                }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    updateConnectionStatus(false)
                    // Keep showing last data even if disconnected
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (service in gatt.services) {
                    for (characteristic in service.characteristics) {
                        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                            gatt.setCharacteristicNotification(characteristic, true)
                            for (descriptor in characteristic.descriptors) {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    }
                }
            } else {
                Log.w("DeviceDetailsActivity", "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value
            val dataString = String(data, Charsets.UTF_8) // Convert byte array to UTF-8 string

            // Clear buffers for each new notification
            charBuffer.clear()
            charNoSpaceBuffer.clear()

            synchronized(dataBuffer) {
                dataBuffer.append(dataString)

                var start = dataBuffer.indexOf("{")
                var end = dataBuffer.indexOf("}")

                while (start != -1 && end != -1 && start < end) {
                    val completeData = dataBuffer.substring(start, end + 1)

                    // Clear temporary buffers for this iteration
                    charBuffer.clear()
                    charNoSpaceBuffer.clear()

                    // Collect non-whitespace characters
                    completeData.forEach { char ->
                        if (!char.isWhitespace()) {
                            charBuffer.append(char)
                        }
                    }

                    var finalString = charBuffer.toString()
                    // Remove extra markers/artifacts
                    finalString = finalString.replace("[JAdvertisingchunk:", "")
                        .replace(">", "")
                        .replace(" ", "")

                    // Filter out only JSON-friendly characters
                    finalString.forEach { char ->
                        if (char.isLetterOrDigit() || char in "{}\":,") {
                            charNoSpaceBuffer.append(char)
                        }
                    }

                    val actualFinalString = charNoSpaceBuffer.toString()
                    Log.d("DeviceDetailsActivity", "Actual Final String: $actualFinalString")

                    // Replace short keys with descriptive ones
                    val replacedString = actualFinalString
                        .replace("\"ts\"", "\"Timestamp\"")
                        .replace("\"bp\"", "\"Air Pressure\"")
                        .replace("\"bt\"", "\"Temperature\"")
                        .replace("\"ba\"", "\"Altitude\"")
                        .replace("\"hr\"", "\"HeartRate\"")
                        .replace("\"x\"", "\"AccelX\"")
                        .replace("\"y\"", "\"AccelY\"")
                        .replace("\"z\"", "\"AccelZ\"")
                        .replace("\"m\"", "\"Magnitude\"")
                        .replace("\"ad\"", "\"AccelDifference\"")
                        .replace("\"s\"", "\"StepCount\"")

                    try {
                        val jsonObject = JSONObject(replacedString)

                        // Check for valid data: if "Timestamp" is empty then consider this update invalid.
                        val timestamp = jsonObject.optString("Timestamp")
                        if (timestamp.isEmpty()) {
                            Log.d("DeviceDetailsActivity", "Received update with empty Timestamp; ignoring update.")
                        } else {
                            // Valid update: store it and update the UI.
                            lastReceivedData = jsonObject
                            runOnUiThread {
                                updateUI(jsonObject)
                                sendDataToBackend(jsonObject)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DeviceDetailsActivity", "Failed to parse JSON: ${e.message}")
                    }

                    // Remove the processed JSON from the buffer
                    dataBuffer.delete(start, end + 1)
                    start = dataBuffer.indexOf("{")
                    end = dataBuffer.indexOf("}")
                }

                // Clean up any unmatched leading text
                if (start == -1 && end != -1) {
                    dataBuffer.delete(0, end + 1)
                } else if (start != -1 && end == -1) {
                    dataBuffer.delete(0, start)
                } else {

                }
            }
        }
    }

    /**
     * Update the UI with new data.
     *
     * If the provided JSON has an empty "Timestamp", then it is considered invalid,
     * and we fall back to the last valid data.
     */
    private fun updateUI(jsonObject: JSONObject) {
        // Use lastReceivedData if the new jsonObject appears empty (based on "Timestamp")
        val timestamp = jsonObject.optString("Timestamp")
        val validData = if (timestamp.isEmpty() && lastReceivedData != null) {
            lastReceivedData
        } else {
            jsonObject
        }

        validData?.let {
            recentDataTextView.text = formatRecentData(it)
            if (it.has("HeartRate")) {
                updateLineChart(it.getInt("HeartRate").toFloat())
            } else {
                Log.w("DeviceDetailsActivity", "No HeartRate value found in JSON")
            }
        }
    }

    /**
     * Sends the JSON data to the backend using OkHttp.
     */
    private fun sendDataToBackend(data: JSONObject) {
        val client = OkHttpClient()
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = data.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("https://c69d-129-97-124-16.ngrok-free.app/api/sensor-data")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceDetailsActivity", "Failed to send data to backend", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.i("DeviceDetailsActivity", "Data sent to backend successfully")
                } else {
                    Log.e("DeviceDetailsActivity", "Failed to send data to backend: ${response.message}")
                    response.body?.string()?.let { responseBody ->
                        Log.e("DeviceDetailsActivity", "Response body: $responseBody")
                    }
                }
            }
        })
    }

    /**
     * Setup chart appearance and axes.
     */
    private fun setupLineChart() {
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setDrawGridBackground(false)
        lineChart.setPinchZoom(true)
        lineChart.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))

        val data = LineData()
        data.setValueTextColor(ContextCompat.getColor(this, android.R.color.black))
        lineChart.data = data

        val xl = lineChart.xAxis
        xl.textColor = ContextCompat.getColor(this, android.R.color.black)
        xl.setDrawGridLines(false)
        xl.setAvoidFirstLastClipping(true)
        xl.isEnabled = true

        val leftAxis = lineChart.axisLeft
        leftAxis.textColor = ContextCompat.getColor(this, android.R.color.black)
        leftAxis.setDrawGridLines(true)

        val rightAxis = lineChart.axisRight
        rightAxis.isEnabled = false

        val l = lineChart.legend
        l.form = Legend.LegendForm.LINE
        l.textColor = ContextCompat.getColor(this, android.R.color.black)
    }

    /**
     * Append a new data point to the chart and keep the last 10 points visible.
     */
    private fun updateLineChart(newDataPoint: Float) {
        val data = lineChart.data ?: return
        var set = data.getDataSetByIndex(0)

        if (set == null) {
            set = createSet()
            data.addDataSet(set)
        }

        // Maintain the past 10 data points
        if (recentDataPoints.size > 10) {
            recentDataPoints.removeAt(0)
        }
        recentDataPoints.add(Entry(currentIndex.toFloat(), newDataPoint))
        currentIndex++

        // Clear old entries, then re-add
        set.clear()
        for (entry in recentDataPoints) {
            set.addEntry(entry)
        }

        data.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.setVisibleXRangeMaximum(10f)
        lineChart.moveViewToX(data.entryCount.toFloat())
    }

    /**
     * Create a dataset for the chart (tracking HeartRate, for example).
     */
    private fun createSet(): LineDataSet {
        val set = LineDataSet(null, "Heart Rate")
        set.axisDependency = YAxis.AxisDependency.LEFT
        set.color = ContextCompat.getColor(this, android.R.color.holo_red_dark)
        set.setCircleColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        set.lineWidth = 2f
        set.circleRadius = 4f
        set.fillAlpha = 65
        set.fillColor = ContextCompat.getColor(this, android.R.color.holo_red_dark)
        set.highLightColor = ContextCompat.getColor(this, android.R.color.holo_red_dark)
        set.valueTextColor = ContextCompat.getColor(this, android.R.color.black)
        set.valueTextSize = 9f
        set.setDrawValues(false)
        return set
    }

    /**
     * Updates the connection status text and color.
     * Also manages the WebSocket connection for predictions.
     */
    private fun updateConnectionStatus(isConnected: Boolean) {
        isBleConnected = isConnected
        if (isConnected) {
            connectionStatusTextView.text = "Bangle.js 2 SmartWatch Connected"
            connectionStatusTextView.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
            // Open the WebSocket (and start simulation) only when BLE is connected
            connectWebSocket()
        } else {
            connectionStatusTextView.text = "Bangle.js 2 SmartWatch Disconnected"
            connectionStatusTextView.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
            disconnectWebSocket()
        }
    }

    /**
     * Format the most recent data in a nice multi-line layout with bold labels.
     */
    private fun formatRecentData(jsonObject: JSONObject): SpannableStringBuilder {
        val sb = SpannableStringBuilder()

        fun addBoldLabel(label: String, value: String) {
            val start = sb.length
            sb.append(label)
            sb.setSpan(
                StyleSpan(android.graphics.Typeface.BOLD),
                start,
                sb.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.append(value)
            sb.append("\n")
        }

        addBoldLabel("Most Recent Health Data:\n", "")
        addBoldLabel("Timestamp: ", jsonObject.optString("Timestamp"))
        addBoldLabel("Air Pressure: ", jsonObject.optString("Air Pressure"))
        addBoldLabel("Temperature: ", jsonObject.optString("Temperature"))
        addBoldLabel("Altitude: ", jsonObject.optString("Altitude"))
        addBoldLabel("Heart Rate: ", jsonObject.optString("HeartRate"))
        addBoldLabel("AccelX: ", jsonObject.optString("AccelX"))
        addBoldLabel("AccelY: ", jsonObject.optString("AccelY"))
        addBoldLabel("AccelZ: ", jsonObject.optString("AccelZ"))
        addBoldLabel("Magnitude: ", jsonObject.optString("Magnitude"))
        addBoldLabel("Accel Difference: ", jsonObject.optString("AccelDifference"))
        addBoldLabel("Step Count: ", jsonObject.optString("StepCount"))

        return sb
    }

    /**
     * Connects to the WebSocket to receive prediction data (mocked for prototype).
     */
    private fun connectWebSocket() {
        val request = Request.Builder().url("wss://example.com/mock-predictions").build()
        val client = OkHttpClient.Builder().build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("DeviceDetailsActivity", "WebSocket opened")
                // Start simulating prediction messages
                runOnUiThread {
                    predictionHandler.post(predictionRunnable)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("DeviceDetailsActivity", "Received prediction message: $text")
                if (isBleConnected) {
                    showPredictionPopup(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("DeviceDetailsActivity", "WebSocket failure: ${t.message}")
                // In case of failure, still simulate predictions for this prototype
                runOnUiThread {
                    predictionHandler.post(predictionRunnable)
                }
            }
        })
    }

    /**
     * Disconnects from the WebSocket and stops prediction simulation.
     */
    private fun disconnectWebSocket() {
        webSocket?.close(1000, "BLE disconnected")
        webSocket = null
        predictionHandler.removeCallbacks(predictionRunnable)
    }

    /**
     * Generates a random mock prediction.
     */
    private fun getRandomPrediction(): String {
        // Simulate a prediction message (for example, detecting a heart rate anomaly)
        val randomHeartRate = (60..100).random()
        return "Heart rate anomaly detected at $randomHeartRate BPM."
    }

    /**
     * Displays a popup with the prediction.
     */
    private fun showPredictionPopup(prediction: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("New Prediction")
                .setMessage(prediction)
                .setPositiveButton("Close") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
        }
    }
}
