#!/bin/bash

echo "================================================================="
echo "  KICH HOAT HE THONG KIEM THU TU DONG - MINI SHOPEE BACKEND      "
echo "================================================================="
echo ""

# 1. Don dep container cu tranh xung dot
echo "[1/4] Dang kiem tra va don dep cac container cu de tranh xung dot..."
docker stop shopee_mini_db shopee_mini_app shopee_mini_redis shopee_mini_rabbitmq >/dev/null 2>&1
docker rm shopee_mini_db shopee_mini_app shopee_mini_redis shopee_mini_rabbitmq >/dev/null 2>&1
echo ""

# 2. Khoi chay Docker Compose
echo "[2/4] Dang tu dong tai va khoi chay ung dung..."
if command -v docker-compose &> /dev/null; then
    docker-compose up -d
else
    docker compose up -d
fi

if [ $? -ne 0 ]; then
    echo ""
    echo "[LOI] Khong the khoi dong he thong."
    echo "Luu y: Vui long dam bao rang Docker Service da duoc bat va co quyen sudo!"
    echo ""
    exit 1
fi
echo ""

# 3. Cho ung dung start
echo "[3/4] Dang cho ung dung khoi tao trong 5 giay..."
sleep 5
echo ""

# 4. Tu dong mo trinh duyet
echo "[4/4] Dang mo cong tai lieu kiem thu tren trinh duyet..."
if command -v xdg-open &> /dev/null; then
    xdg-open http://localhost:8080
elif command -v gnome-open &> /dev/null; then
    gnome-open http://localhost:8080
else
    echo "He thong khong ho tro tu dong mo trinh duyet."
    echo "Vui long mo trinh duyet thu cong va truy cap dia chi: http://localhost:8080"
fi
echo ""

echo "================================================================="
echo "  KICH HOAT HE THONG THANH CONG!                                 "
echo "  - Cong tai lieu: http://localhost:8080                         "
echo "================================================================="
