package com.punchthrough.blestarterappandroid

import android.bluetooth.*
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
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

    private val dataBuffer = StringBuilder()
    private val charBuffer = StringBuilder()
    private val charNoSpaceBuffer = StringBuilder()

    // Historical data storage
    private val recentDataPoints = mutableListOf<Entry>()
    private var currentIndex = 0

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
                    // Do not clear recent data when disconnected
                }
            } else {

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

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            val dataString = String(data, Charsets.UTF_8) // Convert the byte array to a UTF-8 string
            charBuffer.clear() // Clear the buffer for the next batch of data
            charNoSpaceBuffer.clear()

            synchronized(dataBuffer) {
                dataBuffer.append(dataString)

                var start = dataBuffer.indexOf("{")
                var end = dataBuffer.indexOf("}")

                while (start != -1 && end != -1 && start < end) {
                    var completeData = dataBuffer.substring(start, end + 1)

                    // Tokenize each element into individual characters and add to charBuffer
                    completeData.forEach { char ->
                        if (!char.isWhitespace()) {
                            charBuffer.append(char)
                        }
                    }

                    var finalString = charBuffer.toString()
                    finalString = finalString.replace("[JAdvertisingchunk:", "")
                    finalString = finalString.replace(">", "")
                    finalString = finalString.replace(" ", "")

                    finalString.forEach { char ->
                        if (char.isLetterOrDigit() || char in "{}\":,") {
                            charNoSpaceBuffer.append(char)
                        }
                    }
                    var actualFinalString = charNoSpaceBuffer.toString()
                    Log.d("DeviceDetailsActivity", "Actual Final String: $actualFinalString")

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

                    Log.d("DeviceDetailsActivity", "Actual Final String: $replacedString")

                    try {
                        val jsonObject = JSONObject(replacedString)

                        // Update the UI with the latest data
                        runOnUiThread {
                            recentDataTextView.text = formatRecentData(jsonObject)
                            if (jsonObject.has("HeartRate")) {
                                updateLineChart(jsonObject.getInt("HeartRate").toFloat()) // For example, tracking heart rate
                            } else {
                                Log.w("DeviceDetailsActivity", "No HeartRate value found in JSON")
                            }
                            sendDataToBackend(jsonObject) // Send data to backend
                        }
                    } catch (e: Exception) {
                        Log.e("DeviceDetailsActivity", "Failed to parse JSON: ${e.message}")
                    }

                    dataBuffer.delete(start, end + 1)
                    start = dataBuffer.indexOf("{")
                    end = dataBuffer.indexOf("}")
                }

                // Clean up any leading text before the next JSON object starts
                if (start == -1 && end != -1) {
                    dataBuffer.delete(0, end + 1)
                } else if (start != -1 && end == -1) {
                    // Remove text before the '{'
                    dataBuffer.delete(0, start)
                } else {

                }
            }
        }
    }

    private fun sendDataToBackend(data: JSONObject) {
        val client = OkHttpClient()

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = data.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("https://ultimate-dogfish-distinct.ngrok-free.app/api/sensor-data")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceDetailsActivity", "Failed to send data to backend", e)
                e.printStackTrace() // Print the full stack trace
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
        val set = LineDataSet(null, "Heart Rate") // For example, tracking heart rate
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

    private fun updateConnectionStatus(isConnected: Boolean) {
        if (isConnected) {
            connectionStatusTextView.text = "Bangle.js 2 SmartWatch Connected"
            connectionStatusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            connectionStatusTextView.text = "Bangle.js 2 SmartWatch Disconnected"
            connectionStatusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun formatRecentData(jsonObject: JSONObject): SpannableStringBuilder {
        val sb = SpannableStringBuilder()

        fun addBoldLabel(label: String, value: String) {
            val start = sb.length
            sb.append(label)
            sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
}