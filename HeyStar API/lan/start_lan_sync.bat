@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title HeyStar update helper

echo.
echo HeyStar update helper
echo Leave this window open, then press Update infos in the school system.
echo This file works from any folder and any Windows computer.
echo Folder: %~dp0
echo.

rem One file is enough on another PC: HeyStarUpdateHelper.exe already contains Python.
set "EXE=%~dp0HeyStarUpdateHelper.exe"
if exist "%EXE%" (
  echo Starting:
  echo   %EXE%
  echo.
  "%EXE%" %*
  exit /b %ERRORLEVEL%
)

echo HeyStarUpdateHelper.exe was not next to this file.
echo That program already contains Python. Copy it into this folder, or this
echo file will try to install Python and run heystar_lan_sync.py instead.
echo.

if not exist "%~dp0heystar_lan_sync.py" (
  echo heystar_lan_sync.py is also missing.
  echo Copy HeyStarUpdateHelper.exe into this folder and run this file again.
  echo.
  pause
  exit /b 1
)

if /I "%~1"=="install" call :install_startup

call :find_python
if not defined PY_EXE (
  echo Python 3.8 or newer was not found on this computer.
  echo Installing Python for this Windows user. This can take a few minutes.
  echo.
  call :install_python
  call :find_python
)

if not defined PY_EXE (
  echo Python is still missing. Copy HeyStarUpdateHelper.exe into this folder and run this file again.
  echo.
  pause
  exit /b 1
)

echo Using Python:
echo   %PY_EXE%
echo.

"%PY_EXE%" -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8788/health', timeout=2).read()" >nul 2>&1
if not errorlevel 1 (
  echo The helper is already running on this computer.
  echo Press Update infos now. You can close this window.
  echo.
  echo To start it automatically every time you sign in, run:
  echo   start_lan_sync.bat install
  echo.
  pause
  exit /b 0
)

echo Starting the helper on http://127.0.0.1:8788
echo When you see "HeyStar update helper", press Update infos.
echo.
"%PY_EXE%" "%~dp0heystar_lan_sync.py" %*
echo.
echo The helper stopped.
echo If the message above says it is already running, press Update infos.
echo Otherwise run this file again and leave the window open.
echo.
pause
exit /b 0

:install_startup
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ws = New-Object -ComObject WScript.Shell; $lnk = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Startup\HeyStar update helper.lnk'; $s = $ws.CreateShortcut($lnk); $s.TargetPath = '%~f0'; $s.WorkingDirectory = '%~dp0'; $s.WindowStyle = 1; $s.Description = 'HeyStar LAN update helper'; $s.Save()"
if errorlevel 1 (
  echo Could not add the helper to Windows startup.
) else (
  echo The helper will also start each time you sign in to Windows.
)
echo.
exit /b 0

:install_python
where winget >nul 2>&1
if errorlevel 1 goto :install_embed
winget install -e --id Python.Python.3.12 --scope user --accept-package-agreements --accept-source-agreements --disable-interactivity
call :find_python
if defined PY_EXE exit /b 0

:install_embed
echo winget could not install Python. Downloading the official Python embed package.
set "EMBED=%~dp0python-embed"
set "ZIP=%TEMP%\python-embed-heystar.zip"
if not exist "%EMBED%\python.exe" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=$env:TEMP+'\python-embed-heystar.zip'; $dest='%~dp0python-embed'; Invoke-WebRequest -Uri 'https://www.python.org/ftp/python/3.12.8/python-3.12.8-embed-amd64.zip' -OutFile $zip; New-Item -ItemType Directory -Force -Path $dest | Out-Null; Expand-Archive -LiteralPath $zip -DestinationPath $dest -Force; $pth = Join-Path $dest 'python312._pth'; if (Test-Path $pth) { $txt = Get-Content -Raw $pth; $txt = $txt -replace '#import site','import site'; Set-Content -Path $pth -Value $txt -Encoding ascii }"
)
if exist "%EMBED%\python.exe" set "PY_EXE=%EMBED%\python.exe"
exit /b 0

:find_python
set "PY_EXE="
if exist "%~dp0python-embed\python.exe" (
  call :try_python "%~dp0python-embed\python.exe"
)
if defined PY_EXE exit /b 0

where py >nul 2>&1
if not errorlevel 1 (
  py -3 -c "import sys; raise SystemExit(0 if sys.version_info[:2] >= (3, 8) else 1)" >nul 2>&1
  if not errorlevel 1 (
    for /f "delims=" %%I in ('py -3 -c "import sys; print(sys.executable)" 2^>nul') do (
      if not defined PY_EXE set "PY_EXE=%%I"
    )
  )
)
if defined PY_EXE exit /b 0

where python >nul 2>&1
if not errorlevel 1 (
  for /f "delims=" %%P in ('where python 2^>nul') do (
    if not defined PY_EXE call :try_python "%%P"
  )
)
if defined PY_EXE exit /b 0

where python3 >nul 2>&1
if not errorlevel 1 (
  for /f "delims=" %%P in ('where python3 2^>nul') do (
    if not defined PY_EXE call :try_python "%%P"
  )
)
if defined PY_EXE exit /b 0

call :scan_dir "%LOCALAPPDATA%\Programs\Python"
if defined PY_EXE exit /b 0
call :scan_dir "%ProgramFiles%"
if defined PY_EXE exit /b 0
call :scan_dir "%ProgramFiles(x86)%"
if defined PY_EXE exit /b 0
call :scan_dir "C:"
if defined PY_EXE exit /b 0

for %%R in (
  "HKCU\Software\Python\PythonCore"
  "HKLM\Software\Python\PythonCore"
  "HKLM\Software\Wow6432Node\Python\PythonCore"
) do (
  if not defined PY_EXE (
    for /f "tokens=2*" %%A in ('reg query %%~R /s /v ExecutablePath 2^>nul ^| findstr /I "REG_SZ"') do (
      if not defined PY_EXE call :try_python "%%B"
    )
  )
)
exit /b 0

:try_python
set "CAND=%~1"
if not defined CAND exit /b 0
set "CHECK=%CAND:WindowsApps=%"
if /I not "%CHECK%"=="%CAND%" exit /b 0
if not exist "%CAND%" exit /b 0
"%CAND%" -c "import sys; raise SystemExit(0 if sys.version_info[:2] >= (3, 8) else 1)" >nul 2>&1
if errorlevel 1 exit /b 0
set "PY_EXE=%CAND%"
exit /b 0

:scan_dir
if not exist "%~1\" exit /b 0
for /d %%D in ("%~1\Python3*") do (
  if not defined PY_EXE if exist "%%~D\python.exe" call :try_python "%%~D\python.exe"
)
for /d %%D in ("%~1\Python\Python3*") do (
  if not defined PY_EXE if exist "%%~D\python.exe" call :try_python "%%~D\python.exe"
)
exit /b 0
