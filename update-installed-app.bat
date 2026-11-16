@echo off
setlocal
echo Building Localify release binary...
call npm run tauri build -- --no-bundle
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

taskkill /IM Localify.exe /F >nul 2>nul
timeout /t 1 /nobreak >nul

set "TARGET=%LOCALAPPDATA%\Programs\Localify"
if not exist "%TARGET%" mkdir "%TARGET%"
copy /Y "src-tauri\target\release\localify.exe" "%TARGET%\Localify.exe" >nul
if errorlevel 1 (
  echo Failed to update %TARGET%\Localify.exe
  exit /b 1
)
copy /Y "src-tauri\target\release\WebView2Loader.dll" "%TARGET%\WebView2Loader.dll" >nul
copy /Y "localify.ico" "%TARGET%\localify.ico" >nul

echo Updated %TARGET%\Localify.exe successfully.
endlocal
