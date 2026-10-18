@echo off
setlocal
cd /d "%~dp0..\backend"
if not exist "target\sunny-blog-1.0.0.jar" (
  echo [Sunny Blog] Building backend...
  call mvn -DskipTests package
  if errorlevel 1 (
    echo Backend build failed.
    pause
    exit /b 1
  )
)
echo [Sunny Blog] Starting backend with local MySQL (profile: local)
echo [Sunny Blog] URL: http://127.0.0.1:8090
java -jar "target\sunny-blog-1.0.0.jar" --spring.profiles.active=local
pause
