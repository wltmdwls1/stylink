# 설계 결정 근거 (Design Decisions)

> 이 프로젝트에서 내린 설계 결정들의 근거를 기록합니다.
> "왜 이렇게 했는지"를 나중에도 명확히 설명할 수 있도록 정리합니다.

---

## 의도적으로 실무와 다르게 간 것들

| 항목 | 실무 | 이 프로젝트 | 근거 |
|------|------|------------|------|
| PG 연동 | 실제 Toss Payments API | Mock 시뮬레이션 | 외부 의존성 없이 결제 흐름 자체에 집중 |
| 배송 연동 | 실제 배송사 API | Mock 시뮬레이션 | 배송 상태 전이 흐름 집중 |
| 본인인증 | 실제 PASS/KCB API | Mock | 핵심 비즈니스 로직에 집중 |
| 알림 | 실제 FCM/SMS 발송 | Mock 로그 처리 | 알림 인프라 없이 흐름만 구현 |
| SKU | ProductVariant 테이블 필요 | 단일 상품 가정 | 아래 상세 참고 |
| 물류센터 | 다중 센터 관리 | 단일 센터 가정 | TRANSFER 흐름 단순화 |
| ORM | MyBatis 혼용 많음 | 순수 JPA | JPA 설계/활용 능숙도 어필 |

---

## 실무 수준 그대로 가져간 것들

| 항목 | 근거 |
|------|------|
| 재고 낙관적 락 (@Version) | 동시 주문 시 중복 RESERVED 방지 — 실무 핵심 패턴 |
| AES256 + BCrypt 암호화 | 개인정보보호법 기준 — 전화번호(양방향)/비밀번호(단방향) 구분 |
| SCD Type 2 (ProductHistory) | 상품 정보 변경 이력 관리 — 실무 데이터 이력 패턴 |
| 상태 기반 재고 관리 | AVAILABLE/RESERVED/IN_TRANSIT/SOLD — 단순 수량보다 명확한 흐름 |
| Audit 필드 분리 | BaseEntity/BaseLogEntity — append-only 의도 명확화 |
| API 로그 분리 | OutboundApiLog/InboundApiLog — 관심사 분리 |
| Enum + 한글 설명 | 타입 안정성 + 의미 명확화 |
| 단방향 연관관계 | 복잡도 감소, 필요 시 양방향 확장 가능 |
| ALL LAZY | N+1 문제 명시적 제어 |

---

## 주요 결정 상세

---

### SKU(사이즈/컬러) 미포함

**실무에서는:**
온라인 쇼핑몰은 ProductVariant(SKU) 테이블로 사이즈/컬러 조합별 재고를 관리하는 게 필수.

**이 프로젝트에서는:**
단일 상품 단위로 가정. 사이즈/컬러 없음.

**근거:**
이 서비스의 핵심은 2차 O2O 출장 스타일링이다.
스타일리스트가 고객의 스펙 정보(키/몸무게/사이즈)를 시스템에서 확인한 뒤,
전문성을 바탕으로 상품을 직접 선별해서 출장을 나가는 개념이다.
사이즈/컬러를 시스템이 관리하는 것보다 스타일리스트의 판단에 맡기는 방식이 이 서비스에 더 적합하다.
또한 재고 동시성 제어와 O2O 흐름이 핵심 어필 포인트이므로, SKU 구조 추가로 복잡도가 올라가는 것을 방지했다.

**확장 시:**
ProductVariant 테이블 추가로 SKU 단위 재고 관리 가능.

---

### 회원 스펙 정보 (height, weight, topSize, bottomSize)

**설계:**
회원가입 시 선택 입력 (nullable).
2차 예약 신청 시 ReservationService에서 필수 여부 체크.

