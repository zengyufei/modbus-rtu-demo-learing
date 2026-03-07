package com.example.modbus.slave;

import com.fazecast.jSerialComm.SerialPort;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 从站侧串口桥接器。
 *
 * <p>建议阅读顺序：先看 SlaveApplication 和 SlaveHandler，再回来看这里。</p>
 */
public class SerialPortNettyBridge implements AutoCloseable {
    private final SerialPort serialPort;
    private final EmbeddedChannel channel;
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread worker;
    private OutputStream outputStream;

    public SerialPortNettyBridge(String portName, int baudRate, EmbeddedChannel channel) {
        this.channel = channel;
        this.serialPort = SerialPort.getCommPort(portName);
        this.serialPort.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        this.serialPort.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
    }

    public void start() throws IOException {
        if (!serialPort.openPort()) {
            throw new IOException("Failed to open serial port: " + serialPort.getSystemPortName());
        }
        outputStream = serialPort.getOutputStream();
        running.set(true);
        drainOutbound();

        worker = new Thread(this::runLoop, "slave-serial-bridge");
        worker.setDaemon(false);
        worker.start();
    }

    private void runLoop() {
        byte[] buffer = new byte[256];
        try {
            while (running.get()) {
                int available = serialPort.bytesAvailable();
                if (available > 0) {
                    int length = serialPort.readBytes(buffer, Math.min(buffer.length, available));
                    if (length > 0) {
                        byte[] copy = new byte[length];
                        System.arraycopy(buffer, 0, copy, 0, length);
                        channel.writeInbound(Unpooled.wrappedBuffer(copy));
                    }
                }
                channel.runPendingTasks();
                channel.runScheduledPendingTasks();
                drainOutbound();
                Thread.sleep(2);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            channel.finishAndReleaseAll();
            closeResources();
        }
    }

    private void drainOutbound() throws IOException {
        Object outbound;
        while ((outbound = channel.readOutbound()) != null) {
            if (outbound instanceof ByteBuf byteBuf) {
                try {
                    byte[] bytes = new byte[byteBuf.readableBytes()];
                    byteBuf.readBytes(bytes);
                    outputStream.write(bytes);
                    outputStream.flush();
                } finally {
                    byteBuf.release();
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        running.set(false);
        channel.close().syncUninterruptibly();
        if (worker != null) {
            worker.join(1000);
        }
        closeResources();
    }

    private void closeResources() {
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
        } catch (IOException ignored) {
        }
        if (serialPort.isOpen()) {
            serialPort.closePort();
        }
    }
}