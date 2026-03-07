param(
    [string]$串口 = "COM5",
    [int]$波特率 = 9600
)

$ErrorActionPreference = "Stop"
$根目录 = Split-Path -Parent $PSScriptRoot
$主站目录 = Join-Path $根目录 "modbus-rtu-master"
$Jar路径 = Join-Path $主站目录 "target\\modbus-rtu-master-1.0-SNAPSHOT-jar-with-dependencies.jar"

Write-Host "[主站] 使用串口=$串口 波特率=$波特率"

if (-not (Test-Path $Jar路径)) {
    Write-Host "[主站] 未找到可执行 jar，开始构建..."
    Push-Location $主站目录
    try {
        & mvn package
    } finally {
        Pop-Location
    }
}

Push-Location $主站目录
try {
    & java -jar $Jar路径 $串口 $波特率
} finally {
    Pop-Location
}
