package com.example.modbus.slave;

import com.example.modbus.slave.codec.ModbusRtu请求解码器;
import com.example.modbus.slave.codec.ModbusRtu编码器;
import com.example.modbus.slave.handler.从站处理器;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * 从站启动入口。
 */
public class 从站程序 {
    /**
     * 输入：命令行参数中的串口名和波特率。
     * 输出：启动串口桥接器和从站处理链，进入等待主站请求的状态。
     */
    public static void main(String[] 参数) throws Exception {
        String 串口名 = 参数.length > 0 ? 参数[0] : "COM6";
        int 波特率 = 参数.length > 1 ? Integer.parseInt(参数[1]) : 9600;

        System.out.printf("从站打开串口 %s，波特率=%d%n", 串口名, 波特率);

        EmbeddedChannel 通道 = new EmbeddedChannel(
                new ModbusRtu请求解码器(),
                new ModbusRtu编码器(),
                new 从站处理器()
        );

        try (串口Netty桥接器 桥接器 = new 串口Netty桥接器(串口名, 波特率, 通道)) {
            桥接器.启动();
            Thread.currentThread().join();
        }
    }
}
