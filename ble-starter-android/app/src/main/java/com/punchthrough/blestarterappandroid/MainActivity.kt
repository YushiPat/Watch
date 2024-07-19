/*
 * Copyright 2024 Punch Through Design LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.punchthrough.blestarterappandroid

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var deviceListAdapter: DeviceListAdapter
    private val deviceList: ArrayList<BluetoothDevice> = ArrayList()

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_LOCATION_PERMISSION = 2
        const val TARGET_DEVICE_ID = "E9:75:84:73:13:A2"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        val listView: ListView = findViewById(R.id.device_list)
        deviceListAdapter = DeviceListAdapter(this, deviceList)
        listView.adapter = deviceListAdapter

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

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
        } else {
            startScanning()
        }
    }

    private fun startScanning() {
        deviceList.clear()
        deviceListAdapter.notifyDataSetChanged()
        bluetoothLeScanner.startScan(leScanCallback)
    }

    private fun stopScanning() {
        bluetoothLeScanner.stopScan(leScanCallback)
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
            }

            if (!deviceList.contains(device)) {
                deviceList.add(device)
                deviceListAdapter.notifyDataSetChanged()
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    startScanning()
                } else {
                    // Permission denied, show a message to the user
                }
                return
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopScanning()
    }

    override fun onResume() {
        super.onResume()
        startScanning()
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