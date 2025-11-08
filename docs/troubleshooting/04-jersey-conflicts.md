# Jersey JAX-RS 라이브러리 의존성 충돌

## 문제 설명

Eureka Server 실행 중 Jersey 클라이언트 관련 메소드를 찾을 수 없다는 오류 발생:

```
java.lang.NoSuchMethodError: 'boolean org.glassfish.jersey.client.ClientRequest.isCancelled()'
    at com.netflix.discovery.shared.transport.jersey.AbstractJerseyEurekaHttpClient.cancel(AbstractJerseyEurekaHttpClient.java:179)
```

## 오류 증상

### 1. NoSuchMethodError 발생
```bash
Exception in thread "main" java.lang.NoSuchMethodError: 
'boolean org.glassfish.jersey.client.ClientRequest.isCancelled()'
    at com.netflix.discovery.shared.transport.jersey.AbstractJerseyEurekaHttpClient.cancel
    at com.netflix.discovery.DiscoveryClient.<init>
    at org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration
```

### 2. Eureka Server 시작 실패
```bash
Application run failed
***************************
APPLICATION FAILED TO START
***************************
```

### 3. 클라이언트 등록 불가
```bash
Unable to register with Eureka server
DiscoveryClient initialization failed
```

## 근본 원인 분석

### 1. Jersey 라이브러리 버전 불일치

**문제의 핵심:**
- Netflix Eureka가 사용하는 Jersey 클라이언트 API와 수동으로 추가된 Jersey 의존성 간 버전 충돌
- Spring Cloud 2024.0.2와 호환되지 않는 Jersey 버전 사용

### 2. 중복 의존성 문제

**문제가 있던 설정:**
```gradle
// server/build.gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
    
    // 🚨 문제: 수동으로 추가한 Jersey 의존성들
    implementation 'org.glassfish.jersey.core:jersey-client:3.1.3'
    implementation 'org.glassfish.jersey.inject:hk2-locator:3.1.3' 
    implementation 'org.glassfish.jersey.inject:jersey-hk2:3.1.3'
}
```

**원인 분석:**
1. `spring-cloud-starter-netflix-eureka-server`는 내부적으로 호환되는 Jersey 버전을 포함
2. 수동으로 추가된 Jersey 3.1.3이 Eureka의 기대하는 API와 호환되지 않음
3. `ClientRequest.isCancelled()` 메소드가 해당 버전에서 제거되거나 변경됨

### 3. 의존성 트리 충돌

```bash
# 문제가 되는 의존성 트리
+--- org.springframework.cloud:spring-cloud-starter-netflix-eureka-server
|    +--- com.netflix.eureka:eureka-client (Jersey 2.x 기반)
|    \--- com.netflix.eureka:eureka-core
+--- org.glassfish.jersey.core:jersey-client:3.1.3  # 충돌!
```

## 해결 방법

### 1. 수동 Jersey 의존성 제거

`server/build.gradle`에서 모든 수동 Jersey 의존성 제거:

```gradle
dependencies {
    // ✅ 필요한 의존성만 유지
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
    
    // ❌ 제거: 수동 Jersey 의존성들
    // implementation 'org.glassfish.jersey.core:jersey-client:3.1.3'
    // implementation 'org.glassfish.jersey.inject:hk2-locator:3.1.3' 
    // implementation 'org.glassfish.jersey.inject:jersey-hk2:3.1.3'
    
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### 2. Spring Cloud BOM 의존성 활용

Root `build.gradle`에서 Spring Cloud BOM이 Jersey 버전을 자동 관리하도록 설정:

```gradle
ext {
    set('springCloudVersion', '2024.0.2')
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}
```

### 3. 의존성 충돌 검증

충돌 확인을 위한 의존성 트리 분석:

```bash
# 의존성 트리 확인
./gradlew :server:dependencies --configuration implementation

