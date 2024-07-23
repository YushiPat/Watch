package com.punchthrough.blestarterappandroid

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var deviceListAdapter: DeviceListAdapter
    private lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser
    private val deviceList: ArrayList<BluetoothDevice> = ArrayList()
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i("MainActivity", "LE Advertise Started.")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("MainActivity", "LE Advertise Failed: $errorCode")
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_LOCATION_PERMISSION = 2
        const val TARGET_DEVICE_ID = "E9:75:84:73:13:A2"
        var userAge: Int = 60
        var userGender: String = "M"
    }

    var userGenderBinary = if (userGender == "M") 0 else 1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser

        val listView: ListView = findViewById(R.id.device_list)
        deviceListAdapter = DeviceListAdapter(this, deviceList)
        listView.adapter = deviceListAdapter

        val formButton: Button = findViewById(R.id.form_button)
        formButton.setOnClickListener {
            val intent = Intent(this, MedicalFormActivity::class.java)
            startActivity(intent)
        }

        val refreshButton: Button = findViewById(R.id.refresh_button)
        refreshButton.setOnClickListener {
            refreshDeviceList()
        }

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedDevice = deviceList[position]
            if (selectedDevice.address == TARGET_DEVICE_ID) {
                val intent = Intent(this, DeviceDetailsActivity::class.java)
                startActivity(intent)
            }
        }

        checkPermissions()
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")))
            .addServiceData(ParcelUuid(UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")), byteArrayOf(
                userAge.toByte(), userGenderBinary.toByte()))
            .build()

        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private fun stopAdvertising() {
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_LOCATION_PERMISSION)
        } else {
            startScanning()
            startAdvertising()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startScanning()
                } else {
                    // Permission denied, show a message to the user
                    Log.e("MainActivity", "Permission denied")
                }
            }
        }
    }

    private fun startScanning() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            deviceList.clear()
            deviceListAdapter.notifyDataSetChanged()
            bluetoothLeScanner.startScan(leScanCallback)
        } else {
            Log.e("MainActivity", "Bluetooth scan permission not granted")
        }
    }

    private fun stopScanning() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothLeScanner.stopScan(leScanCallback)
        } else {
            Log.e("MainActivity", "Bluetooth scan permission not granted")
        }
    }

    private fun refreshDeviceList() {
        stopScanning()
        startScanning()
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device: BluetoothDevice = result.device

            if (device.address == TARGET_DEVICE_ID) {
                stopScanning()
                Log.i("MainActivity", "Target device found: ${device.name ?: "Unknown Device"} (${device.address})")
                if (!deviceList.contains(device)) {
                    deviceList.clear()
                    deviceList.add(device)
                    deviceListAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                onScanResult(0, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("MainActivity", "BLE Scan Failed with code $errorCode")
        }
    }

    override fun onPause() {
        super.onPause()
        stopScanning()
        stopAdvertising()
    }

    override fun onResume() {
        super.onResume()
        startScanning()
        startAdvertising()
    }

    private inner class DeviceListAdapter(context: Activity, private val devices: ArrayList<BluetoothDevice>) : ArrayAdapter<BluetoothDevice>(context, 0, devices) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val device = devices[position]
            val view = convertView ?: layoutInflater.inflate(R.layout.device_list_item, parent, false)
            val deviceNameTextView: TextView = view.findViewById(R.id.device_name)
            val deviceAddressTextView: TextView = view.findViewById(R.id.device_address)

            deviceNameTextView.text = device.name ?: "Unknown Device"
            deviceAddressTextView.text = device.address

            if (device.address == TARGET_DEVICE_ID) {
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.yellow_highlight))
            } else {
                view.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            }

            return view
        }
    }
}
