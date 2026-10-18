@echo off
setlocal
cd /d "%~dp0..\frontend"
if not exist "node_modules" (
  echo [Sunny Blog] Installing frontend dependencies...
  call npm install
  if errorlevel 1 (
    echo npm install failed.
    pause
    exit /b 1
  )
)
echo [Sunny Blog] Starting frontend on http://127.0.0.1:5173
call npm run dev
pause
