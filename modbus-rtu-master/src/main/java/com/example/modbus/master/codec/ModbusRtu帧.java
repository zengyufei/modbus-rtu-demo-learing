package com.example.modbus.master.codec;

/**
 * Modbus RTU 的逻辑帧对象。
 */
public record ModbusRtu帧(byte 从站地址, byte 功能码, byte[] 数据区) {
}