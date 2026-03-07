package com.example.modbus.slave;

import com.example.modbus.slave.codec.ModbusRtuEncoder;
import com.example.modbus.slave.codec.ModbusRtuRequestDecoder;
import com.example.modbus.slave.handler.SlaveHandler;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * 从站启动入口。
 *
 * <p>建议阅读顺序：先看 README，再从这个入口理解从站是怎么启动的。</p>
 */
public class SlaveApplication {
    public static void main(String[] args) throws Exception {
        String serialPort = args.length > 0 ? args[0] : "COM6";
        int baudRate = args.length > 1 ? Integer.parseInt(args[1]) : 9600;

        System.out.printf("Slave open serial port %s baud=%d%n", serialPort, baudRate);

        EmbeddedChannel channel = new EmbeddedChannel(
                new ModbusRtuRequestDecoder(),
                new ModbusRtuEncoder(),
                new SlaveHandler()
        );

        try (SerialPortNettyBridge bridge = new SerialPortNettyBridge(serialPort, baudRate, channel)) {
            bridge.start();
            Thread.currentThread().join();
        }
    }
}