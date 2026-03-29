package com.example.bleotacommunicate;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private BleGattServer gattServer;
    private BleGattClient gattClient;
    
    private Switch modeSwitch;
    private Button startButton;
    private Button stopButton;
    private Button scanButton;
    private Button selectFileButton;
    private Button sendFirmwareButton;
    private Button resetButton;
    private ListView devicesListView;
    private ProgressBar progressBar;
    private TextView logTextView;
    private ScrollView logScrollView;
    
    private List<BluetoothDevice> foundDevices = new ArrayList<>();
    private ArrayAdapter<String> devicesAdapter;
    private BluetoothDevice selectedDevice;
    
    private byte[] testFirmwareData;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        checkPermissions();
        createTestFirmware();
    }
    
    private void initViews() {
        modeSwitch = findViewById(R.id.modeSwitch);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        scanButton = findViewById(R.id.scanButton);
        selectFileButton = findViewById(R.id.selectFileButton);
        sendFirmwareButton = findViewById(R.id.sendFirmwareButton);
        resetButton = findViewById(R.id.resetButton);
        devicesListView = findViewById(R.id.devicesListView);
        progressBar = findViewById(R.id.progressBar);
        logTextView = findViewById(R.id.logTextView);
        logScrollView = findViewById(R.id.logScrollView);
        
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        devicesListView.setAdapter(devicesAdapter);
        
        modeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> updateUI());
        
        startButton.setOnClickListener(v -> start());
        stopButton.setOnClickListener(v -> stop());
        scanButton.setOnClickListener(v -> startScan());
        selectFileButton.setOnClickListener(v -> selectFile());
        sendFirmwareButton.setOnClickListener(v -> sendFirmware());
        resetButton.setOnClickListener(v -> resetDevice());
        
        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            selectedDevice = foundDevices.get(position);
            log("Selected device: " + selectedDevice.getName() + " [" + selectedDevice.getAddress() + "]");
            if (gattClient != null) {
                gattClient.connect(selectedDevice);
            }
        });
        
        updateUI();
    }
    
    private void updateUI() {
        boolean isServer = !modeSwitch.isChecked();
        
        modeSwitch.setText(isServer ? "Server Mode" : "Client Mode");
        startButton.setText(isServer ? "Start Server" : "Start Client");
        
        scanButton.setVisibility(isServer ? View.GONE : View.VISIBLE);
        devicesListView.setVisibility(isServer ? View.GONE : View.VISIBLE);
        selectFileButton.setVisibility(isServer ? View.GONE : View.VISIBLE);
        sendFirmwareButton.setVisibility(isServer ? View.GONE : View.VISIBLE);
        resetButton.setVisibility(isServer ? View.GONE : View.VISIBLE);
    }
    
    private void checkPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
    }
    
    private void start() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        boolean isServer = !modeSwitch.isChecked();
        
        if (isServer) {
            startServer();
        } else {
            startClient();
        }
    }
    
    private void stop() {
        if (gattServer != null) {
            gattServer.stopServer();
            gattServer = null;
        }
        if (gattClient != null) {
            gattClient.close();
            gattClient = null;
        }
        log("Stopped");
    }
    
    private void startServer() {
        gattServer = new BleGattServer(this, serverListener);
        if (gattServer.startServer()) {
            log("Server started");
        }
    }
    
    private void startClient() {
        gattClient = new BleGattClient(this, clientListener);
        log("Client started");
    }
    
    private void startScan() {
        if (gattClient != null) {
            foundDevices.clear();
            devicesAdapter.clear();
            gattClient.startScan();
        }
    }
    
    private void selectFile() {
        log("File selection not implemented in demo - using test firmware");
    }
    
    private void sendFirmware() {
        if (gattClient != null && testFirmwareData != null) {
            gattClient.startDfu();
            new android.os.Handler().postDelayed(() -> {
                gattClient.sendFirmware(testFirmwareData);
            }, 500);
        }
    }
    
    private void resetDevice() {
        if (gattClient != null) {
            gattClient.resetDevice();
        }
    }
    
    private void createTestFirmware() {
        testFirmwareData = new byte[2048];
        for (int i = 0; i < testFirmwareData.length; i++) {
            testFirmwareData[i] = (byte) (i % 256);
        }
        log("Test firmware created (2KB)");
    }
    
    private void log(String message) {
        runOnUiThread(() -> {
            String currentText = logTextView.getText().toString();
            String newText = message + "\n" + currentText;
            if (newText.length() > 10000) {
                newText = newText.substring(0, 10000);
            }
            logTextView.setText(newText);
            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }
    
    private final BleGattServer.GattServerListener serverListener = new BleGattServer.GattServerListener() {
        @Override
        public void onDeviceConnected(BluetoothDevice device) {
            log("Device connected: " + device.getAddress());
        }
        
        @Override
        public void onDeviceDisconnected(BluetoothDevice device) {
            log("Device disconnected: " + device.getAddress());
        }
        
        @Override
        public void onDfuStart() {
            log("DFU started");
            runOnUiThread(() -> progressBar.setProgress(0));
        }
        
        @Override
        public void onPageReceived(byte[] pageData) {
            log("Page received: " + pageData.length + " bytes");
        }
        
        @Override
        public void onDfuComplete(byte[] firmwareData) {
            log("DFU complete! Total size: " + firmwareData.length + " bytes");
            runOnUiThread(() -> progressBar.setProgress(100));
        }
        
        @Override
        public void onReset() {
            log("Reset command received");
        }
        
        @Override
        public void onLog(String message) {
            log(message);
        }
    };
    
    private final BleGattClient.GattClientListener clientListener = new BleGattClient.GattClientListener() {
        @Override
        public void onScanResult(BluetoothDevice device) {
            if (!foundDevices.contains(device)) {
                foundDevices.add(device);
                String name = device.getName();
                if (name == null) name = "Unknown";
                devicesAdapter.add(name + " [" + device.getAddress() + "]");
            }
        }
        
        @Override
        public void onConnected(BluetoothDevice device) {
            log("Connected to: " + device.getAddress());
        }
        
        @Override
        public void onDisconnected() {
            log("Disconnected");
        }
        
        @Override
        public void onServicesDiscovered() {
            log("Services discovered - ready to send firmware");
        }
        
        @Override
        public void onDfuProgress(int percent) {
            runOnUiThread(() -> progressBar.setProgress(percent));
        }
        
        @Override
        public void onDfuComplete() {
            log("DFU transfer complete!");
        }
        
        @Override
        public void onLog(String message) {
            log(message);
        }
    };
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop();
    }
}
