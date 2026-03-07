package com.example.modbus.master.codec;

/**
 * Modbus RTU 使用的 CRC16 计算器。
 *
 * <p>建议阅读顺序：先看懂主从交互，再回头看这个协议细节类。</p>
 * <p>多项式为 0xA001，初始值为 0xFFFF。
 * 最终写入 RTU 帧时按小端顺序发送，也就是先低字节后高字节。</p>
 */
public final class Crc16Modbus {
    private Crc16Modbus() {
    }

    /**
     * 计算指定长度数据的 CRC16。
     *
     * @param data 原始字节数组
     * @param length 参与 CRC 计算的长度
     * @return 16 位 CRC 值
     */
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