**근거:**
DB 컬럼을 nullable로 두고 비즈니스 규칙은 Service 레이어에서 처리.
가입 시 강제하지 않고 2차 서비스 사용 시점에 안내하는 UX가 자연스럽다.
선호 스타일은 별도 컬럼으로 관리하지 않고, 고객의 찜 목록에서 스타일리스트가 자연스럽게 파악하는 방식으로 설계했다.
(찜 목록 = 단순 관심 저장이 아니라 스타일리스트의 고객 취향 파악 도구)

---

### 재고 상태 기반 관리 (AVAILABLE/RESERVED/IN_TRANSIT/SOLD)

**실무에서는:**
수량 감소 방식도 많이 사용.

**이 프로젝트에서는:**
상태 기반 관리.

**근거:**
단순 수량 감소는 동시 주문 시 초과 판매 위험이 있고 재고 상태 추적이 어렵다.
RESERVED로 선점 → 결제 완료 시 SOLD 전환으로 흐름이 명확하다.
2차 출장 서비스의 물리적 이동을 IN_TRANSIT으로 자연스럽게 표현할 수 있다.

**상태명 네이밍 근거:**
- HOLD 대신 RESERVED: "선점됨"보다 "예약됨"이 비즈니스 의도를 더 명확히 표현
- TRANSFER 대신 IN_TRANSIT: 물리적 이동 중임을 더 직관적으로 표현

---

### IN_TRANSIT이 2차 전용인 이유

1차는 Delivery 테이블이 배송 상태를 담당 → 재고는 RESERVED→SOLD로 단순화.
2차는 스타일리스트가 상품을 물리적으로 들고 이동 → Inventory에서 IN_TRANSIT으로 추적.
IN_TRANSIT 중 예약 취소 시 이동된 위치 기준으로 AVAILABLE 복구 처리.

---

### ProductHistory — append-only 이력 로그

**설계:**
- Product: 현재 상품 정보(name, price 등) 직접 보유
- ProductHistory: 변경 전 값을 INSERT하는 append-only 이력 로그

**변경 시 흐름:**
1. 현재 Product.name/price → ProductHistory에 INSERT (변경 전 값 보존)
2. Product UPDATE (새 값으로)

**ProductHistory 존재 이유:**
OrderItem은 고객이 결제한 시점의 진실(고객 기준).
ProductHistory는 상품 가격이 실제로 언제 변경됐는지의 운영 기준.
고객 분쟁 시("이벤트 기간에 10,000원이었는데 왜 나는 12,000원이냐") 두 데이터를 교차 확인해 진실을 가릴 수 있음.

**isCurrent 제거 근거:**
ProductHistory는 전부 변경 전 값(과거)이므로 isCurrent가 의미 없음.
현재 값은 Product에 있으므로 필터 없이 직접 조회 가능.

---

### Member + MemberProfile 분리

**설계:**
- Member: 인증/계정/비즈니스 정보 (email, password, grade, totalPurchaseAmount 등)
- MemberProfile: 스타일링 스펙 정보 (height, weight, topSize, bottomSize)

**근거:**
Member는 로그인, 주문, 등급 관리 등 전반에 걸쳐 항상 함께 조회되는 데이터.
스타일링 스펙 정보는 2차 O2O 출장 서비스에서만 필요하고, 성격이 명확히 다름.
같은 Member 테이블에 두면 1차 서비스에서도 불필요한 컬럼이 항상 로드됨.
"성격이 다른 데이터만 최소 분리"하는 원칙으로 MemberProfile을 별도 테이블로 분리.

**모놀리식에서 나머지를 합친 이유:**
grade, totalPurchaseAmount는 주문할 때마다 같이 쓰이는 데이터라 JOIN 없이 바로 조회하는 게 유리.
MSA 전환 시에는 Auth 서비스 / Customer 서비스 / Profile 서비스로 자연스럽게 분리 가능.

---

### 순수 JPA 선택

