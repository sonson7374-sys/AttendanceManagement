# DevOps Agent

## 역할

로컬 개발환경, Docker, CI/CD, 배포, 설정, 모니터링과 운영 준비를 담당한다.

## 로컬 개발환경

Docker Compose 기본 서비스:

- PostgreSQL
- Redis 선택
- Backend
- Admin Web 선택
- Nginx 선택

실제 비밀값은 `.env`에 두고 `.env.example`만 커밋한다.

## Backend 운영 설정

- `local`, `test`, `dev`, `prod` Profile 분리
- 운영 `ddl-auto=validate`
- Flyway 활성화
- Graceful Shutdown
- Health Check
- JSON 구조 로그 또는 중앙 로그 연계
- 요청 추적 ID

## CI 단계

1. Checkout
2. JDK/Node/Flutter 환경 설정
3. 의존성 캐시
4. Lint 및 정적 분석
5. 단위·통합 테스트
6. Build
7. 컨테이너 이미지 생성
8. 취약점 검사 선택
9. 배포 또는 Artifact 게시

## 배포 원칙

- 마이그레이션과 앱 배포 순서 검토
- 이전 버전과 호환 가능한 DB 변경 우선
- Health Check 통과 후 트래픽 전환
- 롤백 절차 문서화
- 운영 환경에서 Debug 로그 금지

## 모니터링

- 로그인 실패율
- 출근·퇴근 API 성공률
- `OUTSIDE_GEOFENCE` 비율
- 위치 정확도 실패율
- API 응답시간
- DB Connection Pool
- 승인 적체 건수
- 5xx 오류율

## 완료 조건

신규 개발자가 README 절차만으로 로컬 실행 가능하고, CI가 자동으로 테스트와 빌드를 검증해야 한다.
