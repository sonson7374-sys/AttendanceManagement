# CLAUDE.md — GPS 지오펜스 출퇴근 관리 시스템

## 1. 프로젝트 목표

GPS 위치와 지오펜스(Geofence)를 이용해 직원의 출근·퇴근을 처리하는 시스템을 구현한다.

구성 영역은 다음과 같다.

- 직원용 모바일 앱: 출퇴근, 오늘 근태, 월별 근태, 근태 수정 요청, 알림
- 관리자용 웹: 직원·조직·근무지·근무제 관리, 근태 조회·보정, 승인, 통계
- 백엔드 API: 인증, 지오펜스 판정, 근태 도메인, 승인, 감사 로그

기준 요구사항은 `docs/gps_geofence_attendance_implementation_spec.md`를 따른다.

---

## 2. Claude의 기본 작업 원칙

1. 작업 시작 전에 관련 Agent 문서를 읽는다.
2. 요구사항이 모호해도 개발을 멈추지 말고 본 문서의 기본값을 적용한다.
3. 기능 단위로 작은 변경을 만들고, 변경 후 테스트한다.
4. API·DB·화면 중 하나를 변경하면 연관 영역의 영향도도 확인한다.
5. 구현되지 않은 기능을 구현된 것처럼 표시하지 않는다.
6. 임시 우회 코드, 하드코딩, 보안 우회는 남기지 않는다.
7. 민감정보·비밀번호·토큰·실제 위치정보를 저장소에 커밋하지 않는다.
8. 모든 시간 저장은 UTC, 사용자 표시와 근무일 판정은 `Asia/Seoul`을 기본으로 한다.
9. 출퇴근 판정은 클라이언트가 아니라 서버에서 최종 결정한다.
10. 관리자 수정과 승인 처리에는 반드시 감사 로그를 남긴다.

---

## 3. 권장 기술 스택

### Backend

- Java 17
- Spring Boot 3.2+
- Spring Security 6
- Spring Data JPA
- Gradle Wrapper
- PostgreSQL
- Flyway
- JWT Access/Refresh Token
- Testcontainers

### Admin Web

- React 18+
- TypeScript
- Vite
- React Router
- TanStack Query
- React Hook Form
- 지도 SDK는 환경변수로 공급자 선택

### Mobile

기본안은 Flutter로 한다.

- Flutter 3+
- Riverpod 또는 Bloc 중 하나만 선택
- Dio
- Geolocator
- Secure Storage
- Firebase Messaging 선택

### Infra

- Docker Compose
- Nginx
- PostgreSQL
- GitHub Actions 또는 사내 CI

---

## 4. 저장소 권장 구조

```text
.
├── CLAUDE.md
├── .claude/
│   └── agents/
├── docs/
│   ├── gps_geofence_attendance_implementation_spec.md
│   ├── api/
│   ├── adr/
│   └── diagrams/
├── backend/
├── admin-web/
├── mobile-app/
├── infra/
└── scripts/
```

기존 저장소 구조가 있다면 무리하게 전면 변경하지 말고 현재 구조를 우선 존중한다.

---

## 5. Agent 선택 규칙

| 업무 | Agent 문서 |
|---|---|
| 요구사항 정리, 작업 분해, 범위 관리 | `.claude/agents/product-planner.md` |
| Spring Boot API 및 도메인 구현 | `.claude/agents/backend-agent.md` |
| DB 모델, Flyway, 쿼리, 인덱스 | `.claude/agents/database-agent.md` |
| GPS, 거리 계산, 지오펜스 정책 | `.claude/agents/geofence-agent.md` |
| Flutter 직원 앱 | `.claude/agents/mobile-agent.md` |
| React 관리자 웹 | `.claude/agents/admin-web-agent.md` |
| 인증, 인가, 개인정보, 보안 | `.claude/agents/security-agent.md` |
| 테스트 전략 및 테스트 코드 | `.claude/agents/qa-agent.md` |
| Docker, CI/CD, 운영환경 | `.claude/agents/devops-agent.md` |
| 전체 코드 리뷰 및 품질 게이트 | `.claude/agents/reviewer-agent.md` |

여러 영역이 걸치면 `product-planner → 담당 Agent → qa-agent → reviewer-agent` 순서로 수행한다.

---

## 6. MVP 구현 순서

### Phase 1. 기반 구성

- 멀티 애플리케이션 디렉터리 구성
- PostgreSQL 및 Flyway 구성
- 공통 응답·예외 처리
- 인증·인가
- 사용자·조직·근무지 기본 모델

### Phase 2. 핵심 출퇴근

- 사용자별 허용 근무지 조회
- Haversine 거리 계산
- 출근 처리
- 퇴근 처리
- 중복 요청 방지
- 원본 위치 이벤트 저장
- 일별 근태 요약 생성

### Phase 3. 조회 및 수정 요청

- 오늘 근태
- 일별·월별 근태
- 근태 수정 요청
- 승인·반려
- 감사 로그

### Phase 4. 관리자 웹

- 대시보드
- 직원·조직 관리
- 근무지 지도 및 반경 관리
- 일별·월별 근태
- 근태 보정
- 승인함

### Phase 5. 직원 앱

- 로그인
- 위치 권한
- 홈 및 출퇴근
- 근태 조회
- 수정 요청
- 알림

### Phase 6. 운영 품질

- 통합 테스트
- 보안 점검
- Docker Compose
- CI
- 모니터링·로그
- 운영 매뉴얼

---

## 7. 핵심 도메인 규칙

### 7.1 출근

