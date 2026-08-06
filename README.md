# GPS 지오펜스 출퇴근 관리 시스템

GPS 위치와 지오펜스를 이용해 직원의 출퇴근을 처리하는 시스템입니다.

| 영역 | 기술 |
|---|---|
| Backend | Spring Boot 3.2 · Java 17 · PostgreSQL(Flyway 마이그레이션) · Redis |
| Admin Web | React 18 · TypeScript · Vite · TanStack Query |
| Mobile App | Flutter 3 (Riverpod) |
| Infra | Docker Compose · Nginx |

DB는 로컬 Docker Postgres, Supabase 등 관리형 Postgres 모두 동일한 환경변수(`DB_HOST`/`DB_PORT`/`POSTGRES_*`)로 연결할 수 있습니다. 자세한 내용은 [환경변수 설정](#1-환경변수-설정)을 참고하세요.

---

## 빠른 시작 (로컬 개발)

### 사전 요구사항

- Docker Desktop 4.x 이상
- Java 17 (JDK) — backend 로컬 실행 시
- Node.js 20 LTS — admin-web 로컬 실행 시
- Flutter 3 — mobile-app 실행 시

### 1. 환경변수 설정

```bash
cp .env.example .env
# .env 열어서 비밀번호 등 필수 값 확인
```

기본값은 로컬 Docker Postgres를 가리킵니다. Supabase 등 외부 관리형 Postgres를 쓰려면 `.env`의
`DB_HOST`/`DB_PORT`/`DB_SSL_MODE`(require)/`POSTGRES_USER`/`POSTGRES_PASSWORD`를 발급받은 값으로
바꾸면 됩니다(이 경우 아래 2단계의 로컬 `postgres` 컨테이너는 띄우지 않아도 됩니다).

### 2. 개발 DB 기동 (PostgreSQL + Redis)

```bash
# 방법 A: 스크립트
bash scripts/dev-up.sh

# 방법 B: 직접 실행
docker compose up -d postgres redis
```

접속 정보 (기본값):

| 서비스 | 주소 |
|---|---|
| PostgreSQL | `localhost:5432` / DB: `attendance` / User: `attendance` |
| Redis | `localhost:6379` |

### 3. Backend 로컬 실행 (hot-reload)

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
# http://localhost:8080
# http://localhost:8080/actuator/health
```

### 4. Admin Web 로컬 실행 (hot-reload)

```bash
cd admin-web
npm install
npm run dev
# http://localhost:3000 (admin-web/vite.config.ts에서 포트 고정)
```

### 5. Mobile App 로컬 실행 (Android 에뮬레이터/실기기)

```bash
cd mobile-app
flutter pub get
flutter run -d <deviceId>   # 예: emulator-5554, adb devices로 확인
```

Android는 즉시 빌드·실행됩니다. iOS는 `ios/` 하위에 Xcode 프로젝트 파일이 아직 없어
`flutter create .`로 골격을 보완해야 실행할 수 있습니다(자세한 내용은 [알려진 제한 사항](#알려진-제한-사항) 참고).

### 6. 전체 스택 컨테이너 실행 (backend + admin-web 코드 완성 후)

```bash
bash scripts/full-up.sh

# 또는
docker compose --profile full up -d --build
```

| 서비스 | 주소 |
|---|---|
| Admin Web + API | `http://localhost:80` |
| Backend 직접 | `http://localhost:8080` |

---

## 환경 종료

```bash
# 종료 (데이터 유지)
bash scripts/dev-down.sh

# 종료 + DB 데이터 삭제 (완전 초기화)
bash scripts/dev-down.sh --clean
```

---

## 디렉터리 구조

```
.
├── CLAUDE.md                  # AI 개발 가이드
├── .claude/agents/            # 영역별 Agent 지침
├── docs/                      # 요구사항·API·ADR 문서
├── backend/                   # Spring Boot API 서버
│   └── Dockerfile
├── admin-web/                 # React 관리자 웹
│   └── Dockerfile
├── mobile-app/                # Flutter 직원 앱
├── infra/
│   ├── nginx/                 # Nginx 설정
│   └── postgres/init/         # PostgreSQL 초기화 SQL
├── scripts/
│   ├── dev-up.sh              # DB만 기동
│   ├── dev-down.sh            # 환경 종료
│   └── full-up.sh             # 전체 스택 기동
├── docker-compose.yml
└── .env.example               # 환경변수 템플릿
```

---

## 핵심 도메인 규칙 (요약)

- **출퇴근 판정은 서버에서** 수행. 클라이언트의 `withinGeofence` 값을 신뢰하지 않음.
- 지오펜스 허용 반경 기본값: **100m**, GPS 정확도 기준: **50m 이하**
- 모든 시간 저장은 **UTC**, 표시·근무일 판정은 `Asia/Seoul`
- 관리자 수정·승인 처리에는 반드시 **감사 로그** 기록
- **백그라운드 상시 위치 추적 없음** — 출퇴근 시점에만 GPS 수집

### 권한 모델 (2축)

- **역할(`role`: EMPLOYEE/MANAGER/HR_ADMIN/SYSTEM_ADMIN)** — API 엔드포인트 인가(`@PreAuthorize`)에 쓰이는 축.
- **권한레벨(`level`, 공통코드 그룹 `LEVEL_ROLL`: SYSADMIN/PRESIDENT/HRADMIN/DIVHEAD/HQHEAD/OFFICEHEAD/TEAMLEAD/PARTLEAD/EMPLOYEE)** —
  조직 계층상 직책을 나타내며, 화면별 **조회 범위**(본인만 / 본인+하위조직 / 전체)를 결정한다.
  관리자웹 **권한관리** 화면에서 레벨 코드와 메뉴별 표시·기능 권한을 관리한다.

자세한 요구사항: [`docs/gps_geofence_attendance_implementation_spec.md`](docs/gps_geofence_attendance_implementation_spec.md)

---

## 개발 현황

- [x] Docker Compose 환경 구성 (로컬 Postgres/Redis, Supabase 등 관리형 Postgres로도 대체 가능)
- [x] Phase 1: Backend 기반 구성 (Spring Boot · 인증(JWT) · 공통 응답/예외 · Flyway · 감사 로그 기반)
- [x] Phase 2: 핵심 출퇴근 API (Haversine 지오펜스 판정, 출근/퇴근, 휴게, 중복 방지, 서버측 GPS 이동속도 기반 모의위치 탐지)
- [x] Phase 3: 조회·수정 요청·승인 (오늘/일별/월별 근태, 근태 수정 요청·승인/반려, 승인 이력, 알림)
- [x] Phase 4: 관리자 웹 — 아래 12개 화면 모두 구현
  - 대시보드, MY 출근부(실제 GPS 출퇴근), 근태조회(일별/월별/출근부 일괄수정 — 시각만 입력해도 서버가 근무·휴게시간 자동 계산)
  - 승인함(대기중/요청이력 — 근태수정·휴가·외근/출장·근무지 변경요청·근무제 변경요청 5종 통합 승인, 승인자·상세유형 표시)
  - 직원 관리(부서·이름 검색, 엑셀 일괄등록 — 소속부서명·직급을 부서·권한레벨로 자동 매핑)
  - 근무지/부서/근무제 관리, 일정관리, 휴일·휴가 관리, 감사 로그
  - 권한관리(LEVEL_ROLL 등 공통코드 관리 + 레벨별 메뉴/기능 표시 권한 설정)
  - 모든 조회 화면은 로그인 계정의 권한레벨에 따라 본인/본인+하위조직/전체로 범위가 자동으로 좁혀짐
- [x] Phase 5: 직원 모바일 앱 (로그인/자동 로그인, 생체인증 로그인, 기기 등록, 홈, 근태 이력, 근태 수정 요청, 휴가 신청, 근무지·근무제 변경 요청, 일정, 프로필, 알림 조회)
- [x] Supabase 등 외부 관리형 Postgres 연동 (동일 코드로 `DB_HOST`/`DB_SSL_MODE` 등 환경변수만 교체)
- [ ] Phase 6: 운영 품질 (테스트·CI·모니터링) — 단위 테스트 및 CI는 구성되어 있으나 통합 테스트는 로컬 Docker/Testcontainers 환경 검증 필요

### 알려진 제한 사항

- 모바일 앱은 Android 기준으로 빌드·실행이 확인되었습니다. iOS는 `ios/` 하위에 Xcode 프로젝트 파일(`.xcodeproj`)이 아직 없어 `flutter create .`로 골격을 보완해야 실행할 수 있습니다.
- 생체인증(`local_auth`) 적용 시 Android의 `MainActivity`가 `FlutterFragmentActivity`를 상속해야 하는데, 현재는 `FlutterActivity`를 상속하고 있어 이 부분은 아직 반영되지 않았습니다.
- 알림은 현재 앱 내 목록 조회(polling) 방식만 구현되어 있고, FCM 등을 통한 실시간 푸시 발송은 아직 없습니다.
- 고도화 범위 중 급여 연계, QR/Wi-Fi/BLE 보조 인증, 이상 근태 자동 탐지는 아직 남아 있습니다. (휴가·외근·출장·근무지·근무제 변경 신청/승인은 이미 구현되어 있습니다.)
