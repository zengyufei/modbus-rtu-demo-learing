package com.example.modbus.master.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 将逻辑帧编码成真正的 Modbus RTU 字节流。
 */
public class ModbusRtu编码器 extends MessageToByteEncoder<ModbusRtu帧> {
    @Override
    protected void encode(ChannelHandlerContext 上下文, ModbusRtu帧 消息, ByteBuf 输出) {
        int 帧长度 = 2 + 消息.数据区().length + 2;
        byte[] 完整帧 = new byte[帧长度];
        完整帧[0] = 消息.从站地址();
        完整帧[1] = 消息.功能码();
        System.arraycopy(消息.数据区(), 0, 完整帧, 2, 消息.数据区().length);

        int crc = ModbusCRC16计算器.计算(完整帧, 帧长度 - 2);
        完整帧[帧长度 - 2] = (byte) (crc & 0xFF);
        完整帧[帧长度 - 1] = (byte) ((crc >> 8) & 0xFF);
        输出.writeBytes(完整帧);
    }
}