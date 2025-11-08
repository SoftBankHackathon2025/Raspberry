# Spring Boot Config Data Import 문제

## 문제 설명

Spring Boot 애플리케이션 시작 시 `.env` 파일 import 설정으로 인한 config data 로드 실패:

```
java.lang.IllegalStateException: Unable to load config data from '"classpath:.env"'
Caused by: java.lang.IllegalStateException: Incorrect ConfigDataLocationResolver chosen or file extension is not known to any PropertySourceLoader
```

## 오류 증상

### 1. 애플리케이션 시작 실패
```bash
java.lang.IllegalStateException: Unable to load config data from '"classpath:.env"'
        at org.springframework.boot.context.config.StandardConfigDataLocationResolver.getReferences
```

### 2. ConfigDataLocationResolver 오류
```bash
Incorrect ConfigDataLocationResolver chosen or file extension is not known to any PropertySourceLoader. 
If the location is meant to reference a directory, it must end in '/' or File.separator.
```

### 3. 서비스 완전 시작 불가
- 애플리케이션 컨텍스트 로드 실패
- 스프링 부트 시작 과정에서 중단

## 근본 원인 분석

### 1. 존재하지 않는 .env 파일 참조

**문제가 있던 설정 (`fe/src/main/resources/application.properties`):**
```properties
spring.application.name=fe
server.port=8081
spring.config.import: "classpath:.env"  # 🚨 문제: .env 파일이 존재하지 않음
```

### 2. Spring Boot Config Data 구조 문제

**Spring Boot 2.4+ Config Data API:**
- `spring.config.import` 속성은 실제 존재하는 파일을 참조해야 함
- `classpath:.env`는 클래스패스 루트에 `.env` 파일이 있어야 함
- 파일이 없으면 `StandardConfigDataLocationResolver`가 처리 실패

### 3. 파일 확장자 인식 문제

**.env 파일의 특성:**
- 표준 properties 파일이 아님
- Spring Boot의 기본 PropertySourceLoader가 처리하지 못함
- 별도의 config data resolver 필요

## 해결 방법

### 해결책 1: Config Import 설정 제거 (권장)

`fe/src/main/resources/application.properties`에서 import 라인 제거:

```properties
# 수정 전
spring.application.name=fe
server.port=8081
spring.config.import: "classpath:.env"  # 제거

# 수정 후  
spring.application.name=fe
server.port=8081
# spring.config.import 라인 제거됨
```

**장점:**
- 다른 서비스들과 설정 일관성 유지
- 복잡한 config data 처리 불필요
- 민감 정보는 환경변수나 별도 방법으로 관리

### 해결책 2: .env 파일 생성 (선택사항)

필요시 `.env` 파일을 실제로 생성:

**1단계: .env 파일 생성**
```bash
# fe/src/main/resources/.env 파일 생성
DB_URL=jdbc:h2:mem:testdb
DB_USERNAME=sa
DB_PASSWORD=
```

**2단계: .gitignore 추가**
```gitignore
# .gitignore에 추가
**/.env
*.env
```

**3단계: Config Import 설정 수정**
```properties
# application.properties
spring.config.import=optional:classpath:.env
# 또는 더 명확하게
spring.config.import=optional:classpath:/.env
```

### 해결책 3: Environment Variables 사용 (프로덕션 권장)

환경변수를 통한 설정 관리:

```bash
# Docker Compose에서
environment:
  - SPRING_PROFILES_ACTIVE=prod
  - DB_URL=jdbc:postgresql://db:5432/myapp
  - DB_USERNAME=${DB_USERNAME}
  - DB_PASSWORD=${DB_PASSWORD}
```

```properties
# application.properties
spring.datasource.url=${DB_URL:jdbc:h2:mem:testdb}
spring.datasource.username=${DB_USERNAME:sa}
spring.datasource.password=${DB_PASSWORD:}
```

## 검증 방법

### 1. 로컬 빌드 및 실행

```bash
# Java 환경 설정
export JAVA_HOME=/mnt/d/projects/softbankORG/Raspberry/jdk-17.0.12+7
export PATH=$JAVA_HOME/bin:$PATH

# 빌드 테스트
./gradlew :fe:build

# 실행 테스트  
java -jar fe/build/libs/fe-0.0.1-SNAPSHOT.jar
```

