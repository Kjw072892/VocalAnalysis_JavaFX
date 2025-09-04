@echo off
setlocal ENABLEDELAYEDEXPANSION

REM ====== CONFIG ======
set "PY_VER_PRIMARY=3.13.5"
set "PY_VER_FALLBACK=3.12.6"
set "PY_SHORT_PRIMARY=313"
set "PY_SHORT_FALLBACK=312"
set "ARCH=amd64"
set "VENV_DIR=.venv"
set "REQ_FILE=requirements.txt"
set "DEFAULT_PKGS=praat-parselmouth numpy matplotlib"
set "REQ_HASH_FILE=%VENV_DIR%\.req.sha256"
set "READY_MARK=%VENV_DIR%\.ready"

REM ====== HELPERS ======
:log
echo [setup] %*
exit /b 0

:download
REM %1=url  %2=output
powershell -NoLogo -NoProfile -Command ^
  "Invoke-WebRequest -Uri '%~1' -OutFile '%~2' -UseBasicParsing" || exit /b 1
exit /b 0

:install_python_allusers
"%~1" /quiet InstallAllUsers=1 PrependPath=1 Include_launcher=1 Include_pip=1 Shortcuts=0 || exit /b 1
exit /b 0

:install_python_user
"%~1" /quiet InstallAllUsers=0 PrependPath=1 Include_launcher=1 Include_pip=1 Shortcuts=0 || exit /b 1
exit /b 0

:find_python
REM Tries to resolve python for short version (e.g., 313); sets PY_EXE and returns 0 if found.
set "PY_EXE="
for %%D in ("%ProgramFiles%\Python\Python%1" "%LocalAppData%\Programs\Python\Python%1") do (
  if exist "%%~fD\python.exe" (
    set "PY_EXE=%%~fD\python.exe"
    goto :found
  )
)
for /f "tokens=*" %%P in ('where py 2^>NUL') do (
  py -%1 -V 1>nul 2>nul && ( set "PY_EXE=py -%1" & goto :found )
)
for /f "tokens=*" %%P in ('where python 2^>NUL') do (
  set "PY_EXE=%%~fP"
  goto :found
)
exit /b 1
:found
exit /b 0

:hash_file
REM %1=input  -> sets HASH_OUT variable with SHA256 of the file (no spaces)
set "HASH_OUT="
if not exist "%~1" exit /b 1
for /f "skip=1 tokens=1" %%H in ('certutil -hashfile "%~1" SHA256 ^| findstr /R "^[0-9A-F][0-9A-F]"') do (
  set "HASH_OUT=%%H"
  goto :hashdone
)
:hashdone
if not defined HASH_OUT exit /b 1
exit /b 0

:ensure_python
REM Tries primary; if not found, downloads/installs; if that fails, fallback.
call :find_python %PY_SHORT_PRIMARY%
if errorlevel 1 (
  call :log "Python %PY_VER_PRIMARY% not found. Attempting install."
  set "WORK=%TEMP%\python_inst"
  if not exist "%WORK%" mkdir "%WORK%" >nul 2>&1
  set "PY_DL=%WORK%\python-%PY_VER_PRIMARY%-%ARCH%.exe"
  set "PY_URL=https://www.python.org/ftp/python/%PY_VER_PRIMARY%/python-%PY_VER_PRIMARY%-%ARCH%.exe"
  call :download "%PY_URL%" "%PY_DL%" || (call :log "Download failed." & goto :fallback)
  call :install_python_allusers "%PY_DL%" || (
    call :log "All-users install failed. Trying per-user."
    call :install_python_user "%PY_DL%" || (call :log "Install failed." & goto :fallback)
  )
  set "PATH=%ProgramFiles%\Python\Python%PY_SHORT_PRIMARY%;%ProgramFiles%\Python\Python%PY_SHORT_PRIMARY%\Scripts;%LocalAppData%\Programs\Python\Python%PY_SHORT_PRIMARY%;%LocalAppData%\Programs\Python\Python%PY_SHORT_PRIMARY%\Scripts;%PATH%"
  call :find_python %PY_SHORT_PRIMARY% || goto :fallback
)
goto :py_ok

