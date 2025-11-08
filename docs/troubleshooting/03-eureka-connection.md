# Eureka 서비스 디스커버리 연결 문제

## 문제 설명

마이크로서비스들이 Eureka Server에 등록되지 않거나 서로 발견하지 못하는 문제 발생:

```
Cannot execute request on any known server
DiscoveryClient: Failed to connect to remote server
Service 'user' not found in discovery
```

## 오류 증상

### 1. Eureka Server 연결 실패
```bash
2024-11-08 12:34:56.789 ERROR [gateway,] --- Cannot execute request on any known server
com.netflix.discovery.shared.transport.TransportException: Cannot execute request on any known server
```

### 2. 서비스 등록 실패
```bash
DiscoveryClient_GATEWAY/gateway:xxxx - registration failed...
Eureka client connect timeout reached
```

### 3. 서비스 간 통신 불가
```bash
Service 'user' not found in discovery
LoadBalancer does not contain instance for the service 'deploy'
```

### 4. 긴 시작 시간
```bash
Waiting... trying to connect to eureka-server:8761
Initial fetch from Eureka Server failed
```

## 근본 원인 분석

### 1. 네트워크 설정 문제

**Docker Compose 환경:**
- 컨테이너 간 네트워크 통신 설정 부족
- 호스트명 해석 문제 (`eureka-server` vs `localhost`)

### 2. 타임아웃 설정 부족

**기본 설정의 문제:**
```properties
# 기본값들이 프로덕션 환경에 부적절
eureka.client.eureka-server-connect-timeout-seconds=5  # 너무 짧음
eureka.client.eureka-server-read-timeout-seconds=8     # 너무 짧음
```

### 3. 등록/갱신 주기 문제

**기본 설정:**
```properties
# 기본 30초 주기는 개발/테스트에 너무 길음
eureka.client.registry-fetch-interval-seconds=30
eureka.instance.lease-renewal-interval-in-seconds=30
```

### 4. Docker 환경 특화 설정 부족

Docker 컨테이너 환경에서는 IP 주소 기반 등록이 필요하나 기본 설정으로는 호스트명 기반 등록 시도

## 해결 방법

### 1. Eureka Client 타임아웃 최적화

모든 클라이언트 서비스의 `application.properties`에 다음 설정 추가:

```properties
# Eureka Client 설정
eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka/
eureka.instance.prefer-ip-address=true

# 연결 타임아웃 증가
eureka.client.eureka-server-connect-timeout-seconds=10
eureka.client.eureka-server-read-timeout-seconds=10
eureka.client.eureka-connection-idle-timeout-seconds=15

# 빠른 등록 및 갱신 주기 (개발환경용)
eureka.client.registry-fetch-interval-seconds=5
eureka.client.instance-info-replication-interval-seconds=5
eureka.instance.lease-renewal-interval-in-seconds=5
eureka.instance.lease-expiration-duration-in-seconds=15

# 초기 등록 최적화
eureka.client.initial-instance-info-replication-interval-seconds=5

# 스레드 풀 설정
eureka.client.heartbeat-executor-thread-pool-size=2
eureka.client.cache-refresh-executor-thread-pool-size=2
```

### 2. Eureka Server 설정 최적화

`server/src/main/resources/application.properties`:

```properties
spring.application.name=server
server.port=8761

# Eureka Server 설정
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false

# 자체 보호 모드 비활성화 (개발환경)
eureka.server.enable-self-preservation=false

# 빠른 제거 및 갱신 (개발환경용)
eureka.server.eviction-interval-timer-in-ms=4000
eureka.server.response-cache-update-interval-ms=5000
eureka.server.response-cache-auto-expiration-in-seconds=180

# Docker 환경 설정
eureka.instance.hostname=eureka-server
eureka.server.wait-time-in-ms-when-sync-empty=0
```

### 3. Docker Compose 네트워크 설정

`docker-compose.yml`에서 서비스 간 의존성 명확히 설정:

