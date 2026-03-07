package com.example.modbus.master.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 将逻辑帧编码成真正的 Modbus RTU 字节流。
 *
 * <p>建议阅读顺序：先理解 ModbusRtuFrame 和 CRC，再看这个编码器。</p>
 * <p>输出格式：地址 + 功能码 + 数据区 + CRC16（低字节在前）。</p>
 */
public class ModbusRtuEncoder extends MessageToByteEncoder<ModbusRtuFrame> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ModbusRtuFrame msg, ByteBuf out) {
        int frameLength = 2 + msg.data().length + 2;
        byte[] frame = new byte[frameLength];
        frame[0] = msg.unitId();
        frame[1] = msg.functionCode();
        System.arraycopy(msg.data(), 0, frame, 2, msg.data().length);

        int crc = Crc16Modbus.calculate(frame, frameLength - 2);
        frame[frameLength - 2] = (byte) (crc & 0xFF);
        frame[frameLength - 1] = (byte) ((crc >> 8) & 0xFF);
        out.writeBytes(frame);
    }
}