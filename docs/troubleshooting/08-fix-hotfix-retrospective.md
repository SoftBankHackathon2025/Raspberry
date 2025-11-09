# 최근 Fix / Hotfix 커밋 트러블슈팅 리포트

## 개요
- 2025-11-04 ~ 2025-11-08 사이 작성된 최근 100개 커밋 중 `fix`·`hotfix` 태그가 붙은 변경을 전수 조사했다.
- 공통적으로 **CI/CD 파이프라인 안정화**, **Docker 빌드 신뢰성**, **모니터링/경보 품질**, **수동 배포 도구 개선**에 초점이 맞춰졌다.
- 아래 항목은 커밋 해시와 함께 문제가 재현되는 방식, 수정 방법, 향후 점검 포인트를 요약한다.

## CI/CD 트리거 조건 불일치
- **증상**: 수동 실행(`workflow_dispatch`) 시 배포 Job이 건너뛰거나, 불필요한 `push` 이벤트로 중복 실행.
- **원인**: `.github/workflows/ci-cd.yml`과 `code-scanning-improved.yml`의 `on.push` 및 `if` 조건이 `main`/`push` 조합으로 고정되어 있었음 (`1c7a6ba`, `a8bdc00`).
- **조치**: `Deploy to EC2` Job 조건을 `workflow_dispatch`까지 허용하고, UI 트리거 페이지에서는 `ref`를 `main`으로 일원화. 이후 `push` 트리거는 비활성화하여 중복 실행을 차단 (`c109267`, `f1cec11`).
- **재현 시 점검**: GitHub Actions 화면에서 이벤트 타입을 확인하고, `if: ...` 조건에 브랜치와 이벤트가 모두 일치하는지 검토.

## AWS SSM Action 사용 오류
- **증상**: GitHub Actions가 `aws-actions/aws-ssm-send-command@v1`를 찾지 못하거나 입력 파라미터 오류로 실패.
- **원인**: 잘못된 `uses` 경로(`/amazon-ssm...`), 자격 증명 설정 누락, SSM 명령이 필요로 하는 `instance-ids` 입력 형식 불일치 (`b5e8970`, `873b6f7`, `0e6e827`, `e02de64`, `64da2ec`).
- **조치**: 정식 액션 이름 `aws-actions/amazon-ssm-send-command@v1`을 사용하고, 별도 단계에서 `configure-aws-credentials@v4`로 STS 토큰을 셋업. SSM 명령은 EC2에서 `docker-compose pull/up`을 실행하도록 스크립트화.
- **재현 시 점검**: 워크플로 로그에서 `Resource not accessible` 메시지가 뜨면 `AWS_REGION`, `EC2_INSTANCE_ID` 시크릿과 액션 버전을 다시 확인.

## Docker 빌드/컴포즈 실패
- **증상**: `docker build` 단계에서 `context must be a directory` 에러, `docker-compose`는 루트에서 Dockerfile을 찾지 못함.
- **원인**: 빌드 컨텍스트를 모듈 디렉터리로 전달하면서 루트 공용 자원에 접근하지 못한 것이 시작(`daa33dd`). 이어서 `docker build` 명령의 `.` 인자가 빠지거나 역슬래시 오타가 존재 (`940f5a2`, `af79376`).
- **조치**: 모든 Docker 빌드를 프로젝트 루트 컨텍스트로 통일하고, Compose 서비스도 `context: .`, `dockerfile: ./service/Dockerfile` 형태로 갱신 (`4edfc06`, `b810561`). EC2 내 `docker-compose.yml`을 sed로 이미지 참조 방식으로 변환하는 절차도 추가 (`1eddb59`).
- **재현 시 점검**: 로컬에서 `docker build -f ./gateway/Dockerfile .`를 직접 실행해보고, CI 로그의 빌드 경로가 루트인지 확인.

## 테스트 실패로 인한 배포 중단
- **증상**: Gateway 테스트가 실제 Eureka에 의존하면서 CI에서 지속적으로 실패.
- **원인**: `@SpringBootTest` 기반 통합 테스트가 외부 인프라를 요구 (`67e062e`).
- **조치**: 문제 테스트를 주석 처리하여 빌드 실패를 막고, 대신 `./gradlew test` 실행 전 특정 프로필에서만 활성화하도록 TODO 남김. 동시에 각 마이크로서비스에 Actuator/Prometheus 노출 설정을 통일하여 헬스체크 기반 검증이 가능하도록 함 (`858245b`).
- **재현 시 점검**: 실패 로그에서 `Could not locate running Eureka` 문구가 보이면 테스트 범위를 축소하거나 Testcontainers를 도입.

## 모니터링/알림 설정 불일치
- **증상**: Grafana가 `.env` 시크릿을 로드하지 못해 Slack Webhook URL이 비어 있거나, Alert 라벨이 잘못 표기.
- **원인**: `docker-compose.yml`의 Grafana 서비스가 `env_file`을 참조하지 않았고, Alert 라벨이 `application: frontend`로 잘못 지정 (`d021709`, `dc76fff`).
- **조치**: Grafana 서비스에 `env_file: .env`를 추가하고, Alert/Contact 포맷을 개선해 요약/설명을 모두 Slack으로 전달 (`1934a6d`).
- **재현 시 점검**: Grafana pod에서 `env` 확인 후 `GF_SECURITY_ADMIN_PASSWORD`가 비어 있으면 compose 구성을 재점검.

## GitHub API 기반 수동 배포 페이지 오류
- **증상**: `index.html`에서 Actions 트리거 시 잘못된 입력(JSON body, 토큰 권한, ref) 때문에 422 응답.
- **원인**: 워크플로가 불필요한 `env` 파라미터를 요구하거나, `repo` scope 토큰을 전제로 안내 (`035b09f`, `a8bdc00` 이전), 불필요한 입력 필드 유지 (`46643d9`, `9f037fb`).
- **조치**: 필요한 스코프를 `Actions:write, Contents:read`로 명시하고, `inputs`를 워크플로 별로 분기. 또한 토큰 입력란 엔터 키 트리거를 막아 오동작을 방지.
- **재현 시 점검**: 브라우저 개발자 도구의 `fetch` 요청 Body를 확인해 `ref`, `inputs`가 실제 워크플로 요구 사항과 일치하는지 비교.

## 추가 권고
1. **커밋 태깅 유지**: `fix/*`, `hotfix:*` 네이밍이 동일 이슈를 쉽게 추적하게 해주므로, 연관 이슈 번호(예: `#3`)를 계속 첨부한다.
2. **회귀 테스트**: 위 이슈는 주로 CI/CD YAML과 Compose에 집중되어 있으므로, 변경 전 `act` 혹은 `docker compose config`로 로컬 검증 절차를 집중화한다.
3. **트리거 페이지 관리**: `index.html` 변경 시에는 `gh api`로 직접 워크플로를 호출해보고, 응답 코드/메시지를 문서에 반영한다.
