package com.example.modbus.master.codec;

/**
 * Modbus RTU 的逻辑帧对象。
 *
 * <p>建议阅读顺序：先理解这个对象，再看编码器和解码器。</p>
 * <p>这里保存的是地址、功能码和数据区，CRC 不放在对象里，
 * 由编码器在真正写入串口前统一追加。</p>
 */
public record ModbusRtuFrame(byte unitId, byte functionCode, byte[] data) {
}