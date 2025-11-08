# Spring Cloud Gateway 라우팅 설정 및 최적화

## 문제 설명

Spring Cloud Gateway에서 서비스 디스커버리 기반 라우팅이 제대로 동작하지 않고, 라우트 설정이 복잡하며 안정성이 부족한 문제:

```
Route not found for request path: /user/health
Service discovery routing not working
Circuit breaker not configured
Timeout issues in production
```

## 요구사항 및 목표

1. **서비스 디스커버리 기반 자동 라우팅**
2. **명시적 라우트 설정** (user, deploy, fe)
3. **Circuit Breaker 패턴** 적용
4. **타임아웃 및 재시도 정책** 설정
5. **CORS 지원** 및 보안 설정
6. **모니터링 엔드포인트** 노출

## 기존 설정의 문제점

### 1. Properties 기반 제한적 설정

**문제가 있던 application.properties:**
```properties
spring.application.name=gateway
server.port=8080
eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka/
```

**문제점:**
- 라우트 설정 없음
- 서비스 디스커버리 비활성화
- Circuit breaker 미설정
- 타임아웃 설정 부족

### 2. 기본 설정만 사용

Spring Cloud Gateway의 기본 동작에만 의존하여:
- 명시적 라우트 규칙 없음
- 로드밸런싱 설정 없음
- 에러 처리 미비

## 해결 방법

### 1. YAML 기반 포괄적 설정으로 전환

`gateway/src/main/resources/application.yml` 생성:

```yaml
spring:
  application:
    name: gateway
  cloud:
    gateway:
      # 서비스 디스커버리 자동 라우팅 활성화
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
          include-expression: "serviceId.matches('user|deploy|fe')"
          
      # 명시적 라우트 설정
      routes:
        # User Service Routes
        - id: user-service
          uri: lb://user  # 로드밸런서를 통한 라우팅
          predicates:
            - Path=/user/**, /users/**
          filters:
            - name: StripPrefix
              args:
                parts: 1
            - name: Retry
              args:
                retries: 3
                statuses: BAD_GATEWAY,GATEWAY_TIMEOUT
                backoff:
                  firstBackoff: 50ms
                  maxBackoff: 500ms
                  factor: 2
        
        # Deploy Service Routes  
        - id: deploy-service
          uri: lb://deploy
          predicates:
            - Path=/deploy/**, /deployments/**
          filters:
            - name: StripPrefix
              args:
                parts: 1
            - name: Retry
              args:
                retries: 3
                statuses: BAD_GATEWAY,GATEWAY_TIMEOUT
                backoff:
                  firstBackoff: 50ms
                  maxBackoff: 500ms
                  factor: 2
        
        # Frontend Service Routes
        - id: fe-service
          uri: lb://fe
          predicates:
            - Path=/fe/**, /frontend/**
          filters:
            - name: StripPrefix
              args:
                parts: 1
            - name: AddResponseHeader
              args:
                name: X-Frontend-Service
                value: fe
        
        # Default Route to Frontend
        - id: default-route
          uri: lb://fe
          predicates:
            - Path=/**
          order: 1000  # 가장 낮은 우선순위
      
      # 전역 필터 설정
      default-filters:
        - name: AddRequestHeader
          args:
            name: X-Gateway-Source
            value: raspberry-gateway
        - name: AddResponseHeader
          args:
            name: X-Gateway-Timestamp
            value: "gateway-response"
      
      # HTTP 클라이언트 설정
      httpclient:
        connect-timeout: 5000
        response-timeout: 30s
        pool:
          type: elastic
          max-idle-time: 15s
          max-life-time: 60s
        ssl:
          use-insecure-trust-manager: false

server:
  port: 8080
  servlet:
    context-path: /
```

### 2. Eureka 및 모니터링 설정

```yaml
# Eureka Client Configuration
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
    registry-fetch-interval-seconds: 5
    instance-info-replication-interval-seconds: 5
    initial-instance-info-replication-interval-seconds: 5
    eureka-server-connect-timeout-seconds: 5
    eureka-server-read-timeout-seconds: 5
    eureka-connection-idle-timeout-seconds: 10
    heartbeat-executor-thread-pool-size: 2
    cache-refresh-executor-thread-pool-size: 2
    fetch-registry: true
    register-with-eureka: true
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 5
    lease-expiration-duration-in-seconds: 15
    instance-id: ${spring.application.name}:${spring.application.instance_id:${random.value}}

# Management and Actuator Configuration
management:
  endpoints:
    web:
      exposure:
        include: health, info, gateway, refresh, routes, filters, globalfilters, prometheus, metrics
      base-path: /actuator
  endpoint:
    health:
      show-details: always
    gateway:
      enabled: true
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

### 3. 보안 및 CORS 설정

```yaml
# Custom Gateway Properties
gateway:
  security:
    cors:
      enabled: true
      allowed-origins: "*"
      allowed-methods: "GET,POST,PUT,DELETE,OPTIONS"
      allowed-headers: "*"
      allow-credentials: false
  monitoring:
    metrics:
      enabled: true
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50
      wait-duration-in-open-state: 60s
      sliding-window-size: 10
