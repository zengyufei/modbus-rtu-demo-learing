package com.example.modbus.master;

import com.example.modbus.master.codec.ModbusRtu响应解码器;
import com.example.modbus.master.codec.ModbusRtu编码器;
import com.example.modbus.master.handler.主站处理器;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * 主站启动入口。
 *
 * <p>建议阅读顺序：先看 README，再看协议文档，最后从这个入口进入代码。</p>
 */
public class 主站程序 {
    public static void main(String[] 参数) throws Exception {
        String 串口名 = 参数.length > 0 ? 参数[0] : "COM5";
        int 波特率 = 参数.length > 1 ? Integer.parseInt(参数[1]) : 9600;

        System.out.printf("Master open serial port %s baud=%d%n", 串口名, 波特率);

        EmbeddedChannel 通道 = new EmbeddedChannel(
                new ModbusRtu响应解码器(),
                new ModbusRtu编码器(),
                new 主站处理器()
        );

        try (串口Netty桥接器 桥接器 = new 串口Netty桥接器(串口名, 波特率, 通道)) {
            桥接器.启动();
            Thread.currentThread().join();
        }
    }
}