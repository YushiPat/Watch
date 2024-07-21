package com.punchthrough.blestarterappandroid

import android.bluetooth.*
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class DeviceDetailsActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private lateinit var device: BluetoothDevice

    private lateinit var connectionStatusTextView: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var dataBoxTextView: TextView

    private val dataBuffer = StringBuilder()
    private val charBuffer = StringBuilder()
    private val charNoSpaceBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_details)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        connectionStatusTextView = findViewById(R.id.connection_status)
        connectButton = findViewById(R.id.connect_button)
        disconnectButton = findViewById(R.id.disconnect_button)
        dataBoxTextView = findViewById(R.id.data_box)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        device = bluetoothAdapter.getRemoteDevice(MainActivity.TARGET_DEVICE_ID)

        connectButton.setOnClickListener { connectToDevice() }
        disconnectButton.setOnClickListener { disconnectFromDevice() }

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
                    dataBoxTextView.text = "" // Clear data box when connected
                }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    updateConnectionStatus(false)
                    dataBoxTextView.text = "" // Clear data box when disconnected
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
//                    Log.d("DeviceDetailsActivity", "Final String: $finalString")

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

                    // Update the UI with the cleaned data
                    runOnUiThread {
                        dataBoxTextView.append("$replacedString\n")
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
                    // Do nothing if both start and end are -1
                }
            }
        }
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        if (isConnected) {
            connectionStatusTextView.text = "Connected"
            connectionStatusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            connectionStatusTextView.text = "Disconnected"
            connectionStatusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }
}
