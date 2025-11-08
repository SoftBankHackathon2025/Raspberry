# Spring Cloud 마이크로서비스 트러블슈팅 가이드

SoftBank Hackathon 2025 프로젝트에서 발생한 주요 이슈들과 해결 방법을 체계적으로 정리한 가이드입니다. 각 문서는 실제 프로덕션 환경에서 발생한 문제들을 기반으로 작성되었습니다.

## 📋 문제 해결 가이드 목록

### [01. Spring Cloud Gateway 의존성 문제](./01-gateway-dependencies.md)
**문제:** Gateway 모듈에서 라우팅 기능이 동작하지 않고 Servlet/Reactive 스택 충돌 발생

**핵심 증상:**
- `spring-cloud-starter-gateway` 의존성 누락
- MVC 스택과 WebFlux 스택 동시 존재로 인한 충돌
- 게이트웨이 라우트가 등록되지 않음

**해결책:**
- Gateway 스타터 의존성 추가
- 조건부 의존성 설정으로 Gateway 모듈에서 MVC 스타터 제외
- `@EnableDiscoveryClient` 어노테이션 추가

---

### [02. Docker 빌드 및 컨테이너 문제](./02-docker-issues.md)
**문제:** Docker Compose 환경에서 서비스들이 제대로 시작되지 않는 문제

**핵심 증상:**
- `Error response from daemon: pull access denied` (존재하지 않는 이미지 참조)
- JAR 파일 복사 실패 (와일드카드 문제)
- healthcheck 실패 (curl 명령 없음)

**해결책:**
- `image:` 대신 `build:` 속성 사용하여 로컬 빌드
- 특정 JAR 파일명 패턴으로 복사 (`*-SNAPSHOT.jar`)
- Dockerfile에 curl 설치 추가
- 서비스 의존성 및 헬스체크 구성

---

### [03. Eureka 서비스 디스커버리 연결 문제](./03-eureka-connection.md)
**문제:** 마이크로서비스들이 Eureka Server에 등록되지 않거나 서로 발견하지 못하는 문제

**핵심 증상:**
- `Cannot execute request on any known server`
- 서비스 등록 실패 및 긴 시작 시간
- 서비스 간 통신 불가

**해결책:**
- Eureka 클라이언트 타임아웃 설정 최적화
- 빠른 등록/갱신 주기 설정 (개발환경용)
- Docker 네트워크 설정 및 서비스 의존성 관리
- IP 주소 기반 등록 활성화

---

### [04. Jersey JAX-RS 라이브러리 의존성 충돌](./04-jersey-conflicts.md)
**문제:** Eureka Server에서 Jersey 클라이언트 메소드를 찾을 수 없는 오류

**핵심 증상:**
- `NoSuchMethodError: ClientRequest.isCancelled()`
- Eureka Server 시작 실패
- 클라이언트 등록 불가

**해결책:**
- 수동으로 추가된 Jersey 의존성 모두 제거
- Spring Cloud BOM이 관리하는 Jersey 버전 사용
- 의존성 트리 분석으로 충돌 검증

---

### [05. Gateway 라우팅 설정 및 최적화](./05-gateway-routing.md)
**문제:** 서비스 디스커버리 기반 라우팅이 제대로 동작하지 않고 안정성 부족

**핵심 증상:**
- 라우트 설정 없음 또는 부족
- Circuit Breaker 미설정
- 타임아웃 문제 및 에러 처리 미비

**해결책:**
- Properties에서 YAML 설정으로 전환
- 명시적 라우트 설정 및 서비스 디스커버리 활성화
- Circuit Breaker, 재시도 정책, CORS 설정 추가
- 모니터링 엔드포인트 노출

---

### [06. Prometheus/Grafana 모니터링 스택 통합](./06-monitoring-setup.md)
**문제:** 마이크로서비스 환경에서 모니터링 및 메트릭 수집이 되지 않는 문제

**핵심 증상:**
- Prometheus 엔드포인트 접근 불가
- Actuator 메트릭 미노출
- 모니터링 대시보드 부재

**해결책:**
- 모든 서비스에 Actuator 및 Prometheus 의존성 추가
- 표준화된 메트릭 엔드포인트 설정
- Docker Compose로 Prometheus/Grafana 스택 통합
- 대시보드 및 알림 구성

