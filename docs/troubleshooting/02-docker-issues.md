# Docker 빌드 및 컨테이너 문제

## 문제 설명

Docker Compose 환경에서 마이크로서비스들이 제대로 시작되지 않고 다음과 같은 오류들이 발생:

```
Error response from daemon: pull access denied for softbank2025/server
No such file or directory: /app/app.jar
curl: command not found (healthcheck 실패)
```

## 오류 증상

### 1. Docker 이미지를 찾을 수 없음
```bash
Error response from daemon: pull access denied for softbank2025/server, repository does not exist
```

### 2. JAR 파일 복사 실패
```bash
COPY failed: file not found in build context or excluded by .dockerignore: stat build/libs/*.jar: no such file or directory
```

### 3. 헬스체크 실패
```bash
/bin/sh: curl: not found
healthcheck 명령이 실행되지 않음
```

## 근본 원인 분석

### 1. Docker Compose 설정 문제

**문제가 있던 설정:**
```yaml
services:
  eureka-server:
    image: softbank2025/server  # 존재하지 않는 원격 이미지 참조
    ports:
      - "8761:8761"
```

**원인:** `image:` 속성을 사용하여 원격 레지스트리의 이미지를 참조했으나 해당 이미지가 존재하지 않음

### 2. Dockerfile JAR 복사 문제

**문제가 있던 설정:**
```dockerfile
COPY build/libs/*.jar app.jar  # 와일드카드로 인한 문제
```

**원인:** 
- 여러 JAR 파일이 존재할 때 와일드카드가 예상과 다르게 동작
- `COPY` 명령이 특정 파일을 찾지 못함

### 3. 헬스체크 도구 누락

**문제가 있던 설정:**
```dockerfile
FROM eclipse-temurin:17-jre
# curl 설치 없이 바로 애플리케이션 실행
```

**원인:** 베이스 이미지에 `curl`이 설치되어 있지 않아 헬스체크 실패

## 해결 방법

### 1. Docker Compose 설정 수정

`image:` 대신 `build:` 사용하여 로컬 빌드:

```yaml
version: '3.8'
services:
  eureka-server:
    build: ./server  # 로컬 Dockerfile로 빌드
    ports:
      - "8761:8761"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s

  gateway:
    build: ./gateway
    ports:
      - "8080:8080"
    depends_on:
      eureka-server:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### 2. Dockerfile JAR 복사 방식 개선

각 마이크로서비스의 Dockerfile 수정:

```dockerfile
FROM eclipse-temurin:17-jre

# curl 설치 (헬스체크용)
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 특정 JAR 파일만 복사 (와일드카드 대신 구체적 파일명)
COPY build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 3. 빌드 순서 최적화

Docker Compose 실행 전 필수 단계:

```bash
# 1. Gradle 빌드 실행
./gradlew build

# 2. Docker Compose로 빌드 및 실행
docker-compose up --build -d

# 3. 로그 확인
docker-compose logs -f
```

### 4. 서비스 의존성 관리

`depends_on`과 `condition: service_healthy`를 사용하여 시작 순서 제어:

```yaml
services:
  gateway:
    build: ./gateway
    depends_on:
      eureka-server:
        condition: service_healthy  # Eureka가 healthy 상태가 된 후 시작
    environment:
      - EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/
```

## 검증 방법

### 1. 개별 서비스 빌드 확인
```bash
# 각 서비스별 JAR 파일 생성 확인
ls -la server/build/libs/
ls -la gateway/build/libs/
ls -la fe/build/libs/
ls -la deploy/build/libs/
ls -la user/build/libs/
```

### 2. Docker 빌드 테스트
```bash
# 개별 서비스 Docker 빌드 테스트
docker build -t test-server ./server
docker build -t test-gateway ./gateway
```

### 3. 컨테이너 헬스체크 확인
```bash
# 컨테이너 상태 확인
docker-compose ps

# 헬스체크 로그 확인
docker-compose logs eureka-server | grep health
```

### 4. 서비스 간 통신 확인
```bash
# Eureka 대시보드 접근
curl http://localhost:8761

# Gateway 헬스체크
curl http://localhost:8080/actuator/health
```

## 최종 Docker Compose 구성

```yaml
version: '3.8'

services:
  eureka-server:
    build: ./server
    ports:
      - "8761:8761"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s

  gateway:
    build: ./gateway
    ports:
      - "8080:8080"
    depends_on:
      eureka-server:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  frontend:
    build: ./fe
    ports:
      - "8081:8081"
    depends_on:
      eureka-server:
        condition: service_healthy

  deploy-service:
    build: ./deploy
    ports:
      - "8082:8082"
    depends_on:
      eureka-server:
        condition: service_healthy

  user-service:
    build: ./user
    ports:
      - "8083:8083"
    depends_on:
      eureka-server:
        condition: service_healthy

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
```

## 예방 방법

1. **빌드 전 검증**: Docker Compose 실행 전 `./gradlew build` 필수
2. **로컬 테스트**: 각 서비스를 개별적으로 Docker 빌드 테스트
3. **헬스체크 설정**: 모든 서비스에 적절한 헬스체크 구성
4. **의존성 순서**: `depends_on`과 `condition`을 활용한 시작 순서 제어

## 관련 파일

- `docker-compose.yml`
- `server/Dockerfile`
- `gateway/Dockerfile`
- `fe/Dockerfile`
- `deploy/Dockerfile`
- `user/Dockerfile`

## 참고 자료

- [Docker Compose Reference](https://docs.docker.com/compose/compose-file/)
- [Docker Multi-stage builds](https://docs.docker.com/develop/dev-best-practices/)
- [Spring Boot Docker Guide](https://spring.io/guides/gs/spring-boot-docker/)