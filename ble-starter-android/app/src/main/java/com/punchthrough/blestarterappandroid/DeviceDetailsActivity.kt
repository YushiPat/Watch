package com.punchthrough.blestarterappandroid

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.time.Instant
import com.punchthrough.blestarterappandroid.TwilioCallHelper

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

    // --------------------------------------------------
    // Fields for polling the risk assessment
    // --------------------------------------------------
    private val pollingHandler = Handler(Looper.getMainLooper())
    private var isPolling = false
    private var lastShownTimestamp: String? = null

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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // --------------------------------------------------
    // BLE connection and disconnection
    // --------------------------------------------------
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
                    // Start polling for risk assessments after connected
                    startPollingForPrediction()
                }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    updateConnectionStatus(false)
                    // Stop polling when disconnected
                    stopPollingForPrediction()
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
                    //DONT REMOVE THIS ELSE BLOCK
                }
            }
        }
    }

    // --------------------------------------------------
    // Update UI with new data
    // --------------------------------------------------
    private fun updateUI(jsonObject: JSONObject) {
        // Use lastReceivedData if the new jsonObject appears empty
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

    // --------------------------------------------------
    // Send sensor data to backend
    // --------------------------------------------------
    private fun sendDataToBackend(data: JSONObject) {
        val client = OkHttpClient()
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = data.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("https://4e4c-2620-101-f000-7c0-00-90b2.ngrok-free.app/api/sensor-data")
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

    // --------------------------------------------------
    // Setup chart appearance and axes
    // --------------------------------------------------
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

    // --------------------------------------------------
    // Update connection status text and color
    // --------------------------------------------------
    private fun updateConnectionStatus(isConnected: Boolean) {
        if (isConnected) {
            connectionStatusTextView.text = "Bangle.js 2 SmartWatch Connected"
            connectionStatusTextView.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
        } else {
            connectionStatusTextView.text = "Bangle.js 2 SmartWatch Disconnected"
            connectionStatusTextView.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
        }
    }

    // --------------------------------------------------
    // Format recent data in a multi-line layout with bold labels
    // --------------------------------------------------
    private fun formatRecentData(jsonObject: JSONObject): SpannableStringBuilder {
        val sb = SpannableStringBuilder()

        fun addBoldLabel(label: String, value: String) {
            val start = sb.length
            sb.append(label)
            sb.setSpan(
                StyleSpan(android.graphics.Typeface.BOLD),
                start,
                sb.length,
                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
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

    // --------------------------------------------------
    // Polling for the latest risk assessment
    // --------------------------------------------------
    private fun startPollingForPrediction() {
        if (!isPolling) {
            isPolling = true
            pollingHandler.post(pollingRunnable)
        }
    }

    private fun stopPollingForPrediction() {
        isPolling = false
        pollingHandler.removeCallbacks(pollingRunnable)
    }

    private val pollingRunnable = object : Runnable {
        override fun run() {
            if (isPolling) {
                fetchLatestRiskAssessment()
                // Schedule the next run in 1 second
                pollingHandler.postDelayed(this, 1000)
            }
        }
    }

    /**
     * Fetch the latest risk assessment using a standard OkHttpClient since the endpoint uses HTTP.
     */
    private fun fetchLatestRiskAssessment() {
        // Using default OkHttpClient since the endpoint is plain HTTP (no TLS)
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://4e4c-2620-101-f000-7c0-00-90b2.ngrok-free.app/api/risk-assessment/latest")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceDetailsActivity", "Failed to fetch latest risk assessment", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e("DeviceDetailsActivity", "Unsuccessful response: ${resp.message}")
                        return
                    }

                    val responseBody = resp.body?.string().orEmpty()
                    try {
                        // The server returns a JSON object
                        val assessment = JSONObject(responseBody)
                        Log.d("RiskAssessment", assessment.toString())

                        // Extract the timestamp from the nested "$date" field
                        val timestampObj = assessment.optJSONObject("timestamp")
                        val timestampString = timestampObj?.optString("\$date") ?: ""
                        if (timestampString.isNotEmpty()) {
                            val recordTimeMs = parseIsoTimeToMillis(timestampString)
                            val nowMs = System.currentTimeMillis()

                            // If within last 1 second, show popup (and not shown before)

                            // nowMs - recordTimeMs in 0..1000
                            if (nowMs - recordTimeMs in 0..1000) {
                                if (lastShownTimestamp != timestampString) {
                                    lastShownTimestamp = timestampString
                                    // Extract the alert message
                                    val alertObj = assessment.optJSONObject("alert")
                                    val alertMessage = alertObj?.optString("message")
                                        ?: "No alert message"

                                    runOnUiThread {
                                        showPredictionDialog(alertMessage)
                                    }
                                } else {
                                    //DONT REMOVE THIS ELSE BLOCK
                                }
                            } else {
                                //DONT REMOVE THIS ELSE BLOCK
                            }
                        } else {
                            //DONT REMOVE THIS ELSE BLOCK
                        }
                    } catch (ex: Exception) {
                        Log.e("DeviceDetailsActivity", "Error parsing risk assessment JSON", ex)
                    }
                }
            }
        })
    }

    // Helper to parse an ISO-8601 timestamp (e.g. "2025-03-03T22:57:55.242Z") to milliseconds
    private fun parseIsoTimeToMillis(isoTime: String): Long {
        return try {
            Instant.parse(isoTime).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    // Show a popup dialog with the prediction alert
    private fun showPredictionDialog(message: String) {
            // Automatically trigger the emergency call
            TwilioCallHelper.makeCall("+19056170150", message)

            AlertDialog.Builder(this)
                .setTitle("Risk Assessment Alert")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
    }
}
