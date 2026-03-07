package com.example.modbus.master.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 主站侧 RTU 响应解码器。
 *
 * <p>建议阅读顺序：先理解 0x03、0x06、0x10 的帧结构，再来看这里。</p>
 */
public class ModbusRtuResponseDecoder extends ChannelInboundHandlerAdapter {
    private static final long INTER_FRAME_GAP_MS = 5;

    private ByteBuf cumulation;
    private ScheduledFuture<?> gapTask;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        cumulation = ctx.alloc().buffer();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        releaseCumulation();
        cancelGapTask();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf input)) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            cumulation.writeBytes(input);
            decodeFrames(ctx);
            scheduleGapCheck(ctx);
        } finally {
            input.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        discardPartialFrame("channel inactive");
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        discardPartialFrame("decoder exception");
        super.exceptionCaught(ctx, cause);
    }

    private void decodeFrames(ChannelHandlerContext ctx) {
        while (cumulation.readableBytes() >= 4) {
            byte unitId = cumulation.getByte(cumulation.readerIndex());
            byte functionCode = cumulation.getByte(cumulation.readerIndex() + 1);
            int frameLength = expectedLength(cumulation, functionCode);
            if (frameLength == -1) {
                throw new IllegalStateException("Unsupported function code: " + (functionCode & 0xFF));
            }
            if (frameLength == Integer.MAX_VALUE || cumulation.readableBytes() < frameLength) {
                break;
            }

            byte[] frame = new byte[frameLength];
            cumulation.readBytes(frame);

            int expected = ((frame[frameLength - 1] & 0xFF) << 8) | (frame[frameLength - 2] & 0xFF);
            int actual = Crc16Modbus.calculate(frame, frameLength - 2);
            if (expected != actual) {
                throw new IllegalStateException("CRC mismatch, expected=%04X actual=%04X".formatted(expected, actual));
            }

            byte[] data = new byte[frameLength - 4];
            System.arraycopy(frame, 2, data, 0, data.length);
            ctx.fireChannelRead(new ModbusRtuFrame(unitId, functionCode, data));
        }

        if (cumulation.readerIndex() > 0) {
            cumulation.discardReadBytes();
        }
    }

    private int expectedLength(ByteBuf in, byte functionCode) {
        int fc = functionCode & 0xFF;
        if (fc == 0x03) {
            if (in.readableBytes() < 3) {
                return Integer.MAX_VALUE;
            }
            int byteCount = in.getByte(in.readerIndex() + 2) & 0xFF;
            return 3 + byteCount + 2;
        }
        if (fc == 0x06 || fc == 0x10) {
            return 8;
        }
        if ((fc & 0x80) != 0) {
            return 5;
        }
        return -1;
    }

    private void scheduleGapCheck(ChannelHandlerContext ctx) {
        cancelGapTask();
        gapTask = ctx.executor().schedule(() -> {
            if (cumulation != null && cumulation.isReadable()) {
                discardPartialFrame("inter-frame gap timeout");
            }
        }, INTER_FRAME_GAP_MS, TimeUnit.MILLISECONDS);
    }

    private void discardPartialFrame(String reason) {
        if (cumulation != null && cumulation.isReadable()) {
            byte[] orphan = new byte[cumulation.readableBytes()];
            cumulation.readBytes(orphan);
            cumulation.clear();
            System.err.printf("Master decoder discarded partial RTU frame, reason=%s bytes=%s%n", reason, toHex(orphan));
        }
    }

    private void cancelGapTask() {
        if (gapTask != null) {
            gapTask.cancel(false);
            gapTask = null;
        }
    }

    private void releaseCumulation() {
        if (cumulation != null) {
            cumulation.release();
            cumulation = Unpooled.EMPTY_BUFFER;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", value & 0xFF));
        }
        return builder.toString();
    }
}