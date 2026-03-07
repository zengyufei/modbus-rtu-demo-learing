package com.example.modbus.master.handler;

import com.example.modbus.master.codec.ModbusRtu帧;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

// 学习顺序建议：这是主站最重要的教学类，建议在看完启动入口后第一时间阅读。
public class 主站处理器 extends SimpleChannelInboundHandler<ModbusRtu帧> {
    private static final byte 从站地址 = 0x01;
    private static final long 轮询间隔毫秒 = 1500;
    private static final long 响应超时毫秒 = 800;
    private static final int 最大重试次数 = 2;

    private final List<轮询命令> 轮询计划 = List.of(
            轮询命令.创建读命令(0, 4),
            轮询命令.创建单写命令(1, 1234),
            轮询命令.创建读命令(0, 4),
            轮询命令.创建多写命令(2, new int[] {200, 300, 400}),
            轮询命令.创建读命令(0, 6)
    );

    private int 当前轮询下标;
    private 待响应请求 当前请求;
    private ScheduledFuture<?> 轮询任务;
    private ScheduledFuture<?> 超时任务;
    private volatile boolean 正在关闭;

    @Override
    public void channelActive(ChannelHandlerContext 上下文) {
        System.out.println("Master connected to serial line, polling scheduler started.");
        轮询任务 = 上下文.executor().scheduleWithFixedDelay(() -> 执行轮询节拍(上下文), 300, 轮询间隔毫秒, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext 上下文, ModbusRtu帧 响应帧) {
        if (正在关闭) {
            return;
        }

        int 响应功能码 = 响应帧.功能码() & 0xFF;
        if ((响应功能码 & 0x80) != 0) {
            int 异常码 = 响应帧.数据区()[0] & 0xFF;
            System.err.printf("Slave exception, function=0x%02X exception=0x%02X%n", 响应功能码, 异常码);
            失败后推进轮询("slave exception");
            return;
        }

        if (当前请求 == null) {
            System.err.printf("Unexpected response function=0x%02X without pending request.%n", 响应功能码);
            return;
        }
        if (响应功能码 != 当前请求.命令.功能码) {
            System.err.printf("Unexpected response function=0x%02X, expect=0x%02X%n", 响应功能码, 当前请求.命令.功能码);
            return;
        }

        取消超时任务();
        记录响应日志(响应帧);
        当前请求 = null;
        当前轮询下标 = (当前轮询下标 + 1) % 轮询计划.size();
    }

    @Override
    public void channelInactive(ChannelHandlerContext 上下文) {
        关闭处理器();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext 上下文, Throwable 异常) {
        异常.printStackTrace();
        关闭处理器();
        上下文.close();
    }

    public void 关闭处理器() {
        正在关闭 = true;
        取消轮询任务();
        取消超时任务();
        当前请求 = null;
    }

    private void 执行轮询节拍(ChannelHandlerContext 上下文) {
        if (正在关闭 || !上下文.channel().isActive() || 当前请求 != null) {
            return;
        }

        轮询命令 命令 = 轮询计划.get(当前轮询下标);
        当前请求 = new 待响应请求(命令);
        发送命令(上下文, 命令, false);
    }

    private void 发送命令(ChannelHandlerContext 上下文, 轮询命令 命令, boolean 是否重试) {
        上下文.writeAndFlush(new ModbusRtu帧(从站地址, 命令.功能码, 命令.数据区));
        if (是否重试) {
            System.out.printf("Master retry #%d -> %s%n", 当前请求.重试次数, 命令.描述);
        } else {
            System.out.printf("Master poll -> %s%n", 命令.描述);
        }
        安排超时任务(上下文);
    }

    private void 安排超时任务(ChannelHandlerContext 上下文) {
        取消超时任务();
        超时任务 = 上下文.executor().schedule(() -> 处理超时(上下文), 响应超时毫秒, TimeUnit.MILLISECONDS);
    }

    private void 处理超时(ChannelHandlerContext 上下文) {
        if (当前请求 == null || 正在关闭) {
            return;
        }
        if (当前请求.重试次数 < 最大重试次数) {
            当前请求.重试次数++;
            发送命令(上下文, 当前请求.命令, true);
            return;
        }

        System.err.printf("Master timeout -> %s, retries exhausted.%n", 当前请求.命令.描述);
        当前请求 = null;
        当前轮询下标 = (当前轮询下标 + 1) % 轮询计划.size();
    }

    private void 失败后推进轮询(String 原因) {
        取消超时任务();
        if (当前请求 != null) {
            System.err.printf("Request failed: %s, command=%s%n", 原因, 当前请求.命令.描述);
        }
        当前请求 = null;
        当前轮询下标 = (当前轮询下标 + 1) % 轮询计划.size();
    }

    private void 记录响应日志(ModbusRtu帧 响应帧) {
        int 响应功能码 = 响应帧.功能码() & 0xFF;
        if (响应功能码 == 0x03) {
            int 数据字节数 = 响应帧.数据区()[0] & 0xFF;
            System.out.printf("Slave -> Read response byteCount=%d%n", 数据字节数);
            for (int 数据偏移 = 1; 数据偏移 < 数据字节数; 数据偏移 += 2) {
                int 寄存器序号 = (数据偏移 - 1) / 2;
                int 寄存器值 = ((响应帧.数据区()[数据偏移] & 0xFF) << 8) | (响应帧.数据区()[数据偏移 + 1] & 0xFF);
                System.out.printf("  register[%d] = %d%n", 寄存器序号, 寄存器值);
            }
            return;
        }

        if (响应功能码 == 0x06) {
            int 地址 = ((响应帧.数据区()[0] & 0xFF) << 8) | (响应帧.数据区()[1] & 0xFF);
            int 数值 = ((响应帧.数据区()[2] & 0xFF) << 8) | (响应帧.数据区()[3] & 0xFF);
            System.out.printf("Slave -> Write single confirmed address=%d value=%d%n", 地址, 数值);
            return;
        }

        if (响应功能码 == 0x10) {
            int 起始地址 = ((响应帧.数据区()[0] & 0xFF) << 8) | (响应帧.数据区()[1] & 0xFF);
            int 数量 = ((响应帧.数据区()[2] & 0xFF) << 8) | (响应帧.数据区()[3] & 0xFF);
            System.out.printf("Slave -> Write multiple confirmed start=%d quantity=%d%n", 起始地址, 数量);
        }
    }

    private void 取消轮询任务() {
        if (轮询任务 != null) {
            轮询任务.cancel(false);
            轮询任务 = null;
        }
    }

    private void 取消超时任务() {
        if (超时任务 != null) {
            超时任务.cancel(false);
            超时任务 = null;
        }
    }

    private static final class 待响应请求 {
        private final 轮询命令 命令;
        private int 重试次数;

        private 待响应请求(轮询命令 命令) {
            this.命令 = 命令;
        }
    }

    private static final class 轮询命令 {
        private final byte 功能码;
        private final byte[] 数据区;
        private final String 描述;

        private 轮询命令(byte 功能码, byte[] 数据区, String 描述) {
            this.功能码 = 功能码;
            this.数据区 = 数据区;
            this.描述 = 描述;
        }

        private static 轮询命令 创建读命令(int 起始地址, int 数量) {
            byte[] 数据区 = new byte[] {
                    (byte) (起始地址 >> 8),
                    (byte) 起始地址,
                    (byte) (数量 >> 8),
                    (byte) 数量
            };
            return new 轮询命令((byte) 0x03, 数据区,
                    "Read Holding Registers start=" + 起始地址 + " quantity=" + 数量);
        }

        private static 轮询命令 创建单写命令(int 地址, int 数值) {
            byte[] 数据区 = new byte[] {
                    (byte) (地址 >> 8),
                    (byte) 地址,
                    (byte) (数值 >> 8),
                    (byte) 数值
            };
            return new 轮询命令((byte) 0x06, 数据区,
                    "Write Single Register address=" + 地址 + " value=" + 数值);
        }

        private static 轮询命令 创建多写命令(int 起始地址, int[] 数值列表) {
            int 数量 = 数值列表.length;
            byte[] 数据区 = new byte[5 + 数量 * 2];
            数据区[0] = (byte) (起始地址 >> 8);
            数据区[1] = (byte) 起始地址;
            数据区[2] = (byte) (数量 >> 8);
            数据区[3] = (byte) 数量;
            数据区[4] = (byte) (数量 * 2);
            for (int 下标 = 0; 下标 < 数量; 下标++) {
                int 数值 = 数值列表[下标];
                数据区[5 + 下标 * 2] = (byte) (数值 >> 8);
                数据区[6 + 下标 * 2] = (byte) 数值;
            }
            return new 轮询命令((byte) 0x10, 数据区,
                    "Write Multiple Registers start=" + 起始地址 + " values=" + Arrays.toString(数值列表));
        }
    }
}