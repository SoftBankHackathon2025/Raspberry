# Spring Cloud Gateway 의존성 문제

## 문제 설명

Spring Cloud Gateway 모듈에서 라우팅 기능이 동작하지 않고 다음과 같은 오류가 발생:

```
The gateway module is missing the Spring Cloud Gateway starter
웹플럭스 모듈이 아닌 servlet 스택으로 실행됨
게이트웨이 라우트가 등록되지 않음
```

## 오류 증상

1. **Gateway 스타터 의존성 누락**
   - `spring-cloud-starter-gateway` 의존성이 추가되지 않음
   - 게이트웨이 관련 자동 설정이 활성화되지 않음

2. **Servlet/Reactive 스택 충돌**
   - `spring-boot-starter-web` (Servlet 스택)과 `spring-boot-starter-webflux` (Reactive 스택) 동시 존재
   - Spring Boot가 Servlet 스택을 우선 선택하여 WebFlux 기반 Gateway 동작 불가

3. **라우트 등록 실패**
   - Gateway 관련 빈들이 생성되지 않음
   - 서비스 디스커버리 기반 라우팅 불가능

## 근본 원인 분석

### 1. 의존성 구조 문제
```gradle
// 문제가 있던 설정
dependencies {
    // Gateway starter가 누락됨
    implementation 'org.springframework.boot:spring-boot-starter-web'  // Servlet
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

### 2. 공통 의존성 설정의 부작용
Root `build.gradle`에서 모든 모듈에 `spring-boot-starter-web`을 추가했기 때문에 Gateway 모듈에서도 Servlet 스택이 활성화됨:

```gradle
// build.gradle (Root)
subprojects {
    dependencies {
        implementation 'org.springframework.boot:spring-boot-starter-web'  // 모든 모듈에 적용
    }
}
```

## 해결 방법

### 1. Gateway 모듈에 올바른 의존성 추가

`gateway/build.gradle`에 Gateway 스타터 의존성 추가:

```gradle
dependencies {
    // Spring Cloud Gateway 의존성 추가
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
    
    // Eureka Client (서비스 디스커버리용)
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    
    // Circuit Breaker 지원
    implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j'
    
    // Actuator (모니터링용)
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

### 2. Servlet 스택 의존성 제외

Root `build.gradle`에서 Gateway 모듈에 대해서만 `spring-boot-starter-web` 제외:

```gradle
subprojects {
    dependencies {
        // Gateway 모듈을 제외하고 Web starter 추가
        if (project.name != 'gateway') {
            implementation 'org.springframework.boot:spring-boot-starter-web'
        }
        
        implementation 'org.springframework.boot:spring-boot-starter-validation'
        compileOnly 'org.projectlombok:lombok'
        annotationProcessor 'org.projectlombok:lombok'
    }
}
```

### 3. Gateway 애플리케이션 설정

`GatewayApplication.java`에 서비스 디스커버리 활성화:

```java
@SpringBootApplication
@EnableDiscoveryClient  // 명시적 서비스 디스커버리 활성화
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

## 검증 방법

### 1. 빌드 검증
```bash
./gradlew :gateway:build
```

### 2. 실행 로그 확인
Gateway 실행 시 다음 로그 확인:
```
Started GatewayApplication in X.XXX seconds (JVM running for Y.YYY)
Netty started on port(s): 8080  # WebFlux/Netty 사용 확인
```

### 3. Actuator 엔드포인트 확인
```bash
# Gateway 라우트 확인
curl http://localhost:8080/actuator/gateway/routes

# 등록된 서비스 확인  
curl http://localhost:8080/actuator/health
```

### 4. 라우팅 테스트
```bash
# 서비스 디스커버리 기반 라우팅 테스트
curl http://localhost:8080/user/health
curl http://localhost:8080/deploy/health
```

## 예방 방법

1. **모듈별 의존성 분리**: 각 모듈의 목적에 맞는 스타터만 추가
2. **의존성 충돌 검증**: `./gradlew dependencies` 명령으로 의존성 트리 확인
3. **테스트 코드 작성**: Gateway 라우팅에 대한 통합 테스트 작성

## 관련 파일

- `build.gradle` (Root)
- `gateway/build.gradle`
- `gateway/src/main/java/softbank/hackathon/gateway/GatewayApplication.java`
- `gateway/src/main/resources/application.yml`

## 참고 자료

- [Spring Cloud Gateway Reference](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [Spring Boot WebFlux vs MVC](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-developing-web-applications)