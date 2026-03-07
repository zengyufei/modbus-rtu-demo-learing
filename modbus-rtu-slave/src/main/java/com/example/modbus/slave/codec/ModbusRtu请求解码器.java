package com.example.modbus.slave.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 从站侧 RTU 请求解码器。
 */
public class ModbusRtu请求解码器 extends ChannelInboundHandlerAdapter {
    private static final long 帧间隔毫秒 = 5;

    private ByteBuf 累积缓冲;
    private ScheduledFuture<?> 间隔检测任务;

    @Override
    public void handlerAdded(ChannelHandlerContext 上下文) {
        累积缓冲 = 上下文.alloc().buffer();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext 上下文) {
        释放累积缓冲();
        取消间隔检测任务();
    }

    @Override
    public void channelRead(ChannelHandlerContext 上下文, Object 消息) {
        if (!(消息 instanceof ByteBuf 输入缓冲)) {
            上下文.fireChannelRead(消息);
            return;
        }

        try {
            累积缓冲.writeBytes(输入缓冲);
            解码帧(上下文);
            安排帧间隔检查(上下文);
        } finally {
            输入缓冲.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext 上下文) throws Exception {
        丢弃残帧("channel inactive");
        super.channelInactive(上下文);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext 上下文, Throwable 异常) throws Exception {
        丢弃残帧("decoder exception");
        super.exceptionCaught(上下文, 异常);
    }

    /**
     * 输入：串口持续送入的字节流缓冲区。
     * 输出：从缓冲区中切出一帧或多帧完整 RTU 请求帧，并交给从站业务处理器。
     */
    private void 解码帧(ChannelHandlerContext 上下文) {
        while (累积缓冲.readableBytes() >= 4) {
            byte 功能码 = 累积缓冲.getByte(累积缓冲.readerIndex() + 1);
            int 期望帧长度 = 计算期望长度(累积缓冲, 功能码);
            if (期望帧长度 == -1) {
                throw new IllegalStateException("不支持的功能码: " + (功能码 & 0xFF));
            }
            if (期望帧长度 == Integer.MAX_VALUE || 累积缓冲.readableBytes() < 期望帧长度) {
                break;
            }

            byte[] 完整帧 = new byte[期望帧长度];
            累积缓冲.readBytes(完整帧);

            int 期望CRC = ((完整帧[期望帧长度 - 1] & 0xFF) << 8) | (完整帧[期望帧长度 - 2] & 0xFF);
            int 实际CRC = ModbusCRC16计算器.计算(完整帧, 期望帧长度 - 2);
            if (期望CRC != 实际CRC) {
                throw new IllegalStateException("CRC 不匹配，期望=%04X，实际=%04X".formatted(期望CRC, 实际CRC));
            }

            byte[] 数据区 = new byte[期望帧长度 - 4];
            System.arraycopy(完整帧, 2, 数据区, 0, 数据区.length);
            上下文.fireChannelRead(new ModbusRtu帧(完整帧[0], 完整帧[1], 数据区));
        }

        if (累积缓冲.readerIndex() > 0) {
            累积缓冲.discardReadBytes();
        }
    }

    private int 计算期望长度(ByteBuf 输入缓冲, byte 功能码) {
        int 功能码整数 = 功能码 & 0xFF;
        if (功能码整数 == 0x03 || 功能码整数 == 0x06) {
            return 8;
        }
        if (功能码整数 == 0x10) {
            if (输入缓冲.readableBytes() < 7) {
                return Integer.MAX_VALUE;
            }
            int 数据字节数 = 输入缓冲.getByte(输入缓冲.readerIndex() + 6) & 0xFF;
            return 7 + 数据字节数 + 2;
        }
        return -1;
    }

    private void 安排帧间隔检查(ChannelHandlerContext 上下文) {
        取消间隔检测任务();
        间隔检测任务 = 上下文.executor().schedule(() -> {
            if (累积缓冲 != null && 累积缓冲.isReadable()) {
                丢弃残帧("inter-frame gap timeout");
            }
        }, 帧间隔毫秒, TimeUnit.MILLISECONDS);
    }

    private void 丢弃残帧(String 原因) {
        if (累积缓冲 != null && 累积缓冲.isReadable()) {
            byte[] 残留字节 = new byte[累积缓冲.readableBytes()];
            累积缓冲.readBytes(残留字节);
            累积缓冲.clear();
            System.err.printf("从站解码器丢弃残缺 RTU 帧，原因=%s，字节=%s%n", 原因, 转十六进制(残留字节));
        }
    }

    private void 取消间隔检测任务() {
        if (间隔检测任务 != null) {
            间隔检测任务.cancel(false);
            间隔检测任务 = null;
        }
    }

    private void 释放累积缓冲() {
        if (累积缓冲 != null) {
            累积缓冲.release();
            累积缓冲 = Unpooled.EMPTY_BUFFER;
        }
    }

    private String 转十六进制(byte[] 字节数组) {
        StringBuilder 构造器 = new StringBuilder();
        for (byte 当前字节 : 字节数组) {
            if (!构造器.isEmpty()) {
                构造器.append(' ');
            }
            构造器.append(String.format("%02X", 当前字节 & 0xFF));
        }
        return 构造器.toString();
    }
}