```

### 4. 환경별 프로필 설정

```yaml
---
# Development Profile
spring:
  config:
    activate:
      on-profile: dev
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

---
# Production Profile  
spring:
  config:
    activate:
      on-profile: prod
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
logging:
  level:
    reactor.netty.http.client: INFO
    org.springframework.web.reactive: INFO
```

### 5. 로깅 설정 최적화

```yaml
# Logging Configuration
logging:
  level:
    org.springframework.cloud.gateway: INFO
    org.springframework.cloud.netflix.eureka: INFO
    com.netflix.discovery: INFO
    reactor.netty.http.client: DEBUG
    org.springframework.web.reactive: DEBUG
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level [%X{traceId:-},%X{spanId:-}] %logger{36} - %msg%n"
```

## 검증 방법

### 1. Gateway 라우트 확인

```bash
# 등록된 라우트 목록 확인
curl http://localhost:8080/actuator/gateway/routes | jq '.'

# 특정 라우트 정보 확인
curl http://localhost:8080/actuator/gateway/routes/user-service
```

**예상 출력:**
```json
[
  {
    "route_id": "user-service",
    "route_definition": {
      "id": "user-service",
      "uri": "lb://user",
      "predicates": [
        {
          "name": "Path",
          "args": {
            "pattern": "/user/**,/users/**"
          }
        }
      ]
    }
  }
]
```

### 2. 서비스 디스커버리 라우팅 테스트

```bash
# User 서비스 호출 (StripPrefix 적용)
curl http://localhost:8080/user/actuator/health

# Deploy 서비스 호출
curl http://localhost:8080/deploy/actuator/health

# Frontend 서비스 호출
curl http://localhost:8080/fe/

# Default 라우트 테스트 (Frontend로 라우팅)
curl http://localhost:8080/
```

### 3. 로드밸런싱 검증

여러 인스턴스가 있을 때:
```bash
# 여러 번 호출하여 로드밸런싱 확인
for i in {1..5}; do
  curl -s http://localhost:8080/user/actuator/info | jq '.instanceId'
done
```

### 4. Circuit Breaker 동작 확인

```bash
# 서비스 다운 상황 시뮬레이션
docker-compose stop user-service

# Circuit Breaker 상태 확인
curl http://localhost:8080/actuator/health

# 서비스 복구
docker-compose start user-service
```

### 5. 필터 동작 검증

```bash
# 응답 헤더 확인 (AddResponseHeader 필터)
curl -I http://localhost:8080/user/actuator/health
```

**예상 헤더:**
```
X-Gateway-Source: raspberry-gateway
X-Gateway-Timestamp: gateway-response
```

## 성능 최적화

### 1. Connection Pool 설정

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        pool:
          type: elastic
          max-connections: 100
          max-idle-time: 15s
          max-life-time: 60s
```

### 2. Circuit Breaker 조정

```yaml
resilience4j:
  circuitbreaker:
    instances:
      user-service:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
        minimum-number-of-calls: 5
```

### 3. 캐싱 설정

```yaml
spring:
  cloud:
    gateway:
      global-filter:
        response-cache:
          timeToLive: 60s
          size: 1000
```

## 테스트 코드 예시

### Gateway Routes 테스트

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@TestPropertySource(properties = {
    "spring.cloud.discovery.client.simple.instances.user[0].uri=http://localhost:8083",
    "spring.cloud.discovery.client.simple.instances.deploy[0].uri=http://localhost:8082"
})
class GatewayRoutesTest {

    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testUserServiceRouting() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/user/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    @Test 
    void testDeployServiceRouting() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/deploy/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

## 모니터링 및 운영

### 1. 실시간 라우트 모니터링

```bash
# 라우트 통계 확인
curl http://localhost:8080/actuator/metrics | grep gateway

# 서비스별 호출 통계
curl http://localhost:8080/actuator/prometheus | grep gateway_requests
```

### 2. 로그 분석

```bash
# Gateway 로그 실시간 모니터링
docker-compose logs -f gateway | grep -E "(route|filter|circuit)"
```

## 관련 파일

- `gateway/src/main/resources/application.yml`
- `gateway/src/main/java/softbank/hackathon/gateway/GatewayApplication.java`
- `gateway/build.gradle`
- `gateway/src/test/java/.../GatewayRoutesTest.java`

## 참고 자료

- [Spring Cloud Gateway Reference](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [Gateway Filters](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#gatewayfilter-factories)
- [Circuit Breaker Integration](https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/)