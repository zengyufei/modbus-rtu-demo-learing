package com.example.modbus.slave.codec;

/**
 * 从站侧 CRC16 计算器。
 *
 * <p>建议阅读顺序：主从 CRC 逻辑一致，建议与主站版对照着看。</p>
 */
public final class Crc16Modbus {
    private Crc16Modbus() {
    }

    public static int calculate(byte[] data, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= data[i] & 0xFF;
            for (int j = 0; j < 8; j++) {
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