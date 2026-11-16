@echo off
setlocal
echo Building Localify (TypeScript + Rust)...
call npm run tauri build -- --no-bundle
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

if not exist "dist\Localify" mkdir "dist\Localify"
copy /Y "src-tauri\target\release\localify.exe" "dist\Localify\Localify.exe" >nul
copy /Y "src-tauri\target\release\WebView2Loader.dll" "dist\Localify\WebView2Loader.dll" >nul
copy /Y "localify.ico" "dist\Localify\localify.ico" >nul
if exist "LICENSE" copy /Y "LICENSE" "dist\Localify\LICENSE" >nul

set "ISCC="
if exist "%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe" set "ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if not defined ISCC if exist "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" set "ISCC=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if not defined ISCC if exist "C:\Program Files\Inno Setup 6\ISCC.exe" set "ISCC=C:\Program Files\Inno Setup 6\ISCC.exe"

if defined ISCC (
  echo Packaging installer with Inno Setup 6...
  "%ISCC%" "localify-installer.iss"
  if errorlevel 1 exit /b 1
  echo Installer created in dist\installer\
) else (
  echo Inno Setup 6 not found. Skipping setup packaging.
)
endlocal
