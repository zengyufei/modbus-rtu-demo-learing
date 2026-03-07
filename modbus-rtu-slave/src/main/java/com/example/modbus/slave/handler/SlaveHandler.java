package com.example.modbus.slave.handler;

import com.example.modbus.slave.codec.ModbusRtuFrame;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Arrays;

/**
 * 从站业务处理器。
 *
 * <p>本 demo 只实现保持寄存器相关的三个功能码：
 * 0x03 读保持寄存器
 * 0x06 写单个保持寄存器
 * 0x10 写多个保持寄存器</p>
 */
// 学习顺序建议：这是从站教学核心类，建议与 MasterHandler 对照阅读。
public class SlaveHandler extends SimpleChannelInboundHandler<ModbusRtuFrame> {
    // 保持寄存器表，既是业务数据源，也是教学观察点。
    private static final int[] HOLDING_REGISTERS = {10, 20, 30, 40, 50, 60};

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("Slave serial port opened, waiting for master request.");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ModbusRtuFrame msg) {
        int functionCode = msg.functionCode() & 0xFF;
        if (functionCode == 0x03) {
            handleReadHoldingRegisters(ctx, msg);
            return;
        }
        if (functionCode == 0x06) {
            handleWriteSingleRegister(ctx, msg);
            return;
        }
        if (functionCode == 0x10) {
            handleWriteMultipleRegisters(ctx, msg);
            return;
        }
        sendException(ctx, msg.unitId(), msg.functionCode(), (byte) 0x01);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private void handleReadHoldingRegisters(ChannelHandlerContext ctx, ModbusRtuFrame msg) {
        int startAddress = ((msg.data()[0] & 0xFF) << 8) | (msg.data()[1] & 0xFF);
        int quantity = ((msg.data()[2] & 0xFF) << 8) | (msg.data()[3] & 0xFF);
        System.out.printf("Slave <- Read Holding Registers start=%d quantity=%d%n", startAddress, quantity);

        if (quantity <= 0 || startAddress < 0 || startAddress + quantity > HOLDING_REGISTERS.length) {
            sendException(ctx, msg.unitId(), msg.functionCode(), (byte) 0x02);
            return;
        }

        // 示例响应帧（当前 register[0..3] = 10,20,30,40）：
        // 01 03 08 00 0A 00 14 00 1E 00 28 6F CC
        // 0x03 响应数据区 = byteCount + N 个 16 位寄存器值。
        byte[] responseData = new byte[1 + quantity * 2];
        responseData[0] = (byte) (quantity * 2);
        for (int i = 0; i < quantity; i++) {
            int value = HOLDING_REGISTERS[startAddress + i];
            responseData[1 + i * 2] = (byte) (value >> 8);
            responseData[2 + i * 2] = (byte) value;
        }
        ctx.writeAndFlush(new ModbusRtuFrame(msg.unitId(), msg.functionCode(), responseData));
    }

    private void handleWriteSingleRegister(ChannelHandlerContext ctx, ModbusRtuFrame msg) {
        int address = ((msg.data()[0] & 0xFF) << 8) | (msg.data()[1] & 0xFF);
        int value = ((msg.data()[2] & 0xFF) << 8) | (msg.data()[3] & 0xFF);
        System.out.printf("Slave <- Write Single Register address=%d value=%d%n", address, value);

        if (address < 0 || address >= HOLDING_REGISTERS.length) {
            sendException(ctx, msg.unitId(), msg.functionCode(), (byte) 0x02);
            return;
        }

        // 示例请求/响应帧：
        // 01 06 00 01 04 D2 5A 97
        // 0x06 的响应会原样回显地址和值。
        HOLDING_REGISTERS[address] = value;
        System.out.println("Holding registers => " + Arrays.toString(HOLDING_REGISTERS));
        ctx.writeAndFlush(new ModbusRtuFrame(msg.unitId(), msg.functionCode(), msg.data()));
    }

    private void handleWriteMultipleRegisters(ChannelHandlerContext ctx, ModbusRtuFrame msg) {
        int startAddress = ((msg.data()[0] & 0xFF) << 8) | (msg.data()[1] & 0xFF);
        int quantity = ((msg.data()[2] & 0xFF) << 8) | (msg.data()[3] & 0xFF);
        int byteCount = msg.data()[4] & 0xFF;
        System.out.printf("Slave <- Write Multiple Registers start=%d quantity=%d byteCount=%d%n", startAddress, quantity, byteCount);

        if (quantity <= 0 || byteCount != quantity * 2 || startAddress < 0 || startAddress + quantity > HOLDING_REGISTERS.length) {
            sendException(ctx, msg.unitId(), msg.functionCode(), (byte) 0x02);
            return;
        }

        // 示例请求帧：
        // 01 10 00 02 00 03 06 00 C8 01 2C 01 90 67 53
        for (int i = 0; i < quantity; i++) {
            int offset = 5 + i * 2;
            int value = ((msg.data()[offset] & 0xFF) << 8) | (msg.data()[offset + 1] & 0xFF);
            HOLDING_REGISTERS[startAddress + i] = value;
        }

        System.out.println("Holding registers => " + Arrays.toString(HOLDING_REGISTERS));

        // 示例响应帧：
        // 01 10 00 02 00 03 21 C8
        // 0x10 响应只确认“起始地址 + 写入数量”，不会回显全部数据。
        byte[] responseData = new byte[] {
                (byte) (startAddress >> 8),
                (byte) startAddress,
                (byte) (quantity >> 8),
                (byte) quantity
        };
        ctx.writeAndFlush(new ModbusRtuFrame(msg.unitId(), msg.functionCode(), responseData));
    }

    private void sendException(ChannelHandlerContext ctx, byte unitId, byte functionCode, byte exceptionCode) {
        // 异常响应格式：功能码最高位置 1，再带 1 字节异常码。
        ctx.writeAndFlush(new ModbusRtuFrame(unitId, (byte) (functionCode | 0x80), new byte[] {exceptionCode}));
    }
}