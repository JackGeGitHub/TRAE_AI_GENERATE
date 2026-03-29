package com.example.bleotacommunicate;

import java.util.UUID;

public class BleConstants {
    public static final UUID DFU_SERVICE_UUID = UUID.fromString("0000fdf5-0000-1000-8000-00805f9b34fb");
    public static final UUID COMMAND_CHARACTERISTIC_UUID = UUID.fromString("00002a2a-0000-1000-8000-00805f9b34fb");
    public static final UUID PAYLOAD_CHARACTERISTIC_UUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb");
    public static final UUID RESPONSE_CHARACTERISTIC_UUID = UUID.fromString("00002a2c-0000-1000-8000-00805f9b34fb");
    
    public static final String DEVICE_NAME = "BleOTACommunicate";
    
    public static final int CHUNK_SIZE = 16;
    public static final int PAGE_SIZE = 1024;
    public static final int CHUNKS_PER_PAGE = 64;
}
