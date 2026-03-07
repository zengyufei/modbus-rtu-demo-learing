package com.example.modbus.master.handler;

import com.example.modbus.master.codec.ModbusRtuFrame;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

// 学习顺序建议：这是主站最重要的教学类，建议在看完启动入口后第一时间阅读。
public class MasterHandler extends SimpleChannelInboundHandler<ModbusRtuFrame> {
    private static final byte UNIT_ID = 0x01;
    private static final long POLL_INTERVAL_MS = 1500;
    private static final long RESPONSE_TIMEOUT_MS = 800;
    private static final int MAX_RETRIES = 2;

    // A small cyclic poll plan so the demo behaves like a simple industrial master.
    private final List<PollCommand> pollPlan = List.of(
            PollCommand.read(0, 4),
            PollCommand.writeSingle(1, 1234),
            PollCommand.read(0, 4),
            PollCommand.writeMultiple(2, new int[] {200, 300, 400}),
            PollCommand.read(0, 6)
    );

    private int pollIndex;
    private PendingRequest pending;
    private ScheduledFuture<?> pollFuture;
    private ScheduledFuture<?> timeoutFuture;
    private volatile boolean shuttingDown;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("Master connected to serial line, polling scheduler started.");
        pollFuture = ctx.executor().scheduleWithFixedDelay(() -> pollTick(ctx), 300, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ModbusRtuFrame msg) {
        if (shuttingDown) {
            return;
        }

        int functionCode = msg.functionCode() & 0xFF;
        if ((functionCode & 0x80) != 0) {
            int exceptionCode = msg.data()[0] & 0xFF;
            System.err.printf("Slave exception, function=0x%02X exception=0x%02X%n", functionCode, exceptionCode);
            failAndAdvance("slave exception");
            return;
        }

        if (pending == null) {
            System.err.printf("Unexpected response function=0x%02X without pending request.%n", functionCode);
            return;
        }
        if (functionCode != pending.command.functionCode) {
            System.err.printf("Unexpected response function=0x%02X, expect=0x%02X%n", functionCode, pending.command.functionCode);
            return;
        }

        cancelTimeout();
        logResponse(msg);
        pending = null;
        pollIndex = (pollIndex + 1) % pollPlan.size();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        shutdown();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        shutdown();
        ctx.close();
    }

    public void shutdown() {
        shuttingDown = true;
        cancelPoller();
        cancelTimeout();
        pending = null;
    }

    private void pollTick(ChannelHandlerContext ctx) {
        // Only one Modbus RTU request may be in flight on the line at a time.
        if (shuttingDown || !ctx.channel().isActive() || pending != null) {
            return;
        }

        PollCommand command = pollPlan.get(pollIndex);
        pending = new PendingRequest(command);
        sendCommand(ctx, command, false);
    }

    private void sendCommand(ChannelHandlerContext ctx, PollCommand command, boolean retry) {
        ctx.writeAndFlush(new ModbusRtuFrame(UNIT_ID, command.functionCode, command.payload));
        if (retry) {
            System.out.printf("Master retry #%d -> %s%n", pending.retryCount, command.description);
        } else {
            System.out.printf("Master poll -> %s%n", command.description);
        }
        scheduleTimeout(ctx);
    }

