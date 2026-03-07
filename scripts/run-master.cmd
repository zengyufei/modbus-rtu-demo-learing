@echo off
setlocal
chcp 65001 >nul

for %%I in ("%~dp0..") do set "ROOT_DIR=%%~fI"
set "MASTER_DIR=%ROOT_DIR%\modbus-rtu-master"
set "PORT=%~1"
set "BAUD=%~2"

if "%PORT%"=="" set "PORT=COM5"
if "%BAUD%"=="" set "BAUD=9600"

set "JAR_PATH=%MASTER_DIR%\target\modbus-rtu-master-1.0-SNAPSHOT-jar-with-dependencies.jar"

echo [MASTER] port=%PORT% baud=%BAUD%
set "JAVA_TOOL_OPTIONS=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 %JAVA_TOOL_OPTIONS%"

if not exist "%JAR_PATH%" (
    echo [MASTER] jar not found, building...
    pushd "%MASTER_DIR%"
    call mvn package
    if errorlevel 1 (
        popd
        echo [MASTER] build failed.
        exit /b 1
    )
    popd
)

pushd "%MASTER_DIR%"
java -jar "%JAR_PATH%" %PORT% %BAUD%
set "EXIT_CODE=%ERRORLEVEL%"
popd

exit /b %EXIT_CODE%
