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
| 물류센터 | 다중 센터 관리 | 단일 센터(서울) 가정 | TRANSFER 흐름 단순화 |
| 출장 지역 | 센터별 서비스 권역 | 서울만 (우편번호 01~08) | 센터 1곳에서 이동 가능한 범위로 제한. 1차 온라인은 택배라 전국 |
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
RESERVED로 선점 → 배송 시작 시 SOLD 전환으로 흐름이 명확하다.

**SOLD 시점을 배송 시작으로 정한 이유 (1차):**
주문 취소 불가(IN_DELIVERY 이후), 결제 취소/환불 구분(IN_DELIVERY 기준)과 같은 시점이라 세 도메인의 경계가 하나로 맞는다.
배송 전 취소는 RESERVED → AVAILABLE(`release()`) 하나로 처리되고, SOLD를 되돌리는 흐름은 반품 하나뿐이다.
- 결제 완료 시 SOLD: 배송 전 취소 때 SOLD를 되돌려야 해서 되돌리는 흐름이 반품 외에 하나 더 생김
- 배송 완료 시 SOLD: 이미 트럭에 실린 옷이 RESERVED로 남고, 취소 불가인데 판매 확정이 아닌 모순
2차 현장 판매도 고객에게 건네는 순간 SOLD라서 "옷이 고객 쪽으로 넘어가는 순간 = SOLD"로 1차/2차 설명이 통일된다.
2차 출장 서비스의 물리적 이동을 IN_TRANSIT으로 자연스럽게 표현할 수 있다.

**상태명 네이밍 근거:**
- HOLD 대신 RESERVED: "선점됨"보다 "예약됨"이 비즈니스 의도를 더 명확히 표현
- TRANSFER 대신 IN_TRANSIT: 물리적 이동 중임을 더 직관적으로 표현

---

### IN_TRANSIT이 2차 전용인 이유

1차는 Delivery 테이블이 배송 상태를 담당 → 재고는 RESERVED→SOLD로 단순화.
2차는 스타일리스트가 상품을 물리적으로 들고 이동 → Inventory에서 IN_TRANSIT으로 추적.
IN_TRANSIT 중 예약 취소 불가. 미구매 재고는 센터 도착 후 BO 반납 확인(검수) → 정상: AVAILABLE / 불량: UNSELLABLE.
가는 중/돌아오는 중은 별도 상태 없이 예약 상태(IN_PROGRESS / COMPLETED)로 구분.
반납 지연은 배치가 알림만 보냄 (실물 확인이 필요하므로 자동 상태 변경 안 함).
UNSELLABLE 이후 처리(세탁/수선/폐기), 분실 처리는 확장 포인트.

### 결제대기 만료 — 1차 전용, 주문 기준

**기준:** 재고 RESERVED가 아니라 주문 PENDING 15분 초과.
RESERVED는 결제 대기뿐 아니라 1차 결제 완료 후 배송 전, 2차 예약 확정 후 출장 전에도 쓰인다. 재고 기준으로 풀면 정상 재고까지 풀린다.
2차는 예약 시 결제가 없으므로 만료 개념 자체가 없다.

**시간:** 화면 타이머 10분(안내) / 서버 15분(판단). 화면은 강제 종료·시계 차이·API 직접 호출을 막지 못하므로 판단은 서버가 한다.

**동시 처리:** 결제와 배치가 같은 주문을 동시에 바꿀 수 있어 먼저 바꾼 쪽만 성공하게 한다.
`orders`에 `@Version` 낙관적 락을 둔다 (재고와 같은 방식). 늦게 저장하는 쪽이 예외를 받아 결제는 환불, 배치는 건너뛴다.
벌크 UPDATE는 @Version을 확인하지 않으므로 배치는 주문을 하나씩 엔티티로 변경한다.
늦게 온 PG 성공은 전액 환불. 주문을 되살리는 방식은 끝 상태 예외 전이 + 부분 재고 문제로 확장 포인트로 둔다.

---

### 결제 실패 처리 — 롤백 대신 정상 저장 (1차)

