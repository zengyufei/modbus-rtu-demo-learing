# Modbus 异常码说明

本页说明两件事：

1. 常见 Modbus 异常码是什么意思
2. 在当前 demo 里，哪些异常能真实触发，怎么触发

## 异常响应长什么样

Modbus 异常响应格式：

```text
[unit id][function code | 0x80][exception code][crc low][crc high]
```

也就是说：

- 功能码最高位置 1
- 数据区只剩下 1 字节异常码

例如原请求是 `0x03`，异常响应功能码就会变成 `0x83`。

## 常见异常码

### 0x01 Illegal Function

含义：

- 从站不支持这个功能码

### 0x02 Illegal Data Address

含义：

- 地址超范围
- 或者地址 + 数量组合超出了寄存器边界

### 0x03 Illegal Data Value

含义：

- 数据值本身不合法
- 例如数量为 0、字节数不匹配等

### 0x04 Slave Device Failure

含义：

- 从站内部执行失败
- 更接近设备内部故障，而不是协议格式错误

## 本 demo 当前真正会返回哪些异常

当前这份 demo 明确会返回：

- `0x01 Illegal Function`
- `0x02 Illegal Data Address`

当前没有主动返回：

- `0x03 Illegal Data Value`
- `0x04 Slave Device Failure`

也就是说，这份教学代码里你最容易学到的是：

- 不支持功能码时怎么回异常
- 寄存器地址越界时怎么回异常

## 如何触发 0x01 Illegal Function

当前从站只支持：

- `0x03`
- `0x06`
- `0x10`

所以你只要发一个别的功能码，就会返回 `0x01`。

### 示例请求

```text
01 45 00 00 10 0D
```

含义：

- `01`：站号
- `45`：本 demo 不支持的功能码
- `00 00`：随便带两字节数据
- `10 0D`：CRC

### 示例异常响应

```text
01 C5 01 B2 90
```

含义：

- `C5` = `0x45 | 0x80`
- `01` = Illegal Function

### 在 demo 中怎么触发

最直接的方式是修改：

- `modbus-rtu-master/src/main/java/com/example/modbus/master/handler/主站处理器.java`

在 `pollPlan` 里加一个自定义非法功能码命令，或者临时把某个功能码改成 `0x45`。

## 如何触发 0x02 Illegal Data Address

### 场景 A：读保持寄存器越界

从站当前只有 6 个保持寄存器：`0..5`。
如果你请求 `0..9`，就越界了。

#### 示例请求

```text
01 03 00 00 00 0A C5 CD
```

#### 示例异常响应

```text
01 83 02 C0 F1
```

### 场景 B：写单个寄存器地址越界

例如写 `register[8]`。

#### 示例请求

```text
01 06 00 08 00 01 C9 C8
```

#### 示例异常响应

```text
01 86 02 C3 A1
```

### 场景 C：写多个寄存器越界

例如从 `register[4]` 开始写 3 个寄存器，
实际会覆盖 `4, 5, 6`，其中 `6` 已经越界。

#### 示例请求

```text
01 10 00 04 00 03 06 00 01 00 02 00 03 7B 54
```

#### 示例异常响应

```text
01 90 02 CD C1
```

## 在 demo 中怎么触发 0x02

你可以直接改主站轮询计划中的参数：

### 读越界

把：

```java
PollCommand.read(0, 4)
```

改成：

```java
PollCommand.read(0, 10)
```

### 写单个寄存器越界

把：

```java
PollCommand.writeSingle(1, 1234)
```

改成：

```java
PollCommand.writeSingle(8, 1234)
```

### 写多个寄存器越界

把：

```java
PollCommand.writeMultiple(2, new int[] {200, 300, 400})
```

改成：

```java
PollCommand.writeMultiple(4, new int[] {1, 2, 3})
```

## 为什么当前 demo 不返回 0x03

因为当前从站把很多“不合法值”场景直接合并成了地址非法处理，例如：

- `quantity <= 0`
- `byteCount != quantity * 2`

这些现在统一返回 `0x02`。

如果你想更贴近规范，可以把它们拆开：

- 地址越界返回 `0x02`
- 数量非法、字节数不匹配返回 `0x03`

## 为什么当前 demo 不返回 0x04

因为 demo 里没有模拟设备内部故障。

如果你想练这个异常，可以在人为制造内部错误时返回：

- `0x04 Slave Device Failure`

例如：

- 模拟 EEPROM 写失败
- 模拟寄存器后端服务异常
- 模拟从站内部状态机错误