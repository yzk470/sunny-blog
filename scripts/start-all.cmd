@echo off
echo Starting Sunny Blog (backend + frontend) in two windows...
start "Sunny Blog Backend" cmd /k "%~dp0start-backend.cmd"
timeout /t 2 /nobreak >nul
start "Sunny Blog Frontend" cmd /k "%~dp0start-frontend.cmd"
echo.
echo   Frontend: http://127.0.0.1:5173
echo   Backend : http://127.0.0.1:8090
echo   Admin   : admin / admin123
