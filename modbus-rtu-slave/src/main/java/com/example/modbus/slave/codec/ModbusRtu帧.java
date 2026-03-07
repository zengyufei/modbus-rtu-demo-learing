package com.example.modbus.slave.codec;

/**
 * 从站侧使用的逻辑 RTU 帧对象。
 */
public record ModbusRtu帧(byte 从站地址, byte 功能码, byte[] 数据区) {
}