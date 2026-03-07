package com.example.modbus.master;

import com.example.modbus.master.codec.ModbusRtuEncoder;
import com.example.modbus.master.codec.ModbusRtuResponseDecoder;
import com.example.modbus.master.handler.MasterHandler;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * 主站启动入口。
 *
 * <p>建议阅读顺序：先看 README，再看协议文档，最后从这个入口进入代码。</p>
 */
public class MasterApplication {
    public static void main(String[] args) throws Exception {
        String serialPort = args.length > 0 ? args[0] : "COM5";
        int baudRate = args.length > 1 ? Integer.parseInt(args[1]) : 9600;

        System.out.printf("Master open serial port %s baud=%d%n", serialPort, baudRate);

        EmbeddedChannel channel = new EmbeddedChannel(
                new ModbusRtuResponseDecoder(),
                new ModbusRtuEncoder(),
                new MasterHandler()
        );

        try (SerialPortNettyBridge bridge = new SerialPortNettyBridge(serialPort, baudRate, channel)) {
            bridge.start();
            Thread.currentThread().join();
        }
    }
}