    private void scheduleTimeout(ChannelHandlerContext ctx) {
        cancelTimeout();
        timeoutFuture = ctx.executor().schedule(() -> onTimeout(ctx), RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void onTimeout(ChannelHandlerContext ctx) {
        if (pending == null || shuttingDown) {
            return;
        }
        if (pending.retryCount < MAX_RETRIES) {
            pending.retryCount++;
            sendCommand(ctx, pending.command, true);
            return;
        }

        System.err.printf("Master timeout -> %s, retries exhausted.%n", pending.command.description);
        pending = null;
        pollIndex = (pollIndex + 1) % pollPlan.size();
    }

    private void failAndAdvance(String reason) {
        cancelTimeout();
        if (pending != null) {
            System.err.printf("Request failed: %s, command=%s%n", reason, pending.command.description);
        }
        pending = null;
        pollIndex = (pollIndex + 1) % pollPlan.size();
    }

    private void logResponse(ModbusRtuFrame msg) {
        int functionCode = msg.functionCode() & 0xFF;
        if (functionCode == 0x03) {
            int byteCount = msg.data()[0] & 0xFF;
            System.out.printf("Slave -> Read response byteCount=%d%n", byteCount);
            for (int i = 1; i < byteCount; i += 2) {
                int registerIndex = (i - 1) / 2;
                int value = ((msg.data()[i] & 0xFF) << 8) | (msg.data()[i + 1] & 0xFF);
                System.out.printf("  register[%d] = %d%n", registerIndex, value);
            }
            return;
        }

        if (functionCode == 0x06) {
            int address = ((msg.data()[0] & 0xFF) << 8) | (msg.data()[1] & 0xFF);
            int value = ((msg.data()[2] & 0xFF) << 8) | (msg.data()[3] & 0xFF);
            System.out.printf("Slave -> Write single confirmed address=%d value=%d%n", address, value);
            return;
        }

        if (functionCode == 0x10) {
            int startAddress = ((msg.data()[0] & 0xFF) << 8) | (msg.data()[1] & 0xFF);
            int quantity = ((msg.data()[2] & 0xFF) << 8) | (msg.data()[3] & 0xFF);
            System.out.printf("Slave -> Write multiple confirmed start=%d quantity=%d%n", startAddress, quantity);
        }
    }

    private void cancelPoller() {
        if (pollFuture != null) {
            pollFuture.cancel(false);
            pollFuture = null;
        }
    }

    private void cancelTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }

    private static final class PendingRequest {
        private final PollCommand command;
        private int retryCount;

        private PendingRequest(PollCommand command) {
            this.command = command;
        }
    }

    private static final class PollCommand {
        private final byte functionCode;
        private final byte[] payload;
        private final String description;

        private PollCommand(byte functionCode, byte[] payload, String description) {
            this.functionCode = functionCode;
            this.payload = payload;
            this.description = description;
        }

        private static PollCommand read(int startAddress, int quantity) {
            // Example request RTU frame for read(0, 4):
            // 01 03 00 00 00 04 44 09
            byte[] payload = new byte[] {
                    (byte) (startAddress >> 8),
                    (byte) startAddress,
                    (byte) (quantity >> 8),
                    (byte) quantity
            };
            return new PollCommand((byte) 0x03, payload,
                    "Read Holding Registers start=" + startAddress + " quantity=" + quantity);
        }

        private static PollCommand writeSingle(int address, int value) {
            // Example request/response RTU frame for writeSingle(1, 1234):
            // 01 06 00 01 04 D2 5A 97
            byte[] payload = new byte[] {
                    (byte) (address >> 8),
                    (byte) address,
                    (byte) (value >> 8),
                    (byte) value
            };
            return new PollCommand((byte) 0x06, payload,
                    "Write Single Register address=" + address + " value=" + value);
        }

        private static PollCommand writeMultiple(int startAddress, int[] values) {
            int quantity = values.length;
            // Example request RTU frame for writeMultiple(2, [200, 300, 400]):
            // 01 10 00 02 00 03 06 00 C8 01 2C 01 90 67 53
            // Example response RTU frame:
            // 01 10 00 02 00 03 21 C8
            // 0x10 request PDU = start(2) + quantity(2) + byteCount(1) + register values(2*n)
            byte[] payload = new byte[5 + quantity * 2];
            payload[0] = (byte) (startAddress >> 8);
            payload[1] = (byte) startAddress;
            payload[2] = (byte) (quantity >> 8);
            payload[3] = (byte) quantity;
            payload[4] = (byte) (quantity * 2);
            for (int i = 0; i < quantity; i++) {
                int value = values[i];
                payload[5 + i * 2] = (byte) (value >> 8);
                payload[6 + i * 2] = (byte) value;
            }
            return new PollCommand((byte) 0x10, payload,
                    "Write Multiple Registers start=" + startAddress + " values=" + Arrays.toString(values));
        }
    }
}