---

### [07. Spring Boot Config Data Import 문제](./07-config-import-issues.md)
**문제:** `.env` 파일 import 설정으로 인한 애플리케이션 시작 실패

**핵심 증상:**
- `java.lang.IllegalStateException: Unable to load config data from '"classpath:.env"'`
- ConfigDataLocationResolver 오류
- 서비스 완전 시작 불가

**해결책:**
- 불필요한 `spring.config.import` 설정 제거
- 다른 서비스들과 설정 일관성 유지
- 민감 정보는 환경변수로 관리

---

## 🚀 빠른 문제 해결 체크리스트

프로젝트에서 문제가 발생했을 때 다음 순서로 확인하세요:

### 1단계: 기본 환경 확인
```bash
# Java 버전 확인
java -version  # JDK 17 확인

# Gradle 빌드 상태
./gradlew build

# Docker 상태 확인  
docker-compose ps
```

### 2단계: 서비스별 상태 확인
```bash
# Eureka 대시보드
curl http://localhost:8761

# Gateway 헬스체크
curl http://localhost:8080/actuator/health

# 개별 서비스 상태
curl http://localhost:8081/actuator/health  # Frontend
curl http://localhost:8082/actuator/health  # Deploy
curl http://localhost:8083/actuator/health  # User
```

### 3단계: 로그 분석
```bash
# 전체 서비스 로그 확인
docker-compose logs

# 특정 서비스 로그 분석
docker-compose logs eureka-server
docker-compose logs gateway
```

### 4단계: 의존성 확인
```bash
# 의존성 트리 분석
./gradlew dependencies

# 특정 모듈 의존성 확인
./gradlew :gateway:dependencies
./gradlew :server:dependencies
```

## 🔧 주요 설정 파일 위치

### Gradle 설정
- `build.gradle` (Root) - 공통 의존성 관리
- `settings.gradle` - 모듈 정의
- `*/build.gradle` - 각 서비스별 의존성

### 애플리케이션 설정
- `gateway/src/main/resources/application.yml` - Gateway 설정
- `*/src/main/resources/application.properties` - 각 서비스 설정

### Docker 설정
- `docker-compose.yml` - 전체 스택 오케스트레이션
- `*/Dockerfile` - 각 서비스 컨테이너 설정

### 모니터링 설정
- `monitoring/prometheus.yml` - 메트릭 수집 설정
- `monitoring/grafana/` - 대시보드 구성

## 📊 모니터링 대시보드

### Prometheus (포트 9090)
- **URL:** http://localhost:9090
- **용도:** 메트릭 수집 및 쿼리
- **주요 메트릭:** JVM, HTTP, Eureka

### Grafana (포트 3000)
- **URL:** http://localhost:3000
- **계정:** admin / admin
- **용도:** 시각화 대시보드

### Eureka Dashboard (포트 8761)
- **URL:** http://localhost:8761
- **용도:** 서비스 등록 상태 확인

## 🔍 일반적인 해결 패턴

### 빌드 문제
1. `./gradlew clean` - 캐시 정리
2. 의존성 충돌 확인
3. Java 버전 호환성 검증

### 실행 문제  
1. 서비스 시작 순서 확인 (Eureka → Others)
2. 포트 충돌 검사
3. Docker 네트워크 상태 확인

### 통신 문제
1. Eureka 등록 상태 확인
2. 방화벽/네트워크 설정
3. 서비스 디스커버리 설정 검토

### 성능 문제
1. JVM 메모리 설정 확인
2. Connection Pool 튜닝
3. Circuit Breaker 임계값 조정

## 🆘 추가 도움이 필요한 경우

1. **로그 분석**: 각 트러블슈팅 문서의 "검증 방법" 섹션 참조
2. **설정 템플릿**: 각 문서의 "해결 방법" 섹션에서 완전한 설정 예시 확인
3. **성능 최적화**: 각 문서의 "성능 최적화" 또는 "예방 방법" 섹션 참조

---

**마지막 업데이트:** 2024-11-08  
**프로젝트:** SoftBank Hackathon 2025 - Raspberry  
**아키텍처:** Spring Cloud 마이크로서비스 (Spring Boot 3.4.11 + Spring Cloud 2024.0.2)
**최신 이슈:** Config Data Import 문제 해결 (07번 가이드 추가)