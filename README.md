# Netty Modbus RTU 教学 Demo

这是一个面向学习的 Modbus RTU 主从示例仓库，重点不是“功能很多”，而是让你能顺着代码和文档把主从结构、RTU 帧格式、寄存器读写、CRC、异常响应、串口收发一步步看明白。

## 你会得到什么

- 一个主站项目：`modbus-rtu-master`
- 一个从站项目：`modbus-rtu-slave`
- 真实串口运行方式：支持 `com0com` 虚拟串口对
- 已实现功能码：`0x03`、`0x06`、`0x10`
- 主站轮询、超时重试、RTU 帧间隔处理
- 中文代码注释 + 中文协议文档

## 目录结构

```text
D:\cache\codex1
├─ modbus-rtu-master        # 主站工程
├─ modbus-rtu-slave         # 从站工程
└─ docs
   ├─ protocol-walkthrough.md   # 完整 03 -> 06 -> 10 逐字节讲解
   └─ modbus-exceptions.md      # 异常码说明 + 本 demo 如何触发
```

## 建议学习顺序

如果你是第一次看这个项目，建议按这个顺序：

1. 先看本文档，知道项目全貌
2. 再看 [docs/protocol-walkthrough.md](D:\cache\codex1\docs\protocol-walkthrough.md)
3. 再看主站入口和从站入口
4. 然后看主站轮询逻辑和从站寄存器处理逻辑
5. 最后再看编码器、解码器、CRC、串口桥接

## 核心代码入口

### 第一步：先看启动入口

- 主站入口：`modbus-rtu-master/src/main/java/com/example/modbus/master/主站程序.java`
- 从站入口：`modbus-rtu-slave/src/main/java/com/example/modbus/slave/从站程序.java`

### 第二步：再看主从业务逻辑

- 主站轮询逻辑：`modbus-rtu-master/src/main/java/com/example/modbus/master/handler/主站处理器.java`
- 从站业务逻辑：`modbus-rtu-slave/src/main/java/com/example/modbus/slave/handler/从站处理器.java`

### 第三步：最后看协议细节

- 主站响应解码：`modbus-rtu-master/src/main/java/com/example/modbus/master/codec/ModbusRtu响应解码器.java`
- 从站请求解码：`modbus-rtu-slave/src/main/java/com/example/modbus/slave/codec/ModbusRtu请求解码器.java`
- 主从编码器：`ModbusRtu编码器.java`
- CRC：`ModbusCRC16计算器.java`
- 串口桥接：`串口Netty桥接器.java`

## 本 demo 实现了什么

### 功能码

- `0x03` 读保持寄存器
- `0x06` 写单个保持寄存器
- `0x10` 写多个保持寄存器

### 主站行为

主站会循环做下面几件事：

1. 读 `register[0..3]`
2. 写 `register[1] = 1234`
3. 再读 `register[0..3]`
4. 写多个寄存器：`register[2] = 200`、`register[3] = 300`、`register[4] = 400`
5. 读 `register[0..5]`

### 从站行为

从站维护一个很小的保持寄存器表：

- `10, 20, 30, 40, 50, 60`

主站写入后，你能直接在日志里看到它怎么变化。

## 串口运行方式

### 使用虚拟串口对

推荐用 `com0com` 创建一对互通串口，例如：

- 主站：`COM5`
- 从站：`COM6`

### 一键运行脚本

仓库根目录新增了 `scripts` 目录，里面同时提供了 `cmd` 和 PowerShell 两套脚本：

- `scripts\一键运行-demo.cmd`
- `scripts\一键运行-demo.ps1`
- `scripts\run-master.cmd`
- `scripts\run-slave.cmd`
- `scripts\启动主站.cmd`
- `scripts\启动主站.ps1`
- `scripts\启动从站.cmd`
- `scripts\启动从站.ps1`

#### 方式一：双击 `.cmd`

默认会按 `COM5 -> 主站`、`COM6 -> 从站`、`9600` 波特率启动：

```cmd
scripts\一键运行-demo.cmd
```

如果你要自定义串口和波特率：

```cmd
scripts\一键运行-demo.cmd COM5 COM6 9600
```

#### 方式二：PowerShell 一键运行

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\一键运行-demo.ps1
```

自定义参数：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\一键运行-demo.ps1 -主站串口 COM5 -从站串口 COM6 -波特率 9600
```

### 单独启动命令

先启动从站：

```bash
scripts\启动从站.cmd COM6 9600
```

再启动主站：

```bash
scripts\启动主站.cmd COM5 9600
```

## 文档索引

- 协议逐字节讲解：`docs/protocol-walkthrough.md`
- 异常码说明：`docs/modbus-exceptions.md`

## 这个仓库适合怎么学

如果你的目标是“练手而不是背规范”，建议这样用：

1. 先跑起来，看日志
2. 对照 `protocol-walkthrough.md` 看十六进制帧
3. 改 `MasterHandler` 里的轮询计划，观察从站寄存器变化
4. 故意触发越界地址或非法功能码，对照异常文档理解异常响应
5. 最后再把固定 `5ms` 帧间隔改成按波特率计算的 3.5 字符时间
