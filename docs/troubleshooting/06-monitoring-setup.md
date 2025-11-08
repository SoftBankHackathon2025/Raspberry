# Prometheus/Grafana 모니터링 스택 통합

## 문제 설명

마이크로서비스 환경에서 서비스별 모니터링 및 메트릭 수집이 되지 않고, Prometheus 엔드포인트가 노출되지 않는 문제:

```
Prometheus endpoints not accessible
Actuator metrics not exposed
No monitoring dashboards available
Service health monitoring not working
```

## 요구사항 및 목표

1. **Prometheus 메트릭 수집** - 모든 마이크로서비스에서 메트릭 노출
2. **Grafana 대시보드** - 실시간 모니터링 및 시각화
3. **Actuator 엔드포인트** - 헬스체크 및 상태 확인
4. **JVM 및 애플리케이션 메트릭** - 성능 모니터링
5. **Docker Compose 통합** - 원클릭 모니터링 스택 구성

## 기존 설정의 문제점

### 1. Actuator 의존성 누락

**문제가 있던 build.gradle:**
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    // ❌ Actuator 및 Prometheus 의존성 누락
}
```

### 2. 메트릭 엔드포인트 미노출

**문제가 있던 application.properties:**
```properties
# ❌ 모니터링 설정 없음
spring.application.name=gateway
server.port=8080
```

### 3. 모니터링 인프라 부재

- Prometheus 서버 없음
- Grafana 대시보드 없음
- Docker Compose에 모니터링 스택 미포함

## 해결 방법

### 1. 모든 서비스에 Actuator 의존성 추가

**Root `build.gradle` 수정:**
```gradle
subprojects {
    dependencies {
        // 모든 서비스에 공통 적용
        implementation 'org.springframework.boot:spring-boot-starter-actuator'
        implementation 'io.micrometer:micrometer-registry-prometheus'
        
        if (project.name != 'gateway') {
            implementation 'org.springframework.boot:spring-boot-starter-web'
        }
    }
}
```

**개별 서비스 build.gradle 확인:**
```gradle
// gateway/build.gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

### 2. 모든 서비스 Actuator 설정 표준화

**공통 application.properties 설정 (server, user, deploy, fe):**
```properties
# Management and Monitoring
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.health.show-details=always
management.endpoint.prometheus.enabled=true
management.metrics.export.prometheus.enabled=true
```

**Gateway application.yml 모니터링 설정:**
```yaml
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
      show-components: always
    gateway:
      enabled: true
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
  health:
    circuitbreakers:
      enabled: true
  info:
    env:
      enabled: true
    build:
      enabled: true
    git:
      enabled: true
```

### 3. Prometheus 서버 설정

**monitoring/prometheus.yml 생성:**
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:

