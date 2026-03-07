package com.example.modbus.master;

import com.fazecast.jSerialComm.SerialPort;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 串口与 Netty pipeline 之间的桥接器。
 *
 * <p>建议阅读顺序：先看主站入口和主站处理器，再回头看这个桥接器。</p>
 */
public class 串口Netty桥接器 implements AutoCloseable {
    private final SerialPort 串口;
    private final EmbeddedChannel 通道;
    private final AtomicBoolean 运行中 = new AtomicBoolean();
    private Thread 工作线程;
    private OutputStream 输出流;

    public 串口Netty桥接器(String 串口名, int 波特率, EmbeddedChannel 通道) {
        this.通道 = 通道;
        this.串口 = SerialPort.getCommPort(串口名);
        this.串口.setComPortParameters(波特率, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        this.串口.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
    }

    public void 启动() throws IOException {
        if (!串口.openPort()) {
            throw new IOException("Failed to open serial port: " + 串口.getSystemPortName());
        }
        输出流 = 串口.getOutputStream();
        运行中.set(true);
        清空出站队列();

        工作线程 = new Thread(this::运行循环, "master-serial-bridge");
        工作线程.setDaemon(false);
        工作线程.start();
    }

    private void 运行循环() {
        byte[] 缓冲区 = new byte[256];
        try {
            while (运行中.get()) {
                int 可读字节数 = 串口.bytesAvailable();
                if (可读字节数 > 0) {
                    int 实际长度 = 串口.readBytes(缓冲区, Math.min(缓冲区.length, 可读字节数));
                    if (实际长度 > 0) {
                        byte[] 收到字节 = new byte[实际长度];
                        System.arraycopy(缓冲区, 0, 收到字节, 0, 实际长度);
                        通道.writeInbound(Unpooled.wrappedBuffer(收到字节));
                    }
                }
                通道.runPendingTasks();
                通道.runScheduledPendingTasks();
                清空出站队列();
                Thread.sleep(2);
            }
        } catch (Throwable 异常) {
            异常.printStackTrace();
        } finally {
            通道.finishAndReleaseAll();
            关闭资源();
        }
    }

    private void 清空出站队列() throws IOException {
        Object 出站对象;
        while ((出站对象 = 通道.readOutbound()) != null) {
            if (出站对象 instanceof ByteBuf 字节缓冲) {
                try {
                    byte[] 待发送字节 = new byte[字节缓冲.readableBytes()];
                    字节缓冲.readBytes(待发送字节);
                    输出流.write(待发送字节);
                    输出流.flush();
                } finally {
                    字节缓冲.release();
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        运行中.set(false);
        通道.close().syncUninterruptibly();
        if (工作线程 != null) {
            工作线程.join(1000);
        }
        关闭资源();
    }

    private void 关闭资源() {
        try {
            if (输出流 != null) {
                输出流.close();
                输出流 = null;
            }
        } catch (IOException ignored) {
        }
        if (串口.isOpen()) {
            串口.closePort();
        }
    }
}