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

### com0com 安装步骤

如果你的电脑上还没有可互通的虚拟串口，可以先安装 `com0com`。

#### 第一步：安装 com0com

1. 运行 com0com 安装包：
   - `com0com-setup.exe`
2. 安装完成后，系统里通常会先生成一对默认端口：
   - `CNCA0`
   - `CNCB0`
3. 安装完成后，可以用 PowerShell 确认当前串口列表：

```powershell
Get-CimInstance Win32_SerialPort | Select-Object DeviceID, Name
```

如果安装成功，你一般会看到类似：

```text
COM1     通信端口 (COM1)
COM3     蓝牙链接上的标准串行 (COM3)
COM4     蓝牙链接上的标准串行 (COM4)
CNCA0    com0com - serial port emulator
CNCB0    com0com - serial port emulator
```

### 把虚拟串口改成 COM5 和 COM6

本 demo 默认按：

- 主站：`COM5`
- 从站：`COM6`

来启动，所以建议把 `CNCA0/CNCB0` 改成 `COM5/COM6`。

#### 第二步：确认 COM5 和 COM6 没有被占用

必须使用 powershell， 不能使用 cmd。

先执行：

```powershell
Get-CimInstance Win32_SerialPort | Select-Object DeviceID, Name
```

如果你已经有真实设备占用了 `COM5` 或 `COM6`，就不要继续用这两个号，应该换成别的空闲端口。

#### 第三步：进入 com0com 安装目录

```powershell
Set-Location "C:\Program Files (x86)\com0com"
```

如果你的安装目录不同，以实际安装路径为准。

#### 第四步：查看当前虚拟串口对

```powershell
.\setupc.exe list
       CNCA0 PortName=CNCA0
       CNCB0 PortName=CNCB0
```

#### 第五步：把端口名改成 COM5 和 COM6