MyBatis 실무 경험은 있으나, 포트폴리오에서 JPA 설계/활용 능숙도를 증명하기 위해 순수 JPA로 구성.
복잡한 조회는 JPQL fetch join으로 N+1 명시적 제어.
실무에서 MyBatis와 혼용하는 경우가 많지만, 포트폴리오에서 혼용하면 JPA를 제대로 못 쓴다는 인상을 줄 수 있다.

---

### 서비스 아키텍처 — OrderService 오케스트레이터 패턴

**설계:**
OrderService가 주문 흐름 전체를 제어하는 오케스트레이터 역할.
InventoryService, PaymentService, DeliveryService는 각자의 책임만 처리하고 OrderService가 흐름을 조율.

**흐름:**
```
OrderService
  → InventoryService.hold()       // 재고 RESERVED
  → PaymentService.pay()          // 결제
  → DeliveryService.prepare()     // 배송 준비
  → OrderHistory 기록
```

**근거:**
주문 흐름(재고 → 결제 → 배송)의 전체 맥락이 OrderService에 집중되어 있어 흐름 파악이 용이.
실무 모놀리식 구조에서는 오케스트레이터 패턴이 일반적.
MSA 전환 시 오케스트레이터를 별도 서비스로 분리하거나 Saga 패턴으로 확장 가능.

---

### 낙관적 락 선택 (@Version)

비관적 락(SELECT FOR UPDATE)은 DB 레벨 잠금으로 성능 저하가 있다.
재고 HOLD는 충돌이 드문 경우라 낙관적 락이 적합하다.
충돌 시 OptimisticLockException → 재시도 또는 실패 처리.
@Version 컬럼은 임의 수정 절대 금지. DB에서 직접 수정 시 version 컬럼 제외.

---

### 전화번호 AES256, 비밀번호 BCrypt 구분

전화번호: 본인인증/CS 처리 시 복호화가 필요 → 양방향 암호화(AES256).
비밀번호: 복호화 필요 없이 검증만 하면 됨 → 단방향 해시(BCrypt).
용도에 따라 암호화 방식을 구분하는 것이 올바른 설계.

---

### API 로그 두 테이블 분리 (Outbound/Inbound)

나가는 요청(우리 → 외부)과 들어오는 요청(외부 → 우리)은 관심사가 다르다.
분리하면 PG 결제 요청 실패 로그와 배송 콜백 수신 로그를 독립적으로 관리 가능.
트래픽 증가 시 소스별 테이블 추가 분리도 용이.

---

### BaseEntity / BaseLogEntity 분리

이력성 테이블(OrderHistory, InventoryLog 등)은 append-only 구조.
수정이 없는 테이블에 updated_by 같은 수정자 컬럼이 있으면 불필요하고 의도가 불명확.
BaseLogEntity를 분리해서 append-only 의도를 명확히 표현.

---

### 스타일리스트 계정 — Admin 테이블 분리

**설계:**
Stylist는 Admin 테이블에 STYLIST 역할을 추가하는 방식이 아닌, Stylist 테이블 자체에 email + password를 두어 독립 계정 체계로 관리.

**근거:**
Admin(SUPER_ADMIN/MANAGER)은 시스템 운영자 개념이고, Stylist는 현장 서비스 제공자 개념으로 도메인 성격이 다르다.
Admin 테이블에 STYLIST 역할을 추가하면 Admin과 Stylist가 같은 계정 테이블에 섞여 관리가 복잡해진다.
Stylist 테이블에 계정 정보를 두면 도메인 경계가 명확하고, 프로필 정보(포트폴리오, 전화번호 등)와 계정 정보가 한 곳에 응집된다.

**인증 구조:**
Admin 로그인 구현 패턴을 그대로 재사용. JwtProvider는 공통(common)에 있으므로 bo-api의 Stylist 로그인 엔드포인트만 추가하면 된다.
Security 설정에서 Stylist 접근 가능 엔드포인트(현장 세션 조회, 구매확정)만 별도 권한으로 제한.

**확장 시:**
실제 PDA 앱을 만들 경우 Stylist 계정 API를 그대로 연동 가능.
