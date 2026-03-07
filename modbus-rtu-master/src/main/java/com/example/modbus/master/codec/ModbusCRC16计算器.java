package com.example.modbus.master.codec;

/**
 * Modbus RTU 使用的 CRC16 计算器。
 */
public final class ModbusCRC16计算器 {
    private ModbusCRC16计算器() {
    }

    public static int 计算(byte[] 原始字节数组, int 参与计算长度) {
        int crc = 0xFFFF;
        for (int 字节下标 = 0; 字节下标 < 参与计算长度; 字节下标++) {
            crc ^= 原始字节数组[字节下标] & 0xFF;
            for (int 位下标 = 0; 位下标 < 8; 位下标++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }
}