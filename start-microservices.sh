#!/bin/bash

echo "=== SoftBank Hackathon 마이크로서비스 시작 ==="

# Java 환경 설정
export JAVA_HOME=/mnt/d/projects/softbank/jdk-17.0.12+7
export PATH=$JAVA_HOME/bin:$PATH

echo "1. 전체 프로젝트 빌드 중..."
./gradlew clean build -x test

if [ $? -ne 0 ]; then
    echo "❌ 빌드 실패!"
    exit 1
fi

echo "2. Docker 이미지 생성 중..."
docker build -t softbank/server ./server
docker build -t softbank/gateway ./gateway
docker build -t softbank/fe ./fe
docker build -t softbank/deploy ./deploy
docker build -t softbank/user ./user

echo "3. 기존 컨테이너 정리 중..."
docker-compose down

echo "4. 마이크로서비스 시작 중..."
docker-compose up -d

echo "5. 서비스 상태 확인 중..."
sleep 10
docker-compose ps

echo ""
echo "=== 서비스 접속 정보 ==="
echo "🔍 Eureka Server: http://localhost:8761"
echo "🚪 API Gateway:   http://localhost:8080"
echo "🖥️  Frontend:     http://localhost:8081"
echo "🚀 Deploy:        http://localhost:8082"
echo "👤 User:          http://localhost:8083"
echo ""
echo "✅ 모든 서비스가 시작되었습니다!"
echo "서비스 로그 확인: docker-compose logs -f [service-name]"
echo "서비스 중지: docker-compose down"