- 활성 사용자만 가능하다.
- 해당 날짜에 출근 기록이 이미 있으면 중복 출근을 거부한다.
- 서버가 좌표 간 거리를 재계산한다.
- 위치 측정시각이 오래된 경우 거부한다.
- 정확도 기준을 충족해야 한다.
- 허용 근무지 중 가장 가까운 근무지를 판정한다.
- 출근 이벤트 원본과 일별 요약을 같은 트랜잭션에서 저장한다.

### 7.2 퇴근

- 유효한 출근 기록이 있어야 한다.
- 이미 퇴근한 경우 거부한다.
- 미종료 휴게가 있으면 정책에 따라 자동 종료 또는 오류 처리한다. 기본은 오류 처리다.
- 자정 이후 퇴근은 출근일의 근태에 귀속한다.

### 7.3 GPS 기본 정책

```yaml
geofence:
  default-radius-meters: 100
  max-accuracy-meters: 50
  max-location-age-seconds: 30
  retry-count: 3
  allow-mock-location: false
  allow-outside-check-in: false
  allow-outside-check-out: false
```

### 7.4 상태값

승인 상태:

- `PENDING`
- `APPROVED`
- `REJECTED`
- `CANCELED`

근태 상태:

- `BEFORE_WORK`
- `WORKING`
- `BREAK`
- `FINISHED`
- `LATE`
- `EARLY_LEAVE`
- `ABSENT`
- `LEAVE`
- `OUTSIDE_WORK`
- `BUSINESS_TRIP`
- `REMOTE_WORK`

상태 문자열을 여러 코드 위치에 직접 작성하지 말고 Enum으로 관리한다.

---

## 8. API 규칙

- 기본 경로: `/api/v1`
- REST 리소스 중심 URL 사용
- 요청 DTO와 응답 DTO를 Entity와 분리
- Bean Validation 사용
- 날짜·시간은 ISO-8601
- 페이지 조회는 `page`, `size`, `sort`를 사용
- 공통 응답 구조를 일관되게 유지

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "정상 처리되었습니다.",
  "data": {},
  "timestamp": "2026-07-21T09:00:00+09:00"
}
```

오류 코드는 `LOCATION_PERMISSION_DENIED`, `LOW_LOCATION_ACCURACY`, `OUTSIDE_GEOFENCE`, `ALREADY_CHECKED_IN`처럼 기계가 해석할 수 있는 고정 코드로 제공한다.

---

## 9. 데이터 규칙

- 테이블명과 컬럼명은 `snake_case`
- PK는 `BIGINT`
- 금액이 없으므로 위치 좌표는 `DECIMAL(10,7)` 또는 PostgreSQL 공간 타입을 사용
- 변경 가능한 주요 Entity에는 낙관적 잠금 `version` 적용
- `attendance_records`에는 `(user_id, work_date)` 유니크 제약 적용
- 원본 이벤트는 `attendance_events`에 불변 기록으로 보존
- 삭제는 업무 데이터 특성상 기본적으로 소프트 삭제 또는 비활성화 처리
- 스키마 변경은 Flyway 마이그레이션으로만 수행

---

## 10. 보안·개인정보 규칙

- 위치는 출퇴근 시점에만 수집하는 것을 기본으로 한다.
- 백그라운드 상시 추적은 구현하지 않는다.
- JWT 비밀키, DB 비밀번호, 지도 API 키는 환경변수로 관리한다.
- 직원은 본인 데이터만 접근한다.
- 관리자는 허용된 조직 범위만 접근한다.
- 위치 원본 좌표는 최소 권한으로 제한한다.
- 관리자 수정, 승인, 근무지 변경, 권한 변경은 감사 로그에 기록한다.
- 로그에 토큰, 비밀번호, 전체 위치 요청 본문을 남기지 않는다.

---

## 11. 테스트 완료 기준

각 기능은 최소한 다음을 포함한다.

- 정상 케이스
- 권한 오류
- 입력값 오류
- 상태 충돌
- 중복 요청
- 트랜잭션 롤백
- GPS 경계값
- 시간 경계값

백엔드는 단위 테스트와 통합 테스트를 구분한다. DB 의존 통합 테스트는 Testcontainers를 우선 사용한다.

작업 완료 전 다음 명령을 실제 실행한다.

```bash
# Backend
./gradlew clean test
./gradlew bootJar

# Admin Web
npm run lint
npm run test
npm run build

# Flutter
flutter analyze
flutter test
```

프로젝트에 없는 명령은 실제 `package.json`, `build.gradle`, `pubspec.yaml`에 맞춰 조정한다.

---

## 12. 작업 결과 보고 형식

작업 완료 시 다음 순서로 간결하게 보고한다.

1. 구현한 내용
2. 변경 파일
3. 실행한 테스트와 결과
4. 남은 위험 또는 미구현 사항
5. 다음 권장 작업

---

## 13. 금지사항

- 테스트를 통과시키기 위해 테스트를 삭제하거나 무력화하지 않는다.
- 운영 코드에 임시 관리자 계정을 하드코딩하지 않는다.
- 클라이언트가 전송한 `withinGeofence` 값을 신뢰하지 않는다.
- 단말기 시간을 근태 최종시간으로 그대로 사용하지 않는다.
- Entity를 API 응답으로 직접 반환하지 않는다.
- Controller에 핵심 비즈니스 로직을 작성하지 않는다.
- 마이그레이션 없이 운영 DB 스키마를 자동 변경하지 않는다.
- 승인 완료 데이터를 이력 없이 덮어쓰지 않는다.