```yaml
services:
  eureka-server:
    build: ./server
    ports:
      - "8761:8761"
    networks:
      - microservices-network
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
    networks:
      - microservices-network
    depends_on:
      eureka-server:
        condition: service_healthy
    environment:
      - EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/

networks:
  microservices-network:
    driver: bridge
```

### 4. 헬스체크 기반 순차 시작

서비스 시작 순서 제어:

```yaml
  user-service:
    build: ./user
    depends_on:
      eureka-server:
        condition: service_healthy  # Eureka가 준비된 후 시작
    environment:
      - EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/
```

## 검증 방법

### 1. Eureka 대시보드 확인

브라우저에서 `http://localhost:8761` 접속하여 등록된 서비스 확인:

```
Application         AMIs        Availability Zones        Status
GATEWAY             n/a         (1) (1)                   UP (1) - gateway:xxx
USER                n/a         (1) (1)                   UP (1) - user:xxx  
DEPLOY              n/a         (1) (1)                   UP (1) - deploy:xxx
```

### 2. 개별 서비스 등록 상태 확인

```bash
# Gateway에서 등록된 서비스 목록 확인
curl http://localhost:8080/actuator/health
```

응답에서 `discoveryComposite.eureka.applications` 확인:
```json
{
  "status": "UP",
  "components": {
    "discoveryComposite": {
      "status": "UP",
      "components": {
        "eureka": {
          "status": "UP",
          "details": {
            "applications": {
              "GATEWAY": 1,
              "USER": 1, 
              "DEPLOY": 1
            }
          }
        }
      }
    }
  }
}
```

### 3. 서비스 간 통신 테스트

Gateway를 통한 라우팅 테스트:

```bash
# User 서비스 호출
curl http://localhost:8080/user/actuator/health

# Deploy 서비스 호출  
curl http://localhost:8080/deploy/actuator/health
```

### 4. 로그 모니터링

각 서비스의 등록 상태 로그 확인:

```bash
# Gateway 등록 로그
docker-compose logs gateway | grep "Registered instance"

# User 서비스 등록 로그  
docker-compose logs user-service | grep "Registered instance"
```

정상적인 로그 예시:
```
INFO [gateway] --- Registered instance GATEWAY/gateway:xxx with status UP
INFO [user] --- Registered instance USER/user:xxx with status UP
```

## 개발 vs 프로덕션 설정

### 개발 환경 (빠른 등록/해제)
```properties
eureka.client.registry-fetch-interval-seconds=5
eureka.instance.lease-renewal-interval-in-seconds=5
eureka.instance.lease-expiration-duration-in-seconds=15
eureka.server.enable-self-preservation=false
```

### 프로덕션 환경 (안정성 우선)
```properties
eureka.client.registry-fetch-interval-seconds=30
eureka.instance.lease-renewal-interval-in-seconds=30  
eureka.instance.lease-expiration-duration-in-seconds=90
eureka.server.enable-self-preservation=true
```

## 예방 방법

1. **점진적 시작**: Eureka Server 완전 시작 후 클라이언트들 순차 시작
2. **헬스체크 활용**: Docker Compose의 `condition: service_healthy` 활용
3. **로그 모니터링**: 각 서비스의 등록 상태 실시간 확인
4. **네트워크 격리**: Docker 네트워크로 서비스 간 통신 보장

## 관련 파일

- `server/src/main/resources/application.properties`
- `gateway/src/main/resources/application.yml` 
- `user/src/main/resources/application.properties`
- `deploy/src/main/resources/application.properties`
- `fe/src/main/resources/application.properties`
- `docker-compose.yml`

## 참고 자료

- [Netflix Eureka Documentation](https://github.com/Netflix/eureka/wiki)
- [Spring Cloud Netflix Reference](https://docs.spring.io/spring-cloud-netflix/docs/current/reference/html/)
- [Docker Compose Networks](https://docs.docker.com/compose/networking/)