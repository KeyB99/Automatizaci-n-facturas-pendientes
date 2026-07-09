@echo off
:: =====================================================
:: Script de compilacion y ejecucion - Monitor Facturas
:: =====================================================

:: Configurar rutas
set "PROYECTO=g:\Automatizacion facturas faltantes"
set "JAVA_HOME=c:\jdk-17.0.2"
set "MAVEN_HOME=%PROYECTO%\maven\apache-maven-3.9.16"
set "PATH=%MAVEN_HOME%\bin;%JAVA_HOME%\bin;%PATH%"

echo.
echo ============================================================
echo   MONITOR DE FACTURAS FALTANTES - GASTONCITO
echo ============================================================
echo.

cd /d "%PROYECTO%"

:: Verificar Java y Maven
echo [1/3] Verificando herramientas...
java -version 2>&1 | findstr /i "version"
mvn -version 2>&1 | findstr /i "Apache Maven"
echo.

:: Compilar
echo [2/3] Compilando el proyecto...
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: La compilacion fallo. Revisa los mensajes de error arriba.
    pause
    exit /b 1
)

echo.
echo [3/3] Iniciando el monitor...
echo ============================================================
echo  El monitor se ejecutara cada 4 minutos.
echo  NO CIERRES ESTA VENTANA - aqui apareceran los logs.
echo  Para detener el monitor presiona Ctrl+C
echo ============================================================
echo.

java -jar "%PROYECTO%\target\invoice-monitor-1.0.0.jar"

echo.
echo El monitor se detuvo. Presiona cualquier tecla para cerrar...
pause
