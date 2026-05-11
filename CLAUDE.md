# stylink 프로젝트 컨텍스트

> 이 파일은 PC/태블릿 어디서든 Claude Code가 자동으로 읽는 프로젝트 컨텍스트 파일입니다.

---

## 사용자 프로필
- Java 백엔드 개발자, 이직 준비 중
- 이 프로젝트는 **이직 포트폴리오용 사이드 프로젝트**

---

## 협업 규칙 (반드시 준수)
- 대화 중 말할 때마다 메모리/구조를 바꾸지 말 것. 대화가 모인 후 확정 시점에 한번에 반영
- 핵심 비즈니스 로직은 **사용자가 직접 구현** (포트폴리오 목적)
- Claude 역할: 방향 제시, 문서 초안, 리팩토링, 테스트 시나리오 제시
- 사용자가 막히면 코드를 바로 주지 말고 방향/힌트 먼저 제시
- 복잡성을 높이는 기능 추가 요청은 신중하게 검토 (핵심 로직이 흐려지지 않도록)

---

## 프로젝트 개요
- 프로젝트명: **stylink** (style + link, 스타일을 연결해준다는 의미)
- GitHub: https://github.com/wltmdwls1/stylink.git
- 로컬(PC): `C:\dev\stylink`
- 핵심 목표: 상품 상태(HOLD/MOVE/RELEASE) 기반 이벤트 중심 O2O 커머스 백엔드

---

## 서비스 범위 (확정)

### 1차 오픈
온라인 쇼핑몰 기본 흐름
```
상품 탐색 → 찜 → 주문 → 결제(Mock) → 배송(Mock)
```

### 2차 오픈
O2O 출장 스타일링 서비스
```
찜 → 스타일링 예약 → 재고 HOLD → 코디 배정 → 출장 → 현장 판매
취소 시: 재고 위치 기준 복구
```

### 완전 제외 (재론 금지)
- 쿠폰, 포인트, 리뷰
- 오프라인 매장 방문 스타일링 (프로젝트 완성 후 확장 포인트)

---

## 멀티모듈 구조 (확정)
```
stylink/
├── fo-api         # 고객향 REST API (FO 화면 없음, Swagger로 테스트)
├── bo-api         # 관리자 API (나중에 TypeScript BO 화면 연동)
├── batch          # 스케줄러 (HOLD 만료 자동 해제, 예약 만료 자동 취소 등)
├── domain         # 핵심 비즈니스 로직 + JPA Entity
├── common         # 공통 예외, ApiResponse, 상수, 유틸
└── external-mock  # PG/배송/알림/인증 Mock (인터페이스 기반)
```

### 모듈 의존성
```
fo-api  ──┐
bo-api  ──┼──▶  domain  ──▶  common
batch   ──┘         │
                    ▼
            external-mock
```

---

## 외부 연동 Mock 목록
| 시스템 | 처리 방식 |
|--------|----------|
| PG (Toss Payments) | 성공/실패/취소 시나리오 시뮬레이션 |
| 배송 | 상태 변경 시뮬레이션 (준비중→배송중→완료) |
| 알림 | 이벤트 기반 로그 처리 |
| 인증 | 전화번호 기반 Mock (USER → VERIFIED) |

---

## 등급 정책
| 등급 | 기준 | 혜택 |
|------|------|------|
| 일반회원 | 가입 후 기본 | - |
| VIP | 누적 구매 100만원 이상 | 출장 서비스 1회 무료 |

---

## 기술 스택
| 영역 | 기술 |
|------|------|
| Backend | Spring Boot (멀티모듈) |
| DB | MySQL |
| Security | Spring Security + JWT |
| File | AWS S3 |
| Logging | Logback + SLF4J |
| Test | JUnit / SpringBootTest |
| API 문서 | Swagger / OpenAPI |
| CI/CD | Jenkins 또는 GitHub Actions (JAR 빌드 수준) |
| BO 화면 | TypeScript (나중에) |

---

## 역할 분담

### Claude가 할 것
- 설계 문서 초안 (상태 다이어그램, ERD, 도메인 모델 등)
- 멀티모듈 프로젝트 초기 셋팅 (build.gradle, 모듈 구조)
- 공통 코드 (ApiResponse, 예외 구조, Auditing 설정)
- JPA Entity 초안 (ERD 기반, 사용자가 검토/수정)
- external-mock 구현체
- 테스트 시나리오 정의 + 엣지케이스/동시성 케이스 제시
- 비즈니스 로직 구현 후 코드리뷰 + 리팩토링 제안

### 사용자가 할 것
- 핵심 비즈니스 로직 전부
  - `InventoryService` (HOLD / TRANSFER / RELEASE 흐름)
  - `OrderService` (주문 생성 → 결제 → 재고 연동)
  - `ReservationService` (예약 → 코디 배정 → 현장 판매)
  - `PaymentService` (Mock 호출 + 결과 처리)
- Controller, DTO
- 트랜잭션 경계 결정 (`@Transactional` 위치)
- 예외 처리 전략
- 비즈니스 로직 단위 테스트

---

## 문서 작성 계획

### Phase 1 — 코딩 전 필수 (현재 진행 중)
- [ ] 상태 다이어그램 (재고/주문/예약/회원/결제/배송)
- [ ] 도메인 모델 정의서
- [ ] ERD
- [ ] JPA Entity 설계서

### Phase 2 — 코딩 중 병행
- [ ] Mock 명세서
- [ ] API 명세서 (Swagger 병행)
- [ ] 테스트 시나리오 정의서

### Phase 3 — 선택
- [ ] 시스템 아키텍처 다이어그램
- [ ] 시퀀스 다이어그램

### 문서 작성 방식
- 마크다운(.md) 파일로 작성 (`docs/` 폴더)
- Claude 초안 → 사용자 검토/수정/확정 → 다음 문서로 이동
- 의존성 순서: 상태 다이어그램 → 도메인 모델 → ERD → Entity

---

## 현재 진행 상태
- [x] 프로젝트 방향 확정
- [x] GitHub 레포 생성 (stylink, public)
- [x] 로컬 클론 완료 (C:\dev\stylink)
- [x] CLAUDE.md 생성 및 GitHub 업로드
- [ ] docs 폴더 구조 생성
- [ ] Phase 1 문서 작성 시작 ← **다음 할 일**