:fallback
call :log "Falling back to Python %PY_VER_FALLBACK%."
call :find_python %PY_SHORT_FALLBACK%
if errorlevel 1 (
  set "WORK=%TEMP%\python_inst"
  if not exist "%WORK%" mkdir "%WORK%" >nul 2>&1
  set "PY_DL=%WORK%\python-%PY_VER_FALLBACK%-%ARCH%.exe"
  set "PY_URL=https://www.python.org/ftp/python/%PY_VER_FALLBACK%/python-%PY_VER_FALLBACK%-%ARCH%.exe"
  call :download "%PY_URL%" "%PY_DL%" || (call :log "Download failed." & exit /b 1)
  call :install_python_allusers "%PY_DL%" || (
    call :install_python_user "%PY_DL%" || (call :log "Install failed." & exit /b 1)
  )
  set "PATH=%ProgramFiles%\Python\Python%PY_SHORT_FALLBACK%;%ProgramFiles%\Python\Python%PY_SHORT_FALLBACK%\Scripts;%LocalAppData%\Programs\Python\Python%PY_SHORT_FALLBACK%;%LocalAppData%\Programs\Python\Python%PY_SHORT_FALLBACK%\Scripts;%PATH%"
  call :find_python %PY_SHORT_FALLBACK% || (call :log "Could not locate fallback install." & exit /b 1)
)
:py_ok
call :log "Using Python: %PY_EXE%"
exit /b 0

:ensure_venv
REM Uses existing venv if present; otherwise creates it.
set "VENV_PY=%CD%\%VENV_DIR%\Scripts\python.exe"
if exist "%VENV_PY%" (
  call :log "Found existing venv: %VENV_DIR%"
  goto :venv_ok
)
call :log "Creating venv at %VENV_DIR%..."
%PY_EXE% -m venv "%VENV_DIR%" || (call :log "venv create failed." & exit /b 1)
set "VENV_PY=%CD%\%VENV_DIR%\Scripts\python.exe"
"%VENV_PY%" -m pip install --upgrade pip setuptools wheel || (call :log "pip bootstrap failed." & exit /b 1)
:venv_ok
exit /b 0

:requirements_up_to_date
REM Returns 0 if no install needed (requirements unchanged & imports OK), else 1
if exist "%READY_MARK%" (
  if exist "%REQ_FILE%" (
    call :hash_file "%REQ_FILE%"
    if errorlevel 1 exit /b 1
    if not exist "%REQ_HASH_FILE%" exit /b 1
    for /f "usebackq delims=" %%X in ("%REQ_HASH_FILE%") do set "OLDHASH=%%X"
    if /I "!HASH_OUT!"=="!OLDHASH!" (
      "%VENV_PY%" -c "import sys; ok=0
try:
    import parselmouth, numpy, matplotlib
except Exception:
    ok=1
sys.exit(ok)" && exit /b 0
    )
  ) else (
    REM No requirements file; verify defaults present
    "%VENV_PY%" -c "import sys; ok=0
try:
    import parselmouth, numpy, matplotlib
except Exception:
    ok=1
sys.exit(ok)" && exit /b 0
  )
)
exit /b 1

:install_requirements_if_needed
REM Installs only when REQ changed or packages missing
call :requirements_up_to_date
if not errorlevel 1 (
  call :log "Requirements are up-to-date. Skipping installs."
  exit /b 0
)

if exist "%REQ_FILE%" (
  call :log "Installing from %REQ_FILE% (changes detected or missing pkgs)..."
  "%VENV_PY%" -m pip install -r "%REQ_FILE%" || (
    call :log "pip failed, retrying once..."
    "%VENV_PY%" -m pip install -r "%REQ_FILE%" || (call :log "pip install failed." & exit /b 2)
  )
  call :hash_file "%REQ_FILE%" || (call :log "Hashing failed." & exit /b 0)
  > "%REQ_HASH_FILE%" echo %HASH_OUT%
) else (
  call :log "No %REQ_FILE% found. Ensuring defaults: %DEFAULT_PKGS%"
  "%VENV_PY%" -m pip install %DEFAULT_PKGS% || (call :log "pip install (defaults) failed." & exit /b 2)
  REM Write a stable marker so we can short-circuit next time
  > "%REQ_HASH_FILE%" echo DEFAULTS-%DEFAULT_PKGS%
)

REM Post-install sanity check: fail fast if imports still broken
"%VENV_PY%" -c "import parselmouth, numpy, matplotlib" || (call :log "Post-install import failed." & exit /b 2)

REM mark ready
> "%READY_MARK%" echo ready
exit /b 0

REM ====== MAIN ======
call :ensure_python || exit /b 1
call :ensure_venv   || exit /b 1
call :install_requirements_if_needed
if errorlevel 1 exit /b 1

call :log "SUCCESS: venv ready at ""%VENV_DIR%"". Use: ""%VENV_DIR%\Scripts\python.exe"""

REM Optional: quick versions log for debugging
"%VENV_PY%" -m pip --version
"%VENV_PY%" -c "import sys; print('Python', sys.version)"
"%VENV_PY%" -c "import numpy, matplotlib; import parselmouth; print('numpy', numpy.__version__, 'matplotlib', matplotlib.__version__)"

exit /b 0
