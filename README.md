# SoftBankHackathon2025

'클라우드로 미래를 만든다' - AWS·GCP·Azure 등을 활용하여 프로덕트를 개발합니다.

## 아키텍처 & 주요 모듈

- **server** (`8761`): Spring Cloud Eureka Server, 모든 마이크로서비스의 서비스 디스커버리 허브입니다.
- **gateway** (`8080`): Spring Cloud Gateway, Eureka 기반 라우팅/로깅/Actuator 노출.
- **fe** (`8081`): Thymeleaf 프론트엔드, 배포 대시보드(수동 Actions 트리거 UI 포함).
- **deploy** (`8082`): 배포 파이프라인 제어/기록 서비스.
- **user** (`8083`): 사용자 관리용 백엔드.
- **monitoring/**: Prometheus(9090) + Grafana(3000) 스택과 프로비저닝된 대시보드/알림 설정.
- `start-microservices.sh`: 루트에서 전체 Gradle 빌드 → Docker 이미지 빌드 → `docker-compose up -d`까지 자동화.

```
├── server/      # Eureka Server
├── gateway/     # API Gateway
├── fe/          # Frontend + Actions trigger UI (index.html)
├── deploy/      # Deploy service
├── user/        # User domain service
├── docs/        # Troubleshooting / contributor docs
├── monitoring/  # Prometheus & Grafana configs
└── docker-compose.yml
```

## 개발 환경 & 준비물

1. **Java 17** (프로젝트에서는 `/mnt/d/projects/softbank/jdk-17.0.12+7` 사용)
   ```bash
   export JAVA_HOME=/mnt/d/projects/softbank/jdk-17.0.12+7
   export PATH=$JAVA_HOME/bin:$PATH
   ```
2. **Gradle Wrapper**: 루트에서 `./gradlew` 사용 (개별 설치 불필요).
3. **Docker & Docker Compose**: 멀티 컨테이너 실행/모니터링 스택 구동에 필요.
4. **환경 변수**: `.env` 파일에 Grafana/Slack/Webhook 등의 시크릿을 정의하면 `docker-compose`와 Grafana가 자동으로 로드합니다.

## 빌드 & 테스트

```bash
# 전체 서비스 빌드 (테스트 제외)
./gradlew clean build -x test

# 전체 테스트 실행
./gradlew test

# 특정 모듈만 빌드/실행
./gradlew :gateway:build
./gradlew :user:bootRun        # 개발 시 단일 서비스 실행
```

## 실행 방법

### 1) JAR 실행 (로컬 개발용)

Eureka Server를 가장 먼저 띄운 뒤 각 서비스 JAR을 실행합니다. 각 서비스의 기본 포트는 `application.properties`에 이미 정의되어 있습니다.

```bash
java -jar server/build/libs/server-0.0.1-SNAPSHOT.jar                    # 8761
java -jar gateway/build/libs/gateway-0.0.1-SNAPSHOT.jar                  # 8080
java -jar fe/build/libs/fe-0.0.1-SNAPSHOT.jar                            # 8081
java -jar deploy/build/libs/deploy-0.0.1-SNAPSHOT.jar                    # 8082
java -jar user/build/libs/user-0.0.1-SNAPSHOT.jar                        # 8083
```

### 2) Docker Compose 실행 (권장)

루트의 `docker-compose.yml`은 5개 서비스 + Prometheus + Grafana를 동시에 구동합니다.

```bash
# 이미지 빌드 및 구동
docker compose up --build -d

# 중단
docker compose down
```

`start-microservices.sh`를 실행하면 Gradle 빌드, 이미지 태깅(`kwa06001/*`), 기존 컨테이너 정리, `docker-compose up -d`까지 일괄 처리됩니다.

## 서비스 확인 & 모니터링

- **Eureka 대시보드**: http://localhost:8761
  - 등록 서비스: `GATEWAY`, `FE`, `DEPLOY`, `USER`
- **헬스 체크**:
  ```bash
  curl http://localhost:8080/actuator/health
  curl http://localhost:8081/actuator/health
  curl http://localhost:8082/actuator/health
  curl http://localhost:8083/actuator/health
  ```
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (기본 계정 `admin` / `.env`의 `GRAFANA_PASSWORD`)
- **Troubleshooting**: 반복된 문제나 최근 hotfix 내역은 `docs/troubleshooting/` 디렉터리(특히 `08-fix-hotfix-retrospective.md`)를 참고하세요.

## 주의사항

- 모든 서비스는 Eureka Server가 가용 상태일 때만 정상적으로 기동/등록됩니다.
- CI/CD, Docker, Grafana 설정은 `.env`와 `docker-compose.yml`에 종속되므로 값을 변경하면 모니터링/배포 스크립트도 함께 업데이트해야 합니다.
- GitHub Actions 수동 트리거 UI(`index.html`)를 사용할 때는 Actions:write + Contents:read 권한이 있는 PAT만 입력하세요.
