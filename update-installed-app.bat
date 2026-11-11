@echo off
setlocal
title Localify Fast Updater

echo ===================================================
echo     Localify In-Place Fast Updater (No Reinstall)
echo ===================================================
echo.

set "TARGET_JAR=%LOCALAPPDATA%\Programs\Localify\app\localify-player-1.0.jar"
set "DIST_JAR=%~dp0dist\Localify\app\localify-player-1.0.jar"
set "BUILD_JAR=%~dp0target\localify-player-1.0.jar"
set "TARGET_CFG=%LOCALAPPDATA%\Programs\Localify\app\Localify.cfg"
set "DIST_CFG=%~dp0dist\Localify\app\Localify.cfg"
set "TARGET_EXE=%LOCALAPPDATA%\Programs\Localify\Localify.exe"
set "DIST_EXE=%~dp0dist\Localify\Localify.exe"

if not exist "%BUILD_JAR%" (
    echo [INFO] target\localify-player-1.0.jar tidak ditemukan. Menjalankan maven build...
    call "%~dp0mvnw.cmd" clean package -DskipTests
    if errorlevel 1 (
        echo [ERROR] Build Maven gagal.
        pause
        exit /b 1
    )
)

echo [1/3] Menutup Localify jika sedang berjalan...
taskkill /IM Localify.exe /F >nul 2>&1
ping 127.0.0.1 -n 2 >nul

echo [2/3] Mengganti file JAR aplikasi dengan versi terbaru...
if exist "%DIST_JAR%" (
    copy /Y "%BUILD_JAR%" "%DIST_JAR%" >nul
    echo       - Updated: dist\Localify\app\localify-player-1.0.jar
)

if exist "%TARGET_JAR%" (
    copy /Y "%BUILD_JAR%" "%TARGET_JAR%" >nul
    echo       - Updated: %TARGET_JAR%
) else (
    echo       - Catatan: Folder instalasi Localify di AppData belum ada.
)

if exist "%TARGET_CFG%" if exist "%DIST_CFG%" (
    copy /Y "%DIST_CFG%" "%TARGET_CFG%" >nul
    echo       - Updated: %TARGET_CFG%
)

if exist "%TARGET_EXE%" if exist "%DIST_EXE%" (
    copy /Y "%DIST_EXE%" "%TARGET_EXE%" >nul
    echo       - Updated: %TARGET_EXE%
)

echo [3/3] Membuka Localify versi terbaru...
if exist "%LOCALAPPDATA%\Programs\Localify\Localify.exe" (
    pushd "%LOCALAPPDATA%\Programs\Localify"
    start "" "Localify.exe"
    popd
) else if exist "%~dp0dist\Localify\Localify.exe" (
    pushd "%~dp0dist\Localify"
    start "" "Localify.exe"
    popd
)

echo.
echo ===================================================
echo [SUKSES] Localify berhasil diperbarui!
echo Semua data lagu, playlist, cover art, dan settings
echo tetap aman 100%% di folder pengguna (.localify).
echo ===================================================
echo.
ping 127.0.0.1 -n 3 >nul
