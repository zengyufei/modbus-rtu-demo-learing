param(
    [string]$串口 = "COM6",
    [int]$波特率 = 9600
)

$ErrorActionPreference = "Stop"
$根目录 = Split-Path -Parent $PSScriptRoot
$从站目录 = Join-Path $根目录 "modbus-rtu-slave"
$Jar路径 = Join-Path $从站目录 "target\\modbus-rtu-slave-1.0-SNAPSHOT-jar-with-dependencies.jar"

Write-Host "[从站] 使用串口=$串口 波特率=$波特率"

if (-not (Test-Path $Jar路径)) {
    Write-Host "[从站] 未找到可执行 jar，开始构建..."
    Push-Location $从站目录
    try {
        & mvn package
    } finally {
        Pop-Location
    }
}

Push-Location $从站目录
try {
    & java -jar $Jar路径 $串口 $波特率
} finally {
    Pop-Location
}
