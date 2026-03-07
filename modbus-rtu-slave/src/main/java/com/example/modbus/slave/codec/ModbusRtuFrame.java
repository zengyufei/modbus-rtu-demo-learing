package com.example.modbus.slave.codec;

/**
 * 从站侧使用的逻辑 RTU 帧对象。
 *
 * <p>建议阅读顺序：先理解这个对象，再去看编码器和业务处理器。</p>
 */
public record ModbusRtuFrame(byte unitId, byte functionCode, byte[] data) {
}