> 2차 현장 결제 실패는 별도 결정 (현장 재고는 IN_TRANSIT이라 release()로 AVAILABLE이 되면 안 됨)

**결정:** 결제 실패는 예외로 롤백하지 않고, 실패 결과를 행으로 **정상 저장(커밋)**한다. 재고는 `release()`로 직접 되돌린다.

```
[트랜잭션 1] 주문 생성 요청  → 재고 RESERVED + Order PENDING          (커밋 완료)
[트랜잭션 2] 결제 요청 pay() → PG 호출 → 실패
                              → Payment FAILED (fail_code, fail_message)
                              → Order FAILED + OrderHistory
                              → inventory.release()  RESERVED → AVAILABLE
                                 (InventoryLog reason: RELEASED_BY_ORDER_FAIL)
                              → 커밋
[별도 트랜잭션] outbound_api_log ← PG 요청/응답 원문
```

**롤백(옛 설계)을 버린 이유:**
1. **재고가 안 풀린다** — 주문 생성과 결제는 요청·트랜잭션이 따로다. 결제 트랜잭션을 롤백해도 트랜잭션 1에서 커밋된 RESERVED는 그대로라, 15분 만료 배치까지 그 옷을 아무도 못 산다. ("롤백되면 자동 복구"는 주문+결제가 한 트랜잭션이던 예전 가정)
2. **기록이 사라진다** — 카드 거절은 시스템 오류가 아니라 정상적인 업무 결과다. 남겨야 CS("왜 결제가 안 됐죠?")와 원인 분석이 된다.
3. **일관성** — 결제대기 만료도 FAILED 저장 + release()로 정했다. 같은 실패를 같은 방식으로 처리한다.

**실패 종류 — "돈이 빠졌는지 확실한가"로 나눈다:**

| 종류 | 예시 | 돈이 빠졌나 | 처리 | fail_code |
|------|------|------------|------|-----------|
| PG 거절 | 도난카드, 한도초과, 잔액부족, 비밀번호 오류 | 안 빠짐 (확실) | 바로 FAILED + release() | PG 에러코드 그대로 |
| 전송 실패 | PG 서버 연결 거부 | 안 빠짐 (확실) | 바로 FAILED + release() | `PG_CONNECTION_FAILED` |
| 응답 없음 | 타임아웃 | **모름** | 망취소 → FAILED + release() | `PG_TIMEOUT` |

- **망취소**: 결과를 모르는 거래를 PG에 "취소해 달라"고 요청해 확실히 "안 빠진" 상태로 만드는 것.
  타임아웃을 바로 실패 처리하면 PG에선 승인됐는데 주문은 FAILED, 옷은 다른 고객에게 팔리는 사고가 난다.
  A-1의 "늦게 온 PG 성공은 환불"과 같은 생각 — 애매하면 돈을 돌려주는 쪽으로.
- 망취소마저 실패하면 관리자가 확인할 수 있게 로그/알림을 남긴다.
- 확장 포인트: PG 거래 조회 API로 우리 DB와 맞춰보는 배치(대사, reconciliation).

**실패 사유를 두 군데에 남기는 이유:**
- `payment.fail_code` / `fail_message` (V5) — 정리된 사유. BO에서 주문 보고 바로 확인.
- `outbound_api_log` — PG와 주고받은 원문. 개발자 디버깅용, 사유 필드에 담기 어려운 상세 정보까지.
- `fail_code`는 Enum이 아니라 **문자열**: PG 에러코드는 수십 개이고 PG사마다 다르며 추가될 수 있다. Enum으로 막으면 모르는 코드가 올 때 저장이 실패한다. PG가 코드를 못 주는 경우(전송 실패/타임아웃)만 우리 코드를 쓴다.
- API 로그는 결제 트랜잭션이 예상 못 한 오류로 롤백돼도 남아야 하므로 별도 트랜잭션(`REQUIRES_NEW`)으로 저장한다. 장애 상황일수록 로그가 필요하다.