# Jersey 관련 의존성만 필터링
./gradlew :server:dependencies --configuration implementation | grep jersey
```

**정상적인 출력 예시:**
```
+--- org.springframework.cloud:spring-cloud-starter-netflix-eureka-server
     +--- com.netflix.eureka:eureka-client:2.0.2
          +--- org.glassfish.jersey.core:jersey-client:2.39  # Spring Cloud 관리 버전
```

### 4. 빌드 검증

```bash
# 클린 빌드로 의존성 재구성
./gradlew clean

# 서버 모듈만 빌드 테스트
./gradlew :server:build

# 전체 빌드 실행
./gradlew build
```

## 검증 방법

### 1. 로컬 실행 테스트

```bash
# JAR 파일 실행
java -jar server/build/libs/server-0.0.1-SNAPSHOT.jar
```

**성공적인 시작 로그:**
```
Started ServerApplication in 12.345 seconds (JVM running for 13.123)
Tomcat started on port(s): 8761 (http) with context path ''
```

### 2. Docker 컨테이너 테스트

```bash
# Docker 이미지 빌드
docker build -t test-eureka ./server

# 컨테이너 실행
docker run -p 8761:8761 test-eureka
```

### 3. Eureka 대시보드 접근

브라우저에서 `http://localhost:8761` 접속하여 정상 동작 확인

### 4. 의존성 분석

Jersey 관련 의존성이 Spring Cloud에서 관리하는 버전만 사용하는지 확인:

```bash
./gradlew :server:dependencies | grep -E "(jersey|glassfish)"
```

**올바른 출력 (Spring Cloud 관리 버전만):**
```
|    |    +--- org.glassfish.jersey.core:jersey-client:2.39
|    |    +--- org.glassfish.jersey.inject:jersey-hk2:2.39
```

## 최종 Server 모듈 설정

### build.gradle
```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.11'
    id 'io.spring.dependency-management' version '1.1.7'
}

dependencies {
    // Eureka Server (Jersey 의존성 자동 관리)
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
    
    // Monitoring
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
    
    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### ServerApplication.java
```java
@SpringBootApplication
@EnableEurekaServer  // Eureka Server 활성화
public class ServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
```

## 예방 방법

### 1. 의존성 관리 원칙
- **Spring Boot Starter 우선 사용**: 수동 의존성 추가는 최소화
- **BOM 활용**: Spring Cloud BOM으로 버전 호환성 보장
- **의존성 트리 확인**: 새 의존성 추가 시 충돌 여부 검증

### 2. 의존성 충돌 감지
```bash
# 정기적 의존성 트리 검토
./gradlew dependencies

# 중복 의존성 탐지
./gradlew dependencyInsight --dependency jersey-client
```

### 3. 테스트 자동화
```gradle
// build.gradle에 의존성 검증 태스크 추가
task verifyDependencies {
    doLast {
        configurations.implementation.resolvedConfiguration.resolvedArtifacts.each {
            if (it.name.contains('jersey') && !it.moduleVersion.id.version.startsWith('2.')) {
                throw new GradleException("Incompatible Jersey version: ${it.moduleVersion.id}")
            }
        }
    }
}

build.dependsOn verifyDependencies
```

## 관련 이슈

### Spring Cloud와 Jersey 호환성
- Spring Cloud 2024.0.x는 Jersey 2.39를 사용
- Jersey 3.x는 javax → jakarta 패키지 마이그레이션으로 호환성 문제
- Netflix Eureka는 아직 Jersey 3.x 완전 지원하지 않음

## 관련 파일

- `server/build.gradle`
- `build.gradle` (Root)
- `server/src/main/java/softbank/hackathon/server/ServerApplication.java`

## 참고 자료

- [Spring Cloud Release Train](https://spring.io/projects/spring-cloud#release-trains)
- [Netflix Eureka Dependencies](https://github.com/Netflix/eureka/blob/master/build.gradle)
- [Jersey 2.x vs 3.x Migration](https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest/migration.html)