# Protocol Walkthrough

This document explains one complete Modbus RTU exchange in this demo, byte by byte.

## Assumptions Used In This Demo

- Unit ID: `0x01`
- Serial line: `9600 8N1`
- Initial holding registers:
  - `register[0] = 10`
  - `register[1] = 20`
  - `register[2] = 30`
  - `register[3] = 40`
  - `register[4] = 50`
  - `register[5] = 60`

RTU frame layout is always:

```text
[unit id][function code][data ...][crc low][crc high]
```

Important: Modbus RTU sends CRC in little-endian order, so the low byte comes first.

## 1. Read Holding Registers: 0x03

Master asks for `register[0..3]`.

### Request

```text
01 03 00 00 00 04 44 09
```

Meaning:

- `01`: slave address / unit id
- `03`: function code = read holding registers
- `00 00`: start address = 0
- `00 04`: quantity = 4 registers
- `44 09`: CRC16(Modbus), low byte first

### Response

```text
01 03 08 00 0A 00 14 00 1E 00 28 6F CC
```

Meaning:

- `01`: unit id
- `03`: function code
- `08`: byte count = 8 bytes, because 4 registers x 2 bytes each
- `00 0A`: register[0] = 10
- `00 14`: register[1] = 20
- `00 1E`: register[2] = 30
- `00 28`: register[3] = 40
- `6F CC`: CRC

## 2. Write Single Register: 0x06

Master writes `register[1] = 1234`.

### Request

```text
01 06 00 01 04 D2 5A 97
```

Meaning:

- `01`: unit id
- `06`: function code = write single register
- `00 01`: target address = 1
- `04 D2`: value = `0x04D2` = 1234
- `5A 97`: CRC

### Response

For `0x06`, the slave echoes the request data back.

```text
01 06 00 01 04 D2 5A 97
```

After this response, the holding registers become:

- `register[0] = 10`
- `register[1] = 1234`
- `register[2] = 30`
- `register[3] = 40`
- `register[4] = 50`
- `register[5] = 60`

## 3. Read Again After 0x06

Master reads `register[0..3]` again to verify the write.

### Request

```text
01 03 00 00 00 04 44 09
```

### Response

```text
01 03 08 00 0A 04 D2 00 1E 00 28 E6 59
```

Meaning:

- `00 0A`: register[0] = 10
- `04 D2`: register[1] = 1234
- `00 1E`: register[2] = 30
- `00 28`: register[3] = 40

## 4. Write Multiple Registers: 0x10

Master writes:

- `register[2] = 200`
- `register[3] = 300`
- `register[4] = 400`

### Request

```text
01 10 00 02 00 03 06 00 C8 01 2C 01 90 67 53
```

Meaning:

- `01`: unit id
- `10`: function code = write multiple registers
- `00 02`: start address = 2
- `00 03`: quantity = 3 registers
- `06`: byte count = 6 data bytes
- `00 C8`: register[2] = 200
- `01 2C`: register[3] = 300
- `01 90`: register[4] = 400
- `67 53`: CRC

### Response

For `0x10`, the slave confirms only the start address and quantity.

```text
01 10 00 02 00 03 21 C8
```

Meaning:

- `01`: unit id
- `10`: function code
- `00 02`: start address = 2
- `00 03`: quantity written = 3
- `21 C8`: CRC

After this response, the holding registers become:

- `register[0] = 10`
- `register[1] = 1234`
- `register[2] = 200`
- `register[3] = 300`
- `register[4] = 400`
- `register[5] = 60`

## 5. Final Read Verification

Master reads `register[0..5]`.

### Request

```text
01 03 00 00 00 06 C5 C8
```

Meaning:

- `00 06`: quantity = 6 registers

### Response

```text
01 03 0C 00 0A 04 D2 00 C8 01 2C 01 90 00 3C AF C3
```

Meaning:

- `0C`: byte count = 12 bytes = 6 registers x 2 bytes
- `00 0A`: register[0] = 10
- `04 D2`: register[1] = 1234
- `00 C8`: register[2] = 200
- `01 2C`: register[3] = 300
- `01 90`: register[4] = 400
- `00 3C`: register[5] = 60
- `AF C3`: CRC

## What The Demo Is Teaching

This sequence shows the most important ideas in RTU learning:

- how a master initiates every exchange
- how function codes change the payload layout
- how register values are encoded as two-byte big-endian values
- how CRC is appended low-byte first
- how `0x06` echoes the written value
- how `0x10` acknowledges only start address and quantity
- how read-back verification is commonly used after a write