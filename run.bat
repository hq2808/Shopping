@echo off
title Mini Shopee - Quick Start Tool
color 0B

echo =================================================================
echo   KICH HOAT HE THONG KIEM THU TU DONG - MINI SHOPEE BACKEND
echo =================================================================
echo.

:: 1. Don dep container cu tranh xung dot
echo [1/4] Dang kiem tra va don dep cac container cu de tranh xung dot...
docker stop shopee_mini_db shopee_mini_app shopee_mini_redis shopee_mini_rabbitmq >nul 2>&1
docker rm shopee_mini_db shopee_mini_app shopee_mini_redis shopee_mini_rabbitmq >nul 2>&1
echo.

:: 2. Khoi chay Docker Compose
echo [2/4] Dang tu dong tai va khoi chay ung dung...
docker compose up -d
if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo [LOI] Khong the khoi dong he thong.
    echo Luu y: Vui long mo phan mem "Docker Desktop" tren may truoc khi chay file nay!
    echo.
    pause
    exit /b
)
echo.

:: 3. Cho ung dung start
echo [3/4] Dang cho ung dung khoi tao trong 5 giay...
timeout /t 5 /nobreak >nul
echo.

:: 4. Tu dong mo trinh duyet
echo [4/4] Dang mo cong tai lieu kiem thu tren trinh duyet...
start http://localhost:8080
echo.

color 0A
echo =================================================================
echo   KICH HOAT HE THONG THANH CONG!
echo   - Cong tai lieu da duoc mo tren trinh duyet: http://localhost:8080
echo   - An phim bat ky de dong cua so nay.
echo =================================================================
pause >nul