注意：在 PowerShell 里，直接执行带参数的 `.exe` 时，前面可以加 `&`，也可以用 `.\` 方式执行。下面两种写法都可以。

写法一：

```powershell
.\setupc.exe change CNCA0 PortName=COM5
.\setupc.exe change CNCB0 PortName=COM6
```
如果有任何提示，选择继续就行。

写法二：

```powershell
& .\setupc.exe change CNCA0 PortName=COM5
& .\setupc.exe change CNCB0 PortName=COM6
```

如果你的默认端口名不是 `CNCA0/CNCB0`，把它替换成你 `list` 里看到的名字。

#### 第六步：再次确认结果

```powershell
Get-CimInstance Win32_SerialPort | Select-Object DeviceID, Name
```

你应该能看到类似：

```text
COM5     com0com - serial port emulator
COM6     com0com - serial port emulator
```

### 如果 setupc.exe 报找不到 com0com.inf

如果你看到类似错误：

```text
setup for com0com setupgetinfinformation(c:\users\...\com0com.inf) ERROR: 0x00000002
```

通常说明：

1. 你没有在 `com0com` 安装目录里运行 `setupc.exe`
2. 当前安装不完整，目录里缺少 `com0com.inf`

正确做法是先进入安装目录再执行：

```powershell
Set-Location "C:\Program Files (x86)\com0com"
.\setupc.exe list
```

如果目录里根本没有 `com0com.inf`，建议重新安装。

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

## Demo 和真实生产现场的区别

这个项目已经把 Modbus RTU 最核心的主从交互模型跑出来了，但它仍然是“教学型 demo”，不等于完整的工业现场通信系统。

### 哪些地方是一样的

这些能力和真实硬件场景是一致的，也是这个项目最有学习价值的部分：

- 主从结构一致
  - 主站发请求
  - 从站被动响应
  - 同一时刻只允许一个请求在途
- 功能码语义一致
  - `0x03` 读保持寄存器
  - `0x06` 写单个寄存器
  - `0x10` 写多个寄存器
- RTU 帧结构一致
  - 从站地址
  - 功能码
  - 数据区
  - CRC16
- CRC 校验逻辑一致
- 异常响应格式一致
- 寄存器地址、数量、字节数这些协议概念一致

换句话说，你现在已经学到的“协议层脑图”，在真实项目里是可以直接复用的。

### 哪些地方不一样

#### 1. 物理层不一样

当前 demo：

- 用的是 `com0com` 虚拟串口
- 本质上是同一台电脑里的软件回环

真实生产：

- 常见是 `RS-485`
- 真正走的是串口芯片、差分总线、接线端子、屏蔽线

真实现场会多出这些问题：

- A/B 线接反
- 接地问题
- 终端电阻和偏置电阻问题
- 线太长导致波形变差
- 电磁干扰导致 CRC 错误

这些在虚拟串口环境里基本不会自然出现。

#### 2. 时序要求更严格

当前 demo：

- 已经实现了 RTU 帧间隔处理
- 但还是偏“教学近似版”

真实生产：

- 更强调字符间隔和帧间隔
- `3.5` 个字符时间要按波特率动态计算
- 串口驱动、缓冲区、设备响应延迟都会影响帧边界判断

所以真实场景里经常会出现：

- 主站发太快
- 从站还没处理完
- 一帧被拆成多段到达
- 多帧挤在一起
- 某些从站响应很慢

#### 3. 从站设备行为不会这么“理想”

当前 demo 的从站：

- 功能稳定
- 行为可预测
- 寄存器表简单
- 一定按我们写的逻辑响应

真实设备可能：

- 只支持部分功能码
- 寄存器地址和文档不完全一致
- 某些寄存器只读
- 某些寄存器写入后不会立刻生效
- 某些设备忙的时候直接不响应
- 某些设备会返回厂商私有异常

也就是说，真实项目里很多工作是在“代码”和“设备手册”之间做对齐。

#### 4. 真实场景通常是一个主站对多个从站

当前 demo：

- 一个主站
- 一个从站

真实生产：

- 一个主站轮询多个从站
- 每个从站地址不同
- 每类数据点的轮询周期也不同

这时就会多出：

- 轮询调度优先级
- 总线吞吐量控制
- 某个从站离线时如何跳过
- 某个从站恢复后如何重新纳入调度

#### 5. 真实项目更强调容错和恢复

当前 demo：

- 已经有超时重试
- 适合学习主站基本控制流程

真实生产还要考虑：

- 连续失败次数统计
- 从站离线判定
- 恢复上线判定
- 通信质量统计
- 告警和事件记录
- 写命令是否允许重发

例如：

- 主站下发一个写命令后超时
- 你不能确定是“从站没收到”
- 还是“从站已经执行了，但响应丢了”

这在真实设备控制里非常关键，因为盲目重发可能造成重复动作。

#### 6. 真正的嵌入式从站资源更紧张

当前 demo：

- 运行在 PC 上
- Java + Netty + jSerialComm
- 内存和 CPU 比较充足

真实嵌入式从站：

- 可能运行在 MCU 上
- 常见是 C / C++ / RTOS / 裸机
- 更依赖中断、环形缓冲区、状态机

所以真实嵌入式代码通常会更关注：

- 每个字节什么时候到
- 缓冲区有没有溢出
- 定时器是否精确
- 中断和主循环怎样配合

### 一张对照表

| 维度 | 当前 demo | 真实生产现场 |
| --- | --- | --- |
| 传输介质 | `com0com` 虚拟串口 | `RS-485` 真实总线 |
| 主从数量 | 1 主 1 从 | 常见为 1 主多从 |
| 协议格式 | 标准 Modbus RTU | 标准 Modbus RTU |
| CRC | 有 | 有 |
| 功能码 | `0x03/0x06/0x10` | 视设备而定，可能更多也可能更少 |
| 帧间隔 | 教学近似版 | 按波特率严格控制 |
| 电气干扰 | 基本没有 | 常见且必须处理 |
| 从站行为 | 可控且稳定 | 厂商差异明显 |
| 容错 | 超时重试 | 离线判定、恢复、告警、统计 |
| 实现环境 | PC 上的 Java | MCU / 工控机 / PLC / 网关 |

### 你现在这个 demo 的学习价值到底在哪

这个项目最适合帮你建立下面这些基础能力：

1. 看懂 Modbus RTU 报文结构
2. 理解主从请求响应节奏
3. 掌握 `0x03 / 0x06 / 0x10` 的寄存器交互
4. 理解 CRC 和异常响应
5. 理解主站轮询、超时、重试这些基础主站逻辑

如果把学习阶段分层，可以这样理解：

- 现在这个项目解决的是“协议层入门”
- 下一阶段要补的是“工业现场可靠通信”
- 再下一阶段才是“真实嵌入式设备实现”

### 建议的下一步

如果你想让这个项目继续向真实生产靠近，建议按这个顺序继续：

1. 加多从站轮询
2. 把帧间隔改成按波特率动态计算
3. 加离线判定和恢复上线
4. 加原始十六进制收发日志
5. 最后接入真实 `RS-485` 硬件