**재시도 없음:**
주문 1건당 결제 1건(`payment.order_id` UNIQUE). 실패하면 주문은 FAILED(끝 상태)이고, 다시 사려면 새 주문을 만든다.
실제 쇼핑몰처럼 같은 주문으로 다른 카드 재결제를 허용하면 주문당 Payment가 여러 행이 되고 실패하는 동안 재고가 계속 묶여서, 확장 포인트로 둔다.

**구현 시 결정 (사용자 담당):**
- 실패를 예외로 던지면 `@Transactional`이 FAILED 저장까지 롤백한다 → 실패를 결과값으로 받을지, 예외를 잡아서 처리할지
- PG 호출을 트랜잭션 안/밖 어디에 둘지 (안에 두면 타임아웃 동안 DB 커넥션을 잡고 있음)
- Payment 행을 PG 호출 전(PENDING)에 만들지, 결과를 받은 뒤 만들지

---

### 예약 ↔ 재고 연결 — reservation_item

재고는 1차/2차가 같이 쓰므로 상태 하나(`inventory.status`)로 통합 관리하고, 진행 단계는 1차 주문(`orders.status`) / 2차 예약(`reservation.status`)이 각자 관리한다.
둘을 잇는 연결이 1차는 `order_item`, 2차는 `reservation_item`.
연결이 없으면 "이 예약에 어떤 옷이 묶였는지" 알 수 없어 출발/취소/반납 확인 대상을 찾을 수 없다.
- `inventory.reservation_id` 칸 방식은 현재 보유자만 알고 이력이 사라지며, 1차와 구조가 달라져 제외
- `inventory_log`로 찾는 방식은 이력 테이블로 현재 상태를 찾는 것이라 느리고 실수하기 쉬워 제외

`returned_at`(반납 확인 시각)을 둔 이유: 같은 옷이 반납 후 다른 예약으로 다시 나가면, 옛 예약(COMPLETED)과의 연결이 남아 있어
"예약 COMPLETED + 재고 IN_TRANSIT" 조건에 잘못 걸린다. 행마다 반납 여부를 남겨 구분한다.

### 2차 출장을 서울로 한정한 이유

센터가 서울 1곳이라 스타일리스트가 옷을 들고 이동할 수 있는 범위가 제한된다.
1차 온라인은 택배라 전국 대상이지만, 2차는 출장지 우편번호(앞 두 자리 01~08)로 서울 여부를 판단해 예약을 받는다.
주소 문자열("서울시"/"서울특별시")은 표기가 달라 깨지기 쉬워 우편번호를 택했다. 1차 Delivery의 zip_code와도 구조가 같다.
확장 포인트: 지역 확대(예: 경기도) 시 서울 센터로 커버할지, 센터를 추가할지(재고/스타일리스트에 센터 연결, 센터 간 반납 허용 등)는 추후 결정.

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
@Version 컬럼은 애플리케이션 코드에서 직접 수정 금지 (JPA가 수정 시 자동 +1, WHERE version 비교도 자동).

**DB에서 데이터를 직접 고쳐야 할 때:**
- 서버가 켜져 있으면 **항상 `version = version + 1` 함께** 올린다.
  ```sql
  UPDATE orders SET status = 'FAILED', version = version + 1 WHERE id = 1;
  ```
  - 안 올리면 옛 version으로 읽어 둔 작업이 저장할 때 통과해서, 직접 고친 내용을 조용히 덮어씀
  - 다른 칸만 고쳐도 마찬가지 — Hibernate 기본 UPDATE는 모든 칸을 다시 씀 (`@DynamicUpdate` 미사용)
  - 올려서 생기는 손해는 진행 중이던 요청 하나 실패뿐 (의도한 결과)
- 안 올려도 되는 경우: 서버를 내린 점검 시간 / 서버가 다시 건드리지 않는 끝 상태 행(FAILED, CANCELLED 등)
- **version을 낮추는 것은 금지** — 옛 값을 들고 있던 작업이 통과해 락이 무력화됨

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
