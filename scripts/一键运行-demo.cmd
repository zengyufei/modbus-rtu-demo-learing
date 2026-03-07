@echo off
setlocal
chcp 65001 >nul

for %%I in ("%~dp0..") do set "ROOT_DIR=%%~fI"
set "SCRIPTS_DIR=%ROOT_DIR%\scripts"
set "MASTER_PORT=%~1"
set "SLAVE_PORT=%~2"
set "BAUD=%~3"

if "%MASTER_PORT%"=="" set "MASTER_PORT=COM5"
if "%SLAVE_PORT%"=="" set "SLAVE_PORT=COM6"
if "%BAUD%"=="" set "BAUD=9600"

echo [RUN-ALL] master=%MASTER_PORT% slave=%SLAVE_PORT% baud=%BAUD%
start "Modbus RTU Slave" cmd /k call "%SCRIPTS_DIR%\run-slave.cmd" %SLAVE_PORT% %BAUD%
timeout /t 2 /nobreak >nul
start "Modbus RTU Master" cmd /k call "%SCRIPTS_DIR%\run-master.cmd" %MASTER_PORT% %BAUD%

echo [RUN-ALL] master and slave windows started.
exit /b 0
