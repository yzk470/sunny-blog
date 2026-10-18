@echo off
echo Starting Sunny Blog with local MySQL (backend + frontend)...
start "Sunny Blog Backend (MySQL)" cmd /k "%~dp0start-backend-mysql.cmd"
timeout /t 2 /nobreak >nul
start "Sunny Blog Frontend" cmd /k "%~dp0start-frontend.cmd"
echo.
echo   Frontend: http://127.0.0.1:5173
echo   Backend : http://127.0.0.1:8090 (MySQL / local profile)
echo   Admin   : admin / admin123
