import asyncio
from bleak import BleakScanner, BleakClient
import json

# Dictionary to accumulate data chunks for each device
data_chunks = {}

# Notification handler to accumulate and parse received data
def notification_handler(device_name):
    def handler(sender, data):
        global data_chunks
        device_key = f"{device_name}_{sender}"
        if device_key not in data_chunks:
            data_chunks[device_key] = bytearray()

        # Accumulate data chunks
        data_chunks[device_key] += data

        # Attempt to decode the accumulated bytearray to string
        try:
            data_str = data_chunks[device_key].decode('utf-8')
            
            # Check if the accumulated data contains a complete JSON object
            # assuming it starts with '{' and ends with '}'
            if data_str.startswith('{') and data_str.endswith('}'):
                json_data = json.loads(data_str)
                print(f"Notification from {device_name} ({sender}): {json.dumps(json_data, indent=2)}")
                data_chunks[device_key] = bytearray()  # Reset the buffer
            else:
                # Handle incomplete JSON data
                print(f"Accumulating data for {device_name} ({sender}): {data_str}")
                
        except Exception as e:
            print(f"Failed to parse data chunk: {e}")
    return handler

async def main():
    # Scan for devices
    print("Scanning for BLE devices...")
    devices = await BleakScanner.discover()
    if not devices:
        print("No BLE devices found.")
        return

    # Print discovered devices
    print("Discovered devices:")
    target_device = None
    target_name = "Bangle.js 13a2"
    target_address = "D37A0476-386B-481A-9139-A24E571BE199"
    
    for i, device in enumerate(devices):
        device_name = device.name or "Unknown Device"
        print(f"{i}: {device.address} ({device_name})")
        if device.address == target_address:
            target_device = device

    if target_device is None:
        print("Target device not found.")
        return

    print(f"Connecting to {target_device.address} ({target_device.name})")

    # Connect to the target device
    async with BleakClient(target_device.address) as client:
        if client.is_connected:
            print(f"Connected to {target_device.address} ({target_device.name})")

            # Get all services and characteristics
            services = await client.get_services()
            for service in services:
                for char in service.characteristics:
                    # Subscribe to notifications if the characteristic supports it
                    if "notify" in char.properties:
                        print(f"Subscribing to characteristic {char.uuid}")
                        await client.start_notify(char.uuid, notification_handler(target_device.name))

            # Keep the connection open to receive notifications indefinitely
            print("Receiving notifications. Press Ctrl+C to stop.")
            try:
                while True:
                    await asyncio.sleep(1)
            except KeyboardInterrupt:
                print("Stopping notifications and disconnecting...")

            # Stop notifications
            for service in services:
                for char in service.characteristics:
                    if "notify" in char.properties:
                        await client.stop_notify(char.uuid)

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())