**성공적인 시작 로그:**
```
Started FeApplication in 26.438 seconds (process running for 28.864)
Tomcat started on port 8081 (http) with context path '/'
```

### 2. 헬스체크 확인

```bash
# 애플리케이션 헬스 상태 확인
curl http://localhost:8081/actuator/health
```

**예상 응답:**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### 3. Docker 환경 테스트

```bash
# Docker 이미지 빌드
docker build -t test-fe ./fe

# 컨테이너 실행
docker run -p 8081:8081 test-fe
```

## Spring Boot Config Data 기본 원리

### Config Import 동작 원리

```properties
# 지원되는 형식들
spring.config.import=classpath:application-dev.properties
spring.config.import=file:./config/local.properties  
spring.config.import=optional:configtree:/etc/config/
spring.config.import=consul:// (with spring-cloud-consul)
```

### PropertySourceLoader 지원 파일 형식

**기본 지원:**
- `.properties`
- `.yml` / `.yaml`
- `.xml` (properties format)

**추가 의존성 필요:**
- `.env` → spring-boot-dotenv 등의 별도 라이브러리
- `.json` → 커스텀 PropertySourceLoader

### Optional Import 활용

존재하지 않을 수 있는 파일에 대해서는 `optional:` 접두어 사용:

```properties
# 파일이 없어도 애플리케이션 시작 가능
spring.config.import=optional:classpath:.env
spring.config.import=optional:file:./config/override.properties
```

## 모범 사례

### 1. 환경별 설정 파일 분리

```
src/main/resources/
├── application.properties              # 공통 설정
├── application-dev.properties          # 개발 환경
├── application-test.properties         # 테스트 환경  
└── application-prod.properties         # 프로덕션 환경
```

### 2. 민감 정보 관리

**개발 환경:**
```properties
# application-dev.properties
spring.config.import=optional:classpath:dev.env
```

**프로덕션 환경:**
```bash
# 환경변수 또는 외부 config server 사용
export DB_PASSWORD=secure_password
export API_KEY=secret_api_key
```

### 3. 설정 우선순위 이해

Spring Boot 설정 로드 순서 (높은 우선순위 순):
1. 명령행 인수 (`--server.port=8080`)
2. 시스템 속성 (`-Dserver.port=8080`) 
3. 환경변수 (`SERVER_PORT=8080`)
4. `application-{profile}.properties`
5. `application.properties`
6. Config data imports

## 예방 방법

### 1. 설정 파일 검증

새 서비스 추가 시 설정 파일 체크리스트:

```bash
# 1. config import 설정 확인
grep -r "spring.config.import" */src/main/resources/

# 2. 참조 파일 존재 여부 확인  
find . -name "*.env" -o -name "*.properties" | sort

# 3. 빌드 테스트
./gradlew build
```

### 2. 표준 설정 템플릿 사용

모든 서비스에서 동일한 기본 설정 구조 적용:

```properties
# 표준 application.properties 템플릿
spring.application.name=${service.name}
server.port=${service.port}

# Eureka Client 설정 (마이크로서비스의 경우)
eureka.client.service-url.defaultZone=${EUREKA_URL:http://eureka-server:8761/eureka/}
eureka.instance.prefer-ip-address=true

# Management endpoints
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.health.show-details=always
management.endpoint.prometheus.enabled=true
management.metrics.export.prometheus.enabled=true
```

### 3. 자동 검증 스크립트

```bash
#!/bin/bash
# validate-config.sh
for service in server gateway fe deploy user; do
    echo "Validating $service config..."
    if grep -q "spring.config.import" $service/src/main/resources/application.*; then
        echo "⚠️  $service has config.import - verify referenced files exist"
    fi
    ./gradlew :$service:build > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ $service builds successfully"
    else
        echo "❌ $service build failed"
    fi
done
```

## 관련 파일

- `fe/src/main/resources/application.properties`
- 다른 서비스의 `application.properties` (참조용)
- `.gitignore` (민감 정보 파일 제외용)

## 참고 자료

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Config Data Import](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.files.importing)
- [PropertySourceLoader](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/env/PropertySourceLoader.html)