scrape_configs:
  # Eureka Server
  - job_name: 'eureka-server'
    static_configs:
      - targets: ['eureka-server:8761']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

  # Gateway Service  
  - job_name: 'gateway'
    static_configs:
      - targets: ['gateway:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

  # Frontend Service
  - job_name: 'frontend'
    static_configs:
      - targets: ['frontend:8081']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

  # Deploy Service
  - job_name: 'deploy-service'
    static_configs:
      - targets: ['deploy-service:8082']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

  # User Service
  - job_name: 'user-service'
    static_configs:
      - targets: ['user-service:8083']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

  # Prometheus self-monitoring
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
```

### 4. Grafana 대시보드 설정

**monitoring/grafana/provisioning/datasources/datasource.yml:**
```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    orgId: 1
    url: http://prometheus:9090
    basicAuth: false
    isDefault: true
    editable: true
```

**monitoring/grafana/provisioning/dashboards/dashboards.yml:**
```yaml
apiVersion: 1

providers:
  - name: 'Spring Boot Dashboard'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards
```

### 5. Docker Compose 모니터링 스택 통합

**docker-compose.yml 모니터링 섹션 추가:**
```yaml
services:
  # ... 기존 마이크로서비스들 ...

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/usr/share/prometheus/console_libraries'
      - '--web.console.templates=/usr/share/prometheus/consoles'
      - '--storage.tsdb.retention.time=15d'
      - '--web.enable-lifecycle'
    networks:
      - microservices-network
    depends_on:
      - eureka-server
      - gateway
      - frontend
      - deploy-service
      - user-service

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
      - grafana-storage:/var/lib/grafana
    networks:
      - microservices-network
    depends_on:
      - prometheus

volumes:
  grafana-storage:

networks:
  microservices-network:
    driver: bridge
```

## 검증 방법

### 1. Actuator 엔드포인트 확인

모든 서비스의 엔드포인트 접근 테스트:

```bash
# Eureka Server
curl http://localhost:8761/actuator/health
curl http://localhost:8761/actuator/prometheus | head -10

# Gateway
curl http://localhost:8080/actuator/health  
curl http://localhost:8080/actuator/prometheus | head -10

# User Service
curl http://localhost:8083/actuator/health
curl http://localhost:8083/actuator/prometheus | head -10

# Deploy Service  
curl http://localhost:8082/actuator/health
curl http://localhost:8082/actuator/prometheus | head -10

# Frontend
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/prometheus | head -10
```

**예상 헬스체크 응답:**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

**예상 Prometheus 메트릭 응답:**
```
# HELP application_ready_time_seconds Time taken for the application to be ready
# TYPE application_ready_time_seconds gauge
application_ready_time_seconds{main_application_class="softbank.hackathon.gateway.GatewayApplication"} 55.061
```

### 2. Prometheus 타겟 상태 확인

```bash
# Prometheus 웹 UI 접속
open http://localhost:9090

# 타겟 상태 API 확인
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'
```

**정상 상태 출력:**
```json
{"job": "eureka-server", "health": "up"}
{"job": "gateway", "health": "up"}  
{"job": "user-service", "health": "up"}
{"job": "deploy-service", "health": "up"}
{"job": "frontend", "health": "up"}
```

### 3. Grafana 대시보드 접근

```bash
# Grafana 웹 UI 접속 (admin/admin)
open http://localhost:3000

# 대시보드 API 확인
curl -u admin:admin http://localhost:3000/api/datasources
```

### 4. 메트릭 쿼리 테스트

Prometheus에서 주요 메트릭 확인:

```promql
# JVM 메모리 사용률
jvm_memory_used_bytes{service="gateway"}

# HTTP 요청 수
http_server_requests_seconds_count{uri="/actuator/health"}

# 서비스 가동 시간  
process_uptime_seconds

# Eureka 등록된 인스턴스 수
eureka_server_registry_size
```

## 대시보드 설정

### Spring Boot 애플리케이션 대시보드

**monitoring/grafana/provisioning/dashboards/spring-boot.json:**
```json
{
  "dashboard": {
    "id": 1,
    "title": "Spring Boot Microservices",
    "tags": ["spring-boot", "microservices"],
    "timezone": "browser",
    "panels": [
      {
        "title": "Application Status",
        "type": "stat",
        "targets": [
          {
            "expr": "up{job=~\"eureka-server|gateway|user-service|deploy-service|frontend\"}"
          }
        ]
      },
      {
        "title": "JVM Memory Usage",
        "type": "graph", 
        "targets": [
          {
            "expr": "jvm_memory_used_bytes / jvm_memory_max_bytes * 100"
          }
        ]
      }
    ]
  }
}
```

## 알림 설정

### Prometheus 알림 규칙

**monitoring/prometheus/alerts.yml:**
```yaml
groups:
  - name: microservices
    rules:
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.job }} is down"
          
      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes / jvm_memory_max_bytes * 100 > 85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High memory usage on {{ $labels.job }}"
```

## 성능 최적화

### 1. 메트릭 수집 주기 조정

```yaml
# prometheus.yml
global:
  scrape_interval: 30s  # 프로덕션: 30s, 개발: 15s
  evaluation_interval: 30s
```

### 2. 메트릭 필터링

```properties
# application.properties - 불필요한 메트릭 제외
management.metrics.export.prometheus.enabled=true
management.metrics.enable.jvm.gc=true
management.metrics.enable.jvm.memory=true
management.metrics.enable.system.cpu=true
management.metrics.enable.process.files=false
```

### 3. Grafana 데이터 보존

```yaml
# docker-compose.yml grafana 환경변수
environment:
  - GF_DATABASE_TYPE=postgres  # 프로덕션용
  - GF_ANALYTICS_REPORTING_ENABLED=false
```

## 트러블슈팅

### 1. 메트릭 엔드포인트 404 에러

**원인:** Actuator 의존성 누락 또는 엔드포인트 비활성화

**해결:**
```properties
management.endpoints.web.exposure.include=prometheus,health,info
management.endpoint.prometheus.enabled=true
```

### 2. Prometheus 타겟 연결 실패

**원인:** Docker 네트워크 설정 또는 포트 문제

**확인:**
```bash
# 컨테이너 간 네트워크 연결 테스트
docker exec -it prometheus ping gateway
```

### 3. Grafana 데이터소스 연결 실패

**원인:** Prometheus URL 설정 오류

**해결:**
```yaml
# datasource.yml 수정
url: http://prometheus:9090  # 컨테이너 이름 사용
```

## 관련 파일

- `build.gradle` (Root)
- `*/src/main/resources/application.properties` (모든 서비스)
- `gateway/src/main/resources/application.yml`
- `monitoring/prometheus.yml`
- `monitoring/grafana/provisioning/datasources/datasource.yml`
- `docker-compose.yml`

## 참고 자료

- [Spring Boot Actuator Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Prometheus](https://micrometer.io/docs/registry/prometheus)
- [Grafana Provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)