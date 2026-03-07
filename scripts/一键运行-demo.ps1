param(
    [string]$主站串口 = "COM5",
    [string]$从站串口 = "COM6",
    [int]$波特率 = 9600
)

$ErrorActionPreference = "Stop"

Write-Host "[一键运行] 主站串口=$主站串口 从站串口=$从站串口 波特率=$波特率"

$主站脚本 = Join-Path $PSScriptRoot "启动主站.ps1"
$从站脚本 = Join-Path $PSScriptRoot "启动从站.ps1"

Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$从站脚本`"", "-串口", $从站串口, "-波特率", $波特率
Start-Sleep -Seconds 2
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$主站脚本`"", "-串口", $主站串口, "-波特率", $波特率

Write-Host "[一键运行] 已分别打开主站和从站窗口。"
