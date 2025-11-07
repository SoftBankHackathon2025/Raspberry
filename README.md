# SoftBankHackathon2025
'클라우드로 미래를 만든다' - AWS·GCP·Azure 등을 활용하여 사회 문제를 해결하는 프로덕트를 개발

## 프로젝트 구조
Spring Cloud 마이크로서비스 아키텍처로 구성된 프로젝트입니다.

### 서비스 구성
- **server** (포트 8761): Eureka Server - 서비스 발견 및 등록
- **gateway** (포트 8080): API Gateway - 라우팅 및 로드밸런싱
- **fe** (포트 8080): Frontend - Thymeleaf 템플릿 기반 프론트엔드
- **deploy** (포트 8080): Deploy Service - 배포 관리
- **user** (포트 8080): User Service - 사용자 관리

## 개발 환경 설정

### 1. Java 환경 설정
```bash
export JAVA_HOME=/mnt/d/projects/softbank/jdk-17.0.12+7
export PATH=$JAVA_HOME/bin:$PATH
```

### 2. 프로젝트 빌드
```bash
# 전체 서비스 빌드
./gradlew build -x test

# 개별 서비스 빌드
./gradlew :server:build -x test
./gradlew :gateway:build -x test
```

## MSA 실행 방법

### 방법 1: JAR 파일로 실행

1. **Eureka Server 먼저 실행** (필수)
```bash
java -jar server/build/libs/server-0.0.1-SNAPSHOT.jar
```

2. **다른 서비스들 실행** (순서 무관)
```bash
# API Gateway
java -jar gateway/build/libs/gateway-0.0.1-SNAPSHOT.jar

# Frontend Service  
java -jar fe/build/libs/fe-0.0.1-SNAPSHOT.jar

# Deploy Service
java -jar deploy/build/libs/deploy-0.0.1-SNAPSHOT.jar

# User Service
java -jar user/build/libs/user-0.0.1-SNAPSHOT.jar
```

### 방법 2: Docker로 실행

1. **Docker 이미지 빌드**
```bash
docker build -t softbank/server ./server
docker build -t softbank/gateway ./gateway
docker build -t softbank/fe ./fe
docker build -t softbank/deploy ./deploy
docker build -t softbank/user ./user
```

2. **Docker 컨테이너 실행**
```bash
# Eureka Server 먼저 실행
docker run -p 8761:8761 softbank/server

# 다른 서비스들 실행
docker run -p 8080:8080 softbank/gateway
docker run -p 8081:8080 softbank/fe
docker run -p 8082:8080 softbank/deploy
docker run -p 8083:8080 softbank/user
```

## 서비스 확인 방법

### 1. Eureka Server UI 접속
브라우저에서 [http://localhost:8761](http://localhost:8761) 접속

Eureka 대시보드에서 등록된 서비스들을 확인할 수 있습니다:
- GATEWAY
- FE  
- DEPLOY
- USER

### 2. 각 서비스 Health Check
```bash
# API Gateway
curl http://localhost:8080/actuator/health

# 개별 서비스 (Docker 실행 시)
curl http://localhost:8081/
curl http://localhost:8082/
curl http://localhost:8083/
```

### 3. 서비스 등록 확인
Eureka UI에서 **Instances currently registered with Eureka** 섹션을 확인하여 모든 서비스가 정상적으로 등록되었는지 확인합니다.

## 주의사항
- Eureka Server가 먼저 실행되어야 다른 서비스들이 정상적으로 등록됩니다
- 서비스 시작 후 Eureka에 등록되기까지 30초~1분 정도 소요될 수 있습니다
- 포트 충돌을 피하기 위해 Docker 실행 시 다른 포트를 사용합니다
- 캐시 테스트   
