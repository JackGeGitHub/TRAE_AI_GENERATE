package com.example.bleotacommunicate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.bleotacommunicate.proto.BleOtaProto;
import com.google.protobuf.InvalidProtocolBufferException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BleGattClient {
    private static final String TAG = "BleGattClient";
    private static final long SCAN_PERIOD = 10000;
    
    private Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    
    private BluetoothGattCharacteristic commandCharacteristic;
    private BluetoothGattCharacteristic payloadCharacteristic;
    private BluetoothGattCharacteristic responseCharacteristic;
    
    private GattClientListener listener;
    private Handler handler;
    
    private boolean isScanning = false;
    private Map<Integer, CommandCallback> pendingCommands = new HashMap<>();
    private AtomicInteger commandIdCounter = new AtomicInteger(0);
    
    public interface GattClientListener {
        void onScanResult(BluetoothDevice device);
        void onConnected(BluetoothDevice device);
        void onDisconnected();
        void onServicesDiscovered();
        void onDfuProgress(int percent);
        void onDfuComplete();
        void onLog(String message);
    }
    
    public interface CommandCallback {
        void onResponse(BleOtaProto.DFUCommandResponse response);
    }
    
    public BleGattClient(Context context, GattClientListener listener) {
        this.context = context;
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }
    
    public void startScan() {
        if (isScanning) {
            return;
        }
        
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new android.os.ParcelUuid(BleConstants.DFU_SERVICE_UUID))
                .build();
        filters.add(filter);
        
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        
        isScanning = true;
        bluetoothLeScanner.startScan(filters, settings, scanCallback);
        log("Scanning started");
        
        handler.postDelayed(() -> {
            if (isScanning) {
                stopScan();
            }
        }, SCAN_PERIOD);
    }
    
    public void stopScan() {
        if (isScanning) {
            isScanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
            log("Scanning stopped");
        }
    }
    
    public boolean connect(BluetoothDevice device) {
        stopScan();
        log("Connecting to: " + device.getAddress());
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
        return bluetoothGatt != null;
    }
    
    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
    }
    
    public void close() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        pendingCommands.clear();
    }
    
    public void startDfu() {
        int id = commandIdCounter.getAndIncrement();
        BleOtaProto.DFUCommand command = BleOtaProto.DFUCommand.newBuilder()
                .setId(id)
                .setDfuStart(BleOtaProto.DFUStartCommand.newBuilder().build())
                .build();
        
        sendCommand(command, response -> {
            log("DFU Start response: " + response.getErrorCode());
        });
    }
    
    public void sendFirmware(byte[] firmwareData) {
        new Thread(() -> {
            try {
                int totalChunks = (firmwareData.length + BleConstants.CHUNK_SIZE - 1) / BleConstants.CHUNK_SIZE;
                int totalPages = (firmwareData.length + BleConstants.PAGE_SIZE - 1) / BleConstants.PAGE_SIZE;
                
                log("Starting firmware transfer: " + firmwareData.length + " bytes");
                
                for (int page = 0; page < totalPages; page++) {
                    int pageStart = page * BleConstants.PAGE_SIZE;
                    int pageEnd = Math.min(pageStart + BleConstants.PAGE_SIZE, firmwareData.length);
                    
                    for (int chunk = 0; chunk < BleConstants.CHUNKS_PER_PAGE; chunk++) {
                        int chunkStart = pageStart + chunk * BleConstants.CHUNK_SIZE;
                        if (chunkStart >= pageEnd) {
                            break;
                        }
                        
                        int chunkEnd = Math.min(chunkStart + BleConstants.CHUNK_SIZE, pageEnd);
                        byte[] chunkData = Arrays.copyOfRange(firmwareData, chunkStart, chunkEnd);
                        
                        BleOtaProto.DFUChunk dfuChunk = BleOtaProto.DFUChunk.newBuilder()
                                .setNumber(chunk)
                                .setData(com.google.protobuf.ByteString.copyFrom(chunkData))
                                .build();
                        
                        sendPayload(dfuChunk);
                        
                        Thread.sleep(20);
                        
                        int progress = (page * BleConstants.CHUNKS_PER_PAGE + chunk) * 100 / totalChunks;
                        handler.post(() -> {
                            if (listener != null) {
                                listener.onDfuProgress(progress);
                            }
                        });
                    }
                    
                    int pageId = commandIdCounter.getAndIncrement();
                    BleOtaProto.DFUCommand pageCommand = BleOtaProto.DFUCommand.newBuilder()
                            .setId(pageId)
                            .setPageFinish(BleOtaProto.PageFinishCommand.newBuilder().build())
                            .build();
                    
                    sendCommand(pageCommand, response -> {
                        //log("Page " + page + " finish response: " + response.getErrorCode());
                    });
                    
                    Thread.sleep(100);
                }
                
                byte[] sha256 = calculateSha256(firmwareData);
                int finishId = commandIdCounter.getAndIncrement();
                BleOtaProto.DFUCommand finishCommand = BleOtaProto.DFUCommand.newBuilder()
                        .setId(finishId)
                        .setDfuFinish(BleOtaProto.DFUFinishCommand.newBuilder()
                                .setSize(firmwareData.length)
                                .setSha256Sum(com.google.protobuf.ByteString.copyFrom(sha256))
                                .build())
                        .build();
                
                sendCommand(finishCommand, response -> {
                    log("DFU Finish response: " + response.getErrorCode());
                    handler.post(() -> {
                        if (listener != null) {
                            listener.onDfuProgress(100);
                            listener.onDfuComplete();
                        }
                    });
                });
                
            } catch (InterruptedException e) {
                log("Transfer interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    public void resetDevice() {
        int id = commandIdCounter.getAndIncrement();
        BleOtaProto.DFUCommand command = BleOtaProto.DFUCommand.newBuilder()
                .setId(id)
                .setReset(BleOtaProto.ResetCommand.newBuilder().build())
                .build();
        
        sendCommand(command, response -> {
            log("Reset response: " + response.getErrorCode());
        });
    }
    
    private void sendCommand(BleOtaProto.DFUCommand command, CommandCallback callback) {
        if (commandCharacteristic == null) {
            log("Command characteristic not found");
            return;
        }
        
        pendingCommands.put(command.getId(), callback);
        commandCharacteristic.setValue(command.toByteArray());
        commandCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        bluetoothGatt.writeCharacteristic(commandCharacteristic);
    }
    
    private void sendPayload(BleOtaProto.DFUChunk chunk) {
        if (payloadCharacteristic == null) {
            log("Payload characteristic not found");
            return;
        }
        
        payloadCharacteristic.setValue(chunk.toByteArray());
        payloadCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        bluetoothGatt.writeCharacteristic(payloadCharacteristic);
    }
    
    private byte[] calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            log("Found device: " + device.getName() + " [" + device.getAddress() + "]");
            if (listener != null) {
                listener.onScanResult(device);
            }
        }
    };
    
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                log("Connected to GATT server");
                handler.post(() -> {
                    if (listener != null) {
                        listener.onConnected(gatt.getDevice());
                    }
                });
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                log("Disconnected from GATT server");
                handler.post(() -> {
                    if (listener != null) {
                        listener.onDisconnected();
                    }
                });
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(BleConstants.DFU_SERVICE_UUID);
                if (service != null) {
                    commandCharacteristic = service.getCharacteristic(BleConstants.COMMAND_CHARACTERISTIC_UUID);
                    payloadCharacteristic = service.getCharacteristic(BleConstants.PAYLOAD_CHARACTERISTIC_UUID);
                    responseCharacteristic = service.getCharacteristic(BleConstants.RESPONSE_CHARACTERISTIC_UUID);
                    
                    if (responseCharacteristic != null) {
                        gatt.setCharacteristicNotification(responseCharacteristic, true);
                        BluetoothGattDescriptor descriptor = responseCharacteristic.getDescriptor(
                                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                    }
                    
                    log("Services discovered successfully");
                    handler.post(() -> {
                        if (listener != null) {
                            listener.onServicesDiscovered();
                        }
                    });
                }
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            
            if (characteristic.getUuid().equals(BleConstants.RESPONSE_CHARACTERISTIC_UUID)) {
                try {
                    BleOtaProto.DFUCommandResponse response = BleOtaProto.DFUCommandResponse.parseFrom(characteristic.getValue());
                    CommandCallback callback = pendingCommands.remove(response.getId());
                    if (callback != null) {
                        callback.onResponse(response);
                    }
                } catch (InvalidProtocolBufferException e) {
                    log("Failed to parse response: " + e.getMessage());
                }
            }
        }
    };
    
    private void log(String message) {
        Log.d(TAG, message);
        if (listener != null) {
            listener.onLog(message);
        }
    }
}
