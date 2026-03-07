package com.example.modbus.slave.handler;

import com.example.modbus.slave.codec.ModbusRtu帧;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Arrays;

/**
 * 从站业务处理器。
 */
public class 从站处理器 extends SimpleChannelInboundHandler<ModbusRtu帧> {
    private static final int[] 保持寄存器表 = {10, 20, 30, 40, 50, 60};

    @Override
    public void channelActive(ChannelHandlerContext 上下文) {
        System.out.println("从站串口已就绪，等待主站请求。");
    }

    @Override
    /**
     * 输入：主站发来的合法请求帧。
     * 输出：根据功能码分发到不同处理方法，最终生成对应的响应帧或异常帧。
     */
    protected void channelRead0(ChannelHandlerContext 上下文, ModbusRtu帧 请求帧) {
        int 功能码 = 请求帧.功能码() & 0xFF;
        if (功能码 == 0x03) {
            处理读保持寄存器(上下文, 请求帧);
            return;
        }
        if (功能码 == 0x06) {
            处理写单个寄存器(上下文, 请求帧);
            return;
        }
        if (功能码 == 0x10) {
            处理写多个寄存器(上下文, 请求帧);
            return;
        }
        发送异常响应(上下文, 请求帧.从站地址(), 请求帧.功能码(), (byte) 0x01);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext 上下文, Throwable 异常) {
        异常.printStackTrace();
        上下文.close();
    }

    /**
     * 输入：0x03 读保持寄存器请求帧。
     * 输出：返回包含寄存器值的 0x03 响应帧，或返回地址越界异常帧。
     */
    private void 处理读保持寄存器(ChannelHandlerContext 上下文, ModbusRtu帧 请求帧) {
        int 起始地址 = ((请求帧.数据区()[0] & 0xFF) << 8) | (请求帧.数据区()[1] & 0xFF);
        int 数量 = ((请求帧.数据区()[2] & 0xFF) << 8) | (请求帧.数据区()[3] & 0xFF);
        System.out.printf("从站收到读保持寄存器请求，起始地址=%d，数量=%d%n", 起始地址, 数量);

        if (数量 <= 0 || 起始地址 < 0 || 起始地址 + 数量 > 保持寄存器表.length) {
            发送异常响应(上下文, 请求帧.从站地址(), 请求帧.功能码(), (byte) 0x02);
            return;
        }

        byte[] 响应数据区 = new byte[1 + 数量 * 2];
        响应数据区[0] = (byte) (数量 * 2);
        for (int 下标 = 0; 下标 < 数量; 下标++) {
            int 寄存器值 = 保持寄存器表[起始地址 + 下标];
            响应数据区[1 + 下标 * 2] = (byte) (寄存器值 >> 8);
            响应数据区[2 + 下标 * 2] = (byte) 寄存器值;
        }
        上下文.writeAndFlush(new ModbusRtu帧(请求帧.从站地址(), 请求帧.功能码(), 响应数据区));
    }

    /**
     * 输入：0x06 写单个寄存器请求帧。
     * 输出：更新目标寄存器后，原样回显地址和值作为响应帧。
     */
    private void 处理写单个寄存器(ChannelHandlerContext 上下文, ModbusRtu帧 请求帧) {
        int 地址 = ((请求帧.数据区()[0] & 0xFF) << 8) | (请求帧.数据区()[1] & 0xFF);
        int 数值 = ((请求帧.数据区()[2] & 0xFF) << 8) | (请求帧.数据区()[3] & 0xFF);
        System.out.printf("从站收到写单个寄存器请求，地址=%d，数值=%d%n", 地址, 数值);

        if (地址 < 0 || 地址 >= 保持寄存器表.length) {
            发送异常响应(上下文, 请求帧.从站地址(), 请求帧.功能码(), (byte) 0x02);
            return;
        }

        保持寄存器表[地址] = 数值;
        System.out.println("保持寄存器当前值 => " + Arrays.toString(保持寄存器表));
        上下文.writeAndFlush(new ModbusRtu帧(请求帧.从站地址(), 请求帧.功能码(), 请求帧.数据区()));
    }

    /**
     * 输入：0x10 写多个寄存器请求帧。
     * 输出：批量更新寄存器后，返回“起始地址 + 数量”的确认响应帧。
     */
    private void 处理写多个寄存器(ChannelHandlerContext 上下文, ModbusRtu帧 请求帧) {
        int 起始地址 = ((请求帧.数据区()[0] & 0xFF) << 8) | (请求帧.数据区()[1] & 0xFF);
        int 数量 = ((请求帧.数据区()[2] & 0xFF) << 8) | (请求帧.数据区()[3] & 0xFF);
        int 数据字节数 = 请求帧.数据区()[4] & 0xFF;
        System.out.printf("从站收到写多个寄存器请求，起始地址=%d，数量=%d，字节数=%d%n", 起始地址, 数量, 数据字节数);

        if (数量 <= 0 || 数据字节数 != 数量 * 2 || 起始地址 < 0 || 起始地址 + 数量 > 保持寄存器表.length) {
            发送异常响应(上下文, 请求帧.从站地址(), 请求帧.功能码(), (byte) 0x02);
            return;
        }

        for (int 下标 = 0; 下标 < 数量; 下标++) {
            int 数据偏移 = 5 + 下标 * 2;
            int 寄存器值 = ((请求帧.数据区()[数据偏移] & 0xFF) << 8) | (请求帧.数据区()[数据偏移 + 1] & 0xFF);
            保持寄存器表[起始地址 + 下标] = 寄存器值;
        }

        System.out.println("保持寄存器当前值 => " + Arrays.toString(保持寄存器表));

        byte[] 响应数据区 = new byte[] {
                (byte) (起始地址 >> 8),
                (byte) 起始地址,
                (byte) (数量 >> 8),
                (byte) 数量
        };
        上下文.writeAndFlush(new ModbusRtu帧(请求帧.从站地址(), 请求帧.功能码(), 响应数据区));
    }

    /**
     * 输入：一个非法功能码、非法地址或非法数据长度场景。
     * 输出：构造带异常功能码和异常码的 Modbus 异常响应帧。
     */
    private void 发送异常响应(ChannelHandlerContext 上下文, byte 从站地址, byte 功能码, byte 异常码) {
        上下文.writeAndFlush(new ModbusRtu帧(从站地址, (byte) (功能码 | 0x80), new byte[] {异常码}));
    }
}
