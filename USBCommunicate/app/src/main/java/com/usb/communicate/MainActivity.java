package com.usb.communicate;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "USBCommunicate";
    private static final String ACTION_USB_PERMISSION = "com.usb.communicate.USB_PERMISSION";
    
    private static final int REQ_TYPE_HOST_TO_DEVICE = 0x21;
    private static final int REQ_TYPE_DEVICE_TO_HOST = 0xA1;
    private static final int REQ_SET_LINE_CODING = 0x20;
    private static final int REQ_GET_LINE_CODING = 0x21;
    
    private UsbManager usbManager;
    private UsbDevice usbDevice;
    private UsbDeviceConnection usbConnection;
    private UsbInterface usbInterface;
    private UsbEndpoint bulkInEndpoint;
    private UsbEndpoint bulkOutEndpoint;
    
    private boolean isHostMode = true;
    private boolean isConnected = false;
    
    private TextView tvStatus;
    private TextView tvLog;
    private Button btnConnect;
    private Button btnSendControl;
    private Button btnSendBulk;
    private Button btnClearLog;
    private RadioGroup radioGroupMode;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            openDevice(device);
                        }
                    } else {
                        log("Permission denied for USB device");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                log("USB device attached");
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    requestUsbPermission(device);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                log("USB device detached");
                closeDevice();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initUsb();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        btnConnect = findViewById(R.id.btn_connect);
        btnSendControl = findViewById(R.id.btn_send_control);
        btnSendBulk = findViewById(R.id.btn_send_bulk);
        btnClearLog = findViewById(R.id.btn_clear_log);
        radioGroupMode = findViewById(R.id.radio_group_mode);

        btnConnect.setOnClickListener(v -> toggleConnection());
        btnSendControl.setOnClickListener(v -> sendControlCommand());
        btnSendBulk.setOnClickListener(v -> sendBulkCommand());
        btnClearLog.setOnClickListener(v -> clearLog());

        radioGroupMode.setOnCheckedChangeListener((group, checkedId) -> {
            isHostMode = (checkedId == R.id.radio_host);
            log("Switched to " + (isHostMode ? "HOST" : "SLAVE") + " mode");
            closeDevice();
        });

        updateUI();
    }

    private void initUsb() {
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
        
        log("USB Manager initialized");
    }

    private void toggleConnection() {
        if (isConnected) {
            closeDevice();
        } else {
            enumerateDevices();
        }
    }

    private void enumerateDevices() {
        if (!isHostMode) {
            log("SLAVE mode: Waiting for HOST connection...");
            tvStatus.setText("Status: SLAVE mode - Waiting for HOST");
            return;
        }

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        log("Found " + deviceList.size() + " USB device(s)");

        if (deviceList.isEmpty()) {
            Toast.makeText(this, "No USB devices found", Toast.LENGTH_SHORT).show();
            return;
        }

        for (Map.Entry<String, UsbDevice> entry : deviceList.entrySet()) {
            UsbDevice device = entry.getValue();
            log("Device: " + device.getDeviceName() + 
                ", VID: 0x" + Integer.toHexString(device.getVendorId()) + 
                ", PID: 0x" + Integer.toHexString(device.getProductId()));
            requestUsbPermission(device);
            break;
        }
    }

    private void requestUsbPermission(UsbDevice device) {
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                this, 0, new Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE);
        usbManager.requestPermission(device, permissionIntent);
        log("Requesting USB permission...");
    }

    private void openDevice(UsbDevice device) {
        usbDevice = device;
        usbConnection = usbManager.openDevice(device);
        
        if (usbConnection == null) {
            log("Failed to open USB device");
            return;
        }

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            usbInterface = intf;
            
            if (usbConnection.claimInterface(intf, true)) {
                log("Claimed interface " + i);
                
                for (int j = 0; j < intf.getEndpointCount(); j++) {
                    UsbEndpoint ep = intf.getEndpoint(j);
                    if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                            bulkInEndpoint = ep;
                            log("Found BULK IN endpoint");
                        } else {
                            bulkOutEndpoint = ep;
                            log("Found BULK OUT endpoint");
                        }
                    }
                }
                break;
            }
        }

        if (usbInterface != null && bulkInEndpoint != null && bulkOutEndpoint != null) {
            isConnected = true;
            log("USB device connected successfully!");
        } else {
            log("Failed to find required endpoints");
            closeDevice();
        }
        
        updateUI();
    }

    private void closeDevice() {
        if (usbConnection != null) {
            if (usbInterface != null) {
                usbConnection.releaseInterface(usbInterface);
            }
            usbConnection.close();
        }
        usbDevice = null;
        usbConnection = null;
        usbInterface = null;
        bulkInEndpoint = null;
        bulkOutEndpoint = null;
        isConnected = false;
        log("Device disconnected");
        updateUI();
    }

    private void sendControlCommand() {
        if (!isHostMode || !isConnected) {
            Toast.makeText(this, "Must be in HOST mode and connected", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                byte[] lineCoding = new byte[7];
                int baudRate = 115200;
                lineCoding[0] = (byte) (baudRate & 0xFF);
                lineCoding[1] = (byte) ((baudRate >> 8) & 0xFF);
                lineCoding[2] = (byte) ((baudRate >> 16) & 0xFF);
                lineCoding[3] = (byte) ((baudRate >> 24) & 0xFF);
                lineCoding[4] = 0;
                lineCoding[5] = 0;
                lineCoding[6] = 8;

                log("Sending control transfer: Set baud rate to 115200, 8 data bits");
                
                int result = usbConnection.controlTransfer(
                        REQ_TYPE_HOST_TO_DEVICE,
                        REQ_SET_LINE_CODING,
                        0,
                        0,
                        lineCoding,
                        lineCoding.length,
                        1000);

                if (result >= 0) {
                    log("Control transfer sent successfully. Result: " + result);
                    
                    log("Waiting 1 second before reading response...");
                    Thread.sleep(1000);
                    
                    byte[] response = new byte[64];
                    int readResult = usbConnection.controlTransfer(
                            REQ_TYPE_DEVICE_TO_HOST,
                            REQ_GET_LINE_CODING,
                            0,
                            0,
                            response,
                            response.length,
                            1000);
                    
                    if (readResult >= 0) {
                        String responseStr = bytesToHex(response, readResult);
                        log("Received control response: " + responseStr);
                    } else {
                        log("Failed to read control response. Error: " + readResult);
                    }
                } else {
                    log("Control transfer failed. Error: " + result);
                }
            } catch (Exception e) {
                log("Error in control transfer: " + e.getMessage());
            }
        }).start();
    }

    private void sendBulkCommand() {
        if (!isHostMode || !isConnected) {
            Toast.makeText(this, "Must be in HOST mode and connected", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String command = "nvs set nvs app_mode 1\r";
                byte[] buffer = command.getBytes();
                
                log("Sending bulk transfer: \"" + command + "\"");
                
                int bytesWritten = usbConnection.bulkTransfer(
                        bulkOutEndpoint,
                        buffer,
                        buffer.length,
                        1000);

                if (bytesWritten >= 0) {
                    log("Bulk transfer sent successfully. Bytes written: " + bytesWritten);
                    
                    log("Waiting 1 second before reading response...");
                    Thread.sleep(1000);
                    
                    byte[] readBuffer = new byte[1024];
                    int bytesRead = usbConnection.bulkTransfer(
                            bulkInEndpoint,
                            readBuffer,
                            readBuffer.length,
                            1000);
                    
                    if (bytesRead >= 0) {
                        String received = new String(readBuffer, 0, bytesRead);
                        log("Received bulk response (" + bytesRead + " bytes): " + received);
                    } else {
                        log("Failed to read bulk response. Error: " + bytesRead);
                    }
                } else {
                    log("Bulk transfer failed. Error: " + bytesWritten);
                }
            } catch (Exception e) {
                log("Error in bulk transfer: " + e.getMessage());
            }
        }).start();
    }

    private void log(String message) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = "[" + timestamp + "] " + message + "\n";
        
        handler.post(() -> {
            tvLog.append(logMessage);
            Log.d(TAG, message);
        });
    }

    private void clearLog() {
        tvLog.setText("");
        log("Log cleared");
    }

    private void updateUI() {
        btnConnect.setText(isConnected ? "Disconnect" : "Connect Device");
        tvStatus.setText("Status: " + (isConnected ? "Connected" : "Not connected"));
        btnSendControl.setEnabled(isHostMode && isConnected);
        btnSendBulk.setEnabled(isHostMode && isConnected);
    }

    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        closeDevice();
    }
}

