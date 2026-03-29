package com.example.bleotacommunicate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import com.example.bleotacommunicate.proto.BleOtaProto;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Arrays;
import java.util.UUID;

public class BleGattServer {
    private static final String TAG = "BleGattServer";
    
    private Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    
    private BluetoothGattCharacteristic commandCharacteristic;
    private BluetoothGattCharacteristic payloadCharacteristic;
    private BluetoothGattCharacteristic responseCharacteristic;
    
    private GattServerListener listener;
    
    private byte[] firmwareData = new byte[0];
    private int receivedChunkCount = 0;
    private long receivedChunksBitmap = 0;
    
    public interface GattServerListener {
        void onDeviceConnected(BluetoothDevice device);
        void onDeviceDisconnected(BluetoothDevice device);
        void onDfuStart();
        void onPageReceived(byte[] pageData);
        void onDfuComplete(byte[] firmwareData);
        void onReset();
        void onLog(String message);
    }
    
    public BleGattServer(Context context, GattServerListener listener) {
        this.context = context;
        this.listener = listener;
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }
    
    public boolean startServer() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            log("Bluetooth is not enabled");
            return false;
        }
        
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        if (gattServer == null) {
            log("Failed to open GATT server");
            return false;
        }
        
        setupService();
        startAdvertising();
        log("GATT server started");
        return true;
    }
    
    public void stopServer() {
        stopAdvertising();
        if (gattServer != null) {
            gattServer.close();
            gattServer = null;
        }
        log("GATT server stopped");
    }
    
    private void setupService() {
        BluetoothGattService dfuService = new BluetoothGattService(
                BleConstants.DFU_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        
        commandCharacteristic = new BluetoothGattCharacteristic(
                BleConstants.COMMAND_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        
        payloadCharacteristic = new BluetoothGattCharacteristic(
                BleConstants.PAYLOAD_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        
        responseCharacteristic = new BluetoothGattCharacteristic(
                BleConstants.RESPONSE_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        responseCharacteristic.addDescriptor(descriptor);
        
        dfuService.addCharacteristic(commandCharacteristic);
        dfuService.addCharacteristic(payloadCharacteristic);
        dfuService.addCharacteristic(responseCharacteristic);
        
        gattServer.addService(dfuService);
    }
    
    private void startAdvertising() {
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            log("BLE advertising not supported");
            return;
        }
        
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();
        
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(BleConstants.DFU_SERVICE_UUID))
                .build();
        
        advertiser.startAdvertising(settings, data, advertiseCallback);
    }
    
    private void stopAdvertising() {
        if (advertiser != null) {
            advertiser.stopAdvertising(advertiseCallback);
            advertiser = null;
        }
    }
    
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                log("Device connected: " + device.getAddress());
                if (listener != null) {
                    listener.onDeviceConnected(device);
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                log("Device disconnected: " + device.getAddress());
                if (listener != null) {
                    listener.onDeviceDisconnected(device);
                }
            }
        }
        
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            
            UUID charUuid = characteristic.getUuid();
            
            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            }
            
            if (charUuid.equals(BleConstants.COMMAND_CHARACTERISTIC_UUID)) {
                handleCommand(device, value);
            } else if (charUuid.equals(BleConstants.PAYLOAD_CHARACTERISTIC_UUID)) {
                handlePayload(value);
            }
        }
        
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            }
        }
    };
    
    private void handleCommand(BluetoothDevice device, byte[] data) {
        try {
            BleOtaProto.DFUCommand command = BleOtaProto.DFUCommand.parseFrom(data);
            int commandId = command.getId();
            
            BleOtaProto.DFUCommandResponse.Builder responseBuilder = BleOtaProto.DFUCommandResponse.newBuilder()
                    .setId(commandId)
                    .setErrorCode(BleOtaProto.DFUErrorCode.SUCCESS);
            
            switch (command.getCommandCase()) {
                case DFU_START:
                    log("Received DFU_START command");
                    firmwareData = new byte[0];
                    receivedChunkCount = 0;
                    receivedChunksBitmap = 0;
                    if (listener != null) {
                        listener.onDfuStart();
                    }
                    break;
                    
                case PAGE_FINISH:
                    log("Received PAGE_FINISH command");
                    if (receivedChunkCount == BleConstants.CHUNKS_PER_PAGE) {
                        byte[] pageData = Arrays.copyOf(firmwareData, firmwareData.length);
                        if (listener != null) {
                            listener.onPageReceived(pageData);
                        }
                    } else {
                        ///responseBuilder.setChunkBits(longToBytes(receivedChunksBitmap));
                    }
                    receivedChunkCount = 0;
                    receivedChunksBitmap = 0;
                    break;
                    
                case DFU_FINISH:
                    log("Received DFU_FINISH command");
                    if (listener != null) {
                        listener.onDfuComplete(firmwareData);
                    }
                    break;
                    
                case RESET:
                    log("Received RESET command");
                    if (listener != null) {
                        listener.onReset();
                    }
                    break;
                    
                default:
                    log("Unknown command");
                    responseBuilder.setErrorCode(BleOtaProto.DFUErrorCode.FAILURE);
            }
            
            sendResponse(device, responseBuilder.build());
            
        } catch (InvalidProtocolBufferException e) {
            log("Failed to parse command: " + e.getMessage());
        }
    }
    
    private void handlePayload(byte[] data) {
        try {
            BleOtaProto.DFUChunk chunk = BleOtaProto.DFUChunk.parseFrom(data);
            int chunkNumber = chunk.getNumber();
            byte[] chunkData = chunk.getData().toByteArray();
            
            log("Received chunk: " + chunkNumber);
            
            if ((receivedChunksBitmap & (1L << chunkNumber)) == 0) {
                int startPos = chunkNumber * BleConstants.CHUNK_SIZE;
                if (firmwareData.length < startPos + chunkData.length) {
                    firmwareData = Arrays.copyOf(firmwareData, startPos + chunkData.length);
                }
                System.arraycopy(chunkData, 0, firmwareData, startPos, chunkData.length);
                receivedChunksBitmap |= (1L << chunkNumber);
                receivedChunkCount++;
            }
            
        } catch (InvalidProtocolBufferException e) {
            log("Failed to parse chunk: " + e.getMessage());
        }
    }
    
    private void sendResponse(BluetoothDevice device, BleOtaProto.DFUCommandResponse response) {
        responseCharacteristic.setValue(response.toByteArray());
        gattServer.notifyCharacteristicChanged(device, responseCharacteristic, false);
    }
    
    private byte[] longToBytes(long value) {
        byte[] bytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return bytes;
    }
    
    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            log("Advertising started");
        }
        
        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            log("Advertising failed: " + errorCode);
        }
    };
    
    private void log(String message) {
        Log.d(TAG, message);
        if (listener != null) {
            listener.onLog(message);
        }
    }
}
