@echo off
setlocal

echo =======================================================
echo          Localify Windows Installer Builder
echo =======================================================
echo.

cd /d "%~dp0"

echo [1/4] Building shaded JAR with Maven...
call mvnw.cmd package -DskipTests
if errorlevel 1 (
    echo [ERROR] Maven build failed!
    exit /b 1
)

echo.
echo [2/4] Preparing package-input...
if not exist "target\package-input" mkdir "target\package-input"
copy /y "target\localify-player-1.0.jar" "target\package-input\" >nul

echo.
echo [3/4] Creating self-contained Windows app-image with jpackage...
if exist "dist\Localify" rd /s /q "dist\Localify"
call jpackage --type app-image ^
  --name "Localify" ^
  --input "target\package-input" ^
  --main-jar "localify-player-1.0.jar" ^
  --main-class "com.localify.App" ^
  --dest "dist" ^
  --icon "localify.ico" ^
  --vendor "Localify Team" ^
  --app-version "1.0.0" ^
  --copyright "Copyright (c) 2026 Localify" ^
  --description "Localify" ^
  --add-modules "java.base,java.desktop,java.logging,java.net.http,java.scripting,jdk.unsupported,java.management,java.naming,java.xml,jdk.crypto.ec" ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --java-options "-Dprism.order=d3d,sw" ^
  --java-options "-Xms32m" ^
  --java-options "-Xmx256m" ^
  --java-options "-XX:+UseSerialGC" ^
  --java-options "-Dprism.maxvram=96m" ^
  --java-options "-Dprism.targetvram=64m"
if errorlevel 1 (
    echo [ERROR] jpackage app-image creation failed!
    exit /b 1
)

echo.
echo [4/4] Compiling setup installer with Inno Setup...
set "ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if exist "%ISCC%" goto run_iscc
set "ISCC=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if exist "%ISCC%" goto run_iscc
set "ISCC=C:\Program Files\Inno Setup 6\ISCC.exe"
if exist "%ISCC%" goto run_iscc

echo [ERROR] Inno Setup compiler (ISCC.exe) not found!
exit /b 1

:run_iscc
"%ISCC%" localify-installer.iss
if errorlevel 1 (
    echo [ERROR] Inno Setup compilation failed!
    exit /b 1
)

echo.
echo =======================================================
echo  SUCCESS: Installer created successfully!
echo  Installer Location: dist\installer\Localify-Setup-1.0.exe
echo =======================================================
echo.
