package com.example.modbus.slave.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 从站 RTU 编码器。
 *
 * <p>建议阅读顺序：看完 SlaveHandler 里构造的逻辑帧后，再来看这里如何编码回串口字节。</p>
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