# 상태 다이어그램 (State Diagram)

> 각 도메인의 상태 전이 흐름을 정의합니다.
> 필드 정의는 ERD 문서에서 다룹니다.

---

## 공통 정책

### 네이밍 규칙
- 결과 상태 → 단어 하나 : `PAID`, `DELIVERED`, `SOLD`, `CONFIRMED`
- 과정 상태 → 언더스코어 최대 1개 : `IN_PROGRESS`, `IN_PREPARATION`, `RETURN_REQUESTED`
- 도메인별 독립 네이밍 적용 (억지로 통일하지 않음)
- 모든 상태값은 **Enum + 한글 설명** 방식으로 관리
  ```java
  public enum OrderStatus {
      PENDING("결제대기"),
      PAID("결제완료"),
      ...
      private final String description;
  }
  ```

### 취소 제한 정책
| 도메인             | 취소 불가 기준 | 이후 처리 |
|-----------------|--------------|----------|
| 주문(Order)       | `IN_DELIVERY` 이후 | 반품만 가능 |
| 예약(Reservation) | `IN_PROGRESS` 이후 | 취소 불가 |
| 결제(Payment)     | `IN_DELIVERY` 이전 → 취소 / 이후 → 환불 | CANCEL / REFUND 구분 |

### 기타
- 물류센터는 단일 센터(서울)로 가정
  - 1차(온라인): 택배 배송이므로 전국 대상
  - 2차(출장): 스타일리스트가 센터에서 직접 이동하므로 **서울만 예약 가능** — 출장지 우편번호 앞 두 자리 01~08로 판단
  - 확장 포인트: 서비스 지역 확대(예: 경기도) 시 센터 추가 여부, 센터 간 반납 허용 등은 추후 결정
- 결제이탈(ABANDONED) 상태 미사용 → 결제실패(FAILED)로만 처리
- **IN_DELIVERY** = 1차 온라인 주문의 배송 시작 (Order/Delivery 상태)
- **IN_TRANSIT** = 2차 출장 서비스의 스타일리스트 재고 이동 (Inventory 상태)

---

## 1. 재고 (Inventory) ⭐ 핵심

> 재고 상태는 1차/2차 공통으로 관리.
> **IN_TRANSIT은 2차 전용** — 1차는 Delivery 테이블이 배송 추적을 담당하므로 재고는 RESERVED → SOLD로 단순화.

### 1차 (온라인 주문)
```
            주문발생                 배송시작            반품완료
AVAILABLE ──────────► RESERVED ──────────────► SOLD ──────────► AVAILABLE
    ▲                     │
    │             취소 / 만료 / 결제실패
    └─────────────────────┘
```

**SOLD 시점 = 배송 시작(IN_DELIVERY)** — 1차
- 주문 취소 경계 / 결제 CANCEL·REFUND 경계 / 재고 SOLD 경계가 모두 배송 시작으로 일치
  - 배송 시작 전: 주문 취소 가능 → 결제 CANCEL → 재고 RESERVED → AVAILABLE (`release()`)
  - 배송 시작 후: 반품만 가능 → 결제 REFUND → 재고 SOLD → AVAILABLE (`restore()`)
- 결제 전/후 RESERVED 구분은 재고 상태가 아니라 주문 상태로 판단 (PENDING이면 결제 전, 그 외는 결제 완료)

### 2차 (O2O 출장 서비스)
```
            예약확정              스타일리스트 이동         현장 결제 성공
AVAILABLE ──────────► RESERVED ────────────────► IN_TRANSIT ──────────► SOLD   (현장 판매 반품 불가 — 확장 포인트)
    ▲                     │                           │
    │                    취소           미구매 / 현장 결제 실패 (COMPLETED 후 센터 복귀)
    └─────────────────────┘                           │
                                                 BO 반납 확인 (검수)
                                                ┌─────┴─────┐
                                              정상          불량
                                                ▼            ▼
                                           AVAILABLE    UNSELLABLE
```

| 상태           | 한글명 | 설명 |
|--------------|--------|------|
| `AVAILABLE`  | 판매가능 | 주문/예약 가능한 정상 재고 |
| `RESERVED`   | 예약됨 | 주문 또는 예약으로 선점된 재고 (1차/2차 공통) |
| `IN_TRANSIT` | 이동중 | 스타일리스트 출장을 위해 이동 중인 재고 **(2차 전용)** |
| `SOLD`       | 판매완료 | 온라인 주문 또는 현장 판매로 최종 판매 완료 |
| `UNSELLABLE` | 판매불가 | 출장 반납 검수에서 불량 판정된 재고 **(2차 전용)** |

**정책**
- 결제대기 만료 (배치) — **1차 전용**
  - 대상: **주문 PENDING 15분 초과** (재고 RESERVED 기준이 아님 — 결제 완료 후 배송 전, 2차 출장 전 RESERVED까지 풀리는 문제 방지)
  - 처리: 주문 FAILED + 재고 RESERVED → AVAILABLE (reason: RELEASED_BY_BATCH_EXPIRED)
  - 2차는 예약 시 결제가 없으므로 만료 없음 — RESERVED는 출장일까지 유지, 풀리는 건 예약 취소 시뿐
- IN_TRANSIT 중 예약 취소 불가 (예약 IN_PROGRESS 이후 취소 불가 정책과 동일) — 2차 전용
- 예약 COMPLETED 시 미구매 재고 → IN_TRANSIT 유지 (스타일리스트가 센터로 복귀 중) — 2차 전용
  - 가는 중/돌아오는 중 구분: 별도 상태 없이 예약 상태로 판단 (IN_PROGRESS = 가는 중, COMPLETED = 돌아오는 중)
  - 센터 도착 후 BO에서 직원이 직접 보고 **반납 확인(검수)** → 정상: AVAILABLE / 불량: UNSELLABLE
  - 반납 확인 대상: 예약 COMPLETED + 재고 IN_TRANSIT + `reservation_item.returned_at` 비어 있음 → 확인 시 `returned_at` 기록
  - 반납 확인 전까지 판매가능 재고에 포함되지 않음 → 차에 있는 옷이 1차 온라인에서 팔리는 문제 방지
  - 배치: COMPLETED 후 일정 시간(예: 24시간)이 지나도 IN_TRANSIT인 재고 → 관리자 알림(Mock). 상태 자동 변경 안 함 (실물 확인 필요)
- **확장 포인트** (현재 범위 밖): UNSELLABLE 이후 처리(세탁/수선/폐기), 미반납 재고 분실 처리 — 2차 전용
- 동시 주문 발생 시 → 한 건만 RESERVED 성공, 나머지는 실패 처리 (낙관적 락)
- **2차 현장 판매** — 2차 전용
  - 현장 재고는 IN_TRANSIT → 1차 `reserve()`/`sell()`(AVAILABLE/RESERVED 기준) 대신 현장 전용 메서드 사용
  - 현장 주문 생성 시 재고를 새로 잡지 않음 (이미 이 예약에 묶여 차에 실린 옷) — 상태 IN_TRANSIT 그대로
  - 현장 결제 성공 → IN_TRANSIT → SOLD (reason: `SOLD_BY_FIELD_SALE`)
  - 현장 결제 실패 → **재고 그대로 IN_TRANSIT** (`release()` 금지 — AVAILABLE이 되면 차에 있는 옷이 온라인에서 팔림) → 미구매와 같이 센터 반납 검수
- 반품 완료 시 → SOLD → AVAILABLE 복구 — **1차 전용** (2차 현장 판매는 반품 불가, 확장 포인트)

---

## 2. 주문 (Order)

> 1차 온라인 쇼핑몰 중심

```
           결제요청           준비완료                       출고                  배송완료
PENDING ──────────► PAID ─────────────► IN_PREPARATION ────────► IN_DELIVERY ──────────► DELIVERED
   │                  │                      │                                               │
결제실패              취소                    취소                                            반품요청
   ▼                  ▼                      ▼                                               ▼
 FAILED           CANCELLED              CANCELLED                                    RETURN_REQUESTED
                                                                                             │
                                                                                           반품완료
                                                                                             ▼
                                                                                          RETURNED
```

| 상태                 | 한글명 | 설명 |
|--------------------|--------|------|
| `PENDING`          | 결제대기 | 주문 생성 완료, 결제 대기 중 |
| `PAID`             | 결제완료 | 결제 완료 |
| `IN_PREPARATION`   | 상품준비중 | 상품 준비 중 |
| `IN_DELIVERY`      | 배송중 | 배송 시작 → 이후 취소 불가 |
| `DELIVERED`        | 배송완료 | 배송 완료 |
| `FAILED`           | 결제실패 | 결제 실패 |
| `CANCELLED`        | 주문취소 | 주문 취소 (IN_DELIVERY 이전만 가능) |
| `RETURN_REQUESTED` | 반품요청 | 배송 완료 후 반품 요청 |
| `RETURNED`         | 반품완료 | 반품 완료 → 재고 AVAILABLE 복구 + 환불 처리 연계 |

**정책**
- PENDING → 결제 실패 시 주문 FAILED 저장 + 재고 RESERVED 해제(release → AVAILABLE) — 롤백이 아니라 정상 커밋 (결제 정책 참고)
- **결제 시간 제한**: 화면 타이머 10분 (끝나면 결제창 닫힘 — 안내용) / 서버 만료 기준 15분 (판단 기준)
  - 서버에 5분 여유를 두는 이유: 9분 59초에 누른 결제가 PG 응답을 기다리는 동안 만료되지 않도록
  - 강제 종료/네트워크 끊김 등으로 결제를 못 한 주문도 서버 배치가 15분 후 FAILED 처리 + 재고 복구
  - 결제는 주문이 PENDING일 때만 진행 — 만료(FAILED)된 주문의 결제 요청은 거절
- **결제 vs 만료 배치 동시 처리**: 주문 PENDING → (PAID 또는 FAILED) 전환은 먼저 바꾼 쪽만 성공
  - PG 결제는 성공했는데 주문이 이미 FAILED(만료) → 주문을 되살리지 않고 **PG 취소(전액 환불) + 안내**
  - 확장 포인트: 재고가 남아 있으면 주문을 되살리는 방식 (FAILED→PAID 예외 전이, 부분 재고 처리 문제로 제외)
- FAILED는 끝 상태 — 다시 사려면 새 주문
- **주문 유형** `order_type` (V6): `ONLINE`(온라인, 1차) / `FIELD`(현장, 2차)
  - 결제대기 15분 만료 배치는 **ONLINE만** 대상 (현장 주문은 고객 앞에서 바로 결제)
  - FIELD 주문은 `reservation_id`로 어느 출장 예약에서 나왔는지 연결 (ONLINE은 비어 있음)
- **2차 현장 주문(FIELD) 흐름**: `PENDING(결제대기) → PAID(결제완료)` 에서 끝 / 실패 시 `FAILED(결제실패)`
  - 배송 없음 → IN_PREPARATION / IN_DELIVERY / DELIVERED 안 거침
  - 현장 판매 반품 불가 → RETURN_REQUESTED / RETURNED 없음 (확장 포인트: 결제 후 7일 이내 반품 등)
  - 결제 실패 후 다시 사려면 현장 주문 새로 생성 (주문 1건 = 결제 1건 유지)
- 아래 취소/배송/반품 규칙은 **1차(ONLINE) 기준**
- PAID, IN_PREPARATION 상태에서만 취소 가능
- **IN_DELIVERY 이후 취소 불가** → DELIVERED 후 반품 요청만 가능
- 반품 완료 시 → 재고 AVAILABLE 복구 + 환불(REFUND) 처리

---

## 3. 예약 (Reservation)

> 2차 O2O 출장 스타일링 서비스
> 고객이 가능 일정을 선택하면 즉시 자동 확정 (동기 트랜잭션, PENDING 없음)

```
                출장시작                       완료
CONFIRMED ──────────────────► IN_PROGRESS ──────────► COMPLETED
    │                              │
   취소                         (취소 불가)
    ▼
CANCELLED
```

| 상태            | 한글명 | 설명 |
|---------------|--------|------|
| `CONFIRMED`   | 예약확정 | 고객이 가능 일정 선택 시 즉시 자동 확정 — StylistSchedule BOOKED + 재고 RESERVED 처리 |
| `IN_PROGRESS` | 진행중 | 스타일리스트 출장 시작 → 재고 IN_TRANSIT 처리, 이후 취소 불가 |
| `COMPLETED`   | 완료 | 현장 스타일링 종료 (`completed_at` 기록) — 옷별로 구매/미구매가 갈림 |
| `CANCELLED`   | 취소 | 예약 취소 (IN_PROGRESS 이전만 가능) |

**정책**
- 예약 생성 = 즉시 CONFIRMED (동기 트랜잭션, PENDING 없음)
- 고객이 선택한 StylistSchedule 슬롯 → is_booked = true 처리 (동시성 제어 필수 — 중복 예약 방지)
- CONFIRMED 시점 → 찜 목록 기반 재고 RESERVED 처리 + `reservation_item`에 예약 ↔ 재고 연결 저장
  - 이후 출발/취소/반납 확인은 `reservation_item`으로 대상 재고를 찾음
- IN_PROGRESS 시점 → 재고 IN_TRANSIT 처리 (스타일리스트 출발)
- **IN_PROGRESS 이후 취소 불가** (현장 진행 중)
- 관리자(MANAGER)가 부득이한 사유로 취소 시 → CONFIRMED → CANCELLED + 알림(Mock)
- 시각 기록 (V6, 옛 `styling_session` 테이블에서 이동): IN_PROGRESS 전환 시 `started_at`, COMPLETED 전환 시 `completed_at`
  - `completed_at` = 반납 지연 알림 배치의 "완료 후 24시간" 기준
- COMPLETED = 현장 스타일링 종료를 의미 (모든 상품 판매 완료가 아님)
  - 현장에서 옷이 갈리는 3가지 경우
    | 경우 | 뜻 | 주문 | 결제 | 재고 |
    |------|----|------|------|------|
    | 구매 | 사기로 함 + 결제 성공 | FIELD 주문 PAID | SUCCESS | IN_TRANSIT → SOLD |
    | 결제 실패 | 사려 했는데 카드가 안 됨 | FIELD 주문 FAILED | FAILED + 사유 | IN_TRANSIT 유지 |
    | 미구매 | 입어봤는데 안 삼 | **만들지 않음** | 없음 | IN_TRANSIT 유지 |
  - 결제 실패·미구매 → 센터 도착 후 BO 반납 확인(검수) → 정상: AVAILABLE / 불량: UNSELLABLE
  - 미구매를 결제 실패로 기록하지 않음 — 가짜 주문/결제가 생기고 결제 실패 통계가 오염됨
  - 미구매 따로 기록 안 함: "이 예약의 `reservation_item`에 있는데 현장 주문(PAID)에 없는 옷" = 미구매 (미구매 사유 통계는 확장 포인트)
- 취소 시 재고 복구
  - CONFIRMED 취소 → RESERVED 해제 → AVAILABLE
  - IN_TRANSIT 중 관리자 취소 불가 (IN_PROGRESS 이후 취소 불가 정책 동일)

---

## 4. 회원 (Member)

> 세 가지 속성으로 독립 관리

### 4-1. 회원 상태 (status)
```
ACTIVE ──탈퇴──► WITHDRAWN
```

| 상태          | 한글명 | 설명 |
|-------------|--------|------|
| `ACTIVE`    | 정상 | 정상 이용 가능 |
| `WITHDRAWN` | 탈퇴 | 탈퇴 처리 |

### 4-2. 인증 상태 (authStatus)
```
UNVERIFIED ──본인인증(Mock)──► VERIFIED
```

| 상태           | 한글명 | 설명 |
|--------------|--------|------|
| `UNVERIFIED` | 인증대기 | 가입 완료, 본인인증 미완료 |
| `VERIFIED`   | 인증완료 | 전화번호 기반 본인인증 완료 (Mock 처리) |

### 4-3. 회원 등급 (grade)
```
NORMAL ──누적구매 100만원 이상──► VIP
```

| 상태         | 한글명 | 설명 |
|------------|--------|------|
| `NORMAL`   | 일반회원 | 기본 등급 |
| `VIP`      | VIP | 누적 구매 100만원 이상 달성 |

**정책**
- 본인인증은 전화번호 기반 Mock 처리
- VIP 등급은 주문 완료 시점 체크 또는 배치 처리
- 탈퇴 시 개인정보 처리 정책 별도 정의 필요
- UNVERIFIED 상태에서도 로그인은 가능하나, 주문/예약 등 핵심 기능 제한

---

## 5. 결제 (Payment)

> Mock 기반 처리 / 1차/2차 공통
> - **1차**: 고객이 장바구니 → 주문 → 결제 진행
> - **2차**: 스타일리스트가 bo-api로 상품별 구매 확정 후 Payment 프로세스 트리거 (PDA 대체)

```
PENDING ──결제요청──► SUCCESS
   │
  실패
   ▼
 FAILED

[IN_DELIVERY 이전 - 취소]
SUCCESS ──취소요청──► CANCEL_REQUESTED ──취소완료──► CANCELLED

[IN_DELIVERY 이후 - 환불]
SUCCESS ──환불요청──► REFUND_REQUESTED ──환불완료──► REFUNDED
```

| 상태                 | 한글명 | 설명 |
|--------------------|--------|------|
| `PENDING`          | 결제대기 | 결제 요청 중 |
| `SUCCESS`          | 결제완료 | 결제 승인 완료 |
| `FAILED`           | 결제실패 | 결제 실패 → 주문 FAILED + 재고 RESERVED 해제 |
| `CANCEL_REQUESTED` | 취소요청 | 취소 요청 중 (IN_DELIVERY 이전) |
| `CANCELLED`        | 취소완료 | 결제 취소 완료 |
| `REFUND_REQUESTED` | 환불요청 | 환불 요청 중 (IN_DELIVERY 이후 반품) |
| `REFUNDED`         | 환불완료 | 환불 완료 |

**정책 (Mock 기준)**
- **IN_DELIVERY 이전** → 취소(CANCEL) 처리
- **IN_DELIVERY 이후** → 환불(REFUND) 처리 (취소와 명확히 구분)
- **1차 결제 실패** → 예외 롤백이 아니라 **실패 결과를 정상 저장(커밋)**
  - Payment FAILED + `fail_code`/`fail_message` 저장 → Order FAILED → 재고 `release()` (RESERVED→AVAILABLE, reason: `RELEASED_BY_ORDER_FAIL`)
  - 실패 종류별 처리
    | 종류 | 예시 | 돈이 빠졌나 | 처리 |
    |------|------|------------|------|
    | PG 거절 | 도난카드, 한도초과, 잔액부족, 비밀번호 오류 | 안 빠짐 | 바로 FAILED (fail_code = PG 에러코드) |
    | 전송 실패 | PG 서버 연결 안 됨 | 안 빠짐 | 바로 FAILED (fail_code = `PG_CONNECTION_FAILED`) |
    | 응답 없음 (타임아웃) | 요청은 갔는데 답이 없음 | **모름** | **망취소**(PG 취소 요청) 후 FAILED (fail_code = `PG_TIMEOUT`) |
  - PG 요청/응답 원문은 `outbound_api_log`에 저장 (결제 트랜잭션이 롤백돼도 남아야 함)
  - 재시도 없음 — 주문 1건당 결제 1건 (`payment.order_id` UNIQUE), 다시 사려면 새 주문
  - 확장 포인트: 같은 주문 재결제(다른 카드), 망취소 실패 시 PG 거래 조회 배치(대사)
- 1차 결제 시간 초과로 주문이 이미 FAILED인데 PG 성공 응답이 오면 → PG 취소(전액 환불) — 주문 정책 참고
- **2차 현장 결제**: 스타일리스트가 bo-api로 구매 상품 확정 → FIELD 주문 생성 → 동일 Payment 흐름 적용
  - 결제 성공/실패 저장 방식은 1차와 같음 (FAILED + fail_code 정상 저장)
  - **차이: 실패해도 재고 `release()` 안 함** — IN_TRANSIT 유지 → 센터 반납 검수
  - 현장 결제 후 취소/환불(반품) 없음 — 확장 포인트

---

## 6. 배송 (Delivery)

> Mock 기반 처리 / 1차(온라인) 중심

```
READY ──배송시작──► IN_DELIVERY ──완료──► DELIVERED
                                           │
                                         반품요청
                                           ▼
                                   RETURN_REQUESTED ──반품완료──► RETURNED
```

| 상태                  | 한글명 | 설명 |
|---------------------|--------|------|
| `READY`             | 배송준비 | 배송 준비 중 |
| `IN_DELIVERY`       | 배송중 | 배송 시작 |
| `DELIVERED`         | 배송완료 | 배송 완료 |
| `RETURN_REQUESTED`  | 반품요청 | 반품 요청 |
| `RETURNED`          | 반품완료 | 반품 완료 → Order RETURNED + 재고 AVAILABLE 복구 + 환불 처리 연계 |

**정책 (Mock 기준)**
- 배송 상태는 Mock으로 순차 변경 시뮬레이션
- RETURNED 시 → Order 상태 변경 + 재고 복구 + 환불 처리 연계

---

## 상태 전이 요약

| 도메인     | 핵심 상태 흐름 | 취소 제한 | 오픈 단계 |
|---------|-------------|----------|----------|
| 재고 (1차) | AVAILABLE → RESERVED → SOLD | 반품 시 AVAILABLE 복구 | 1차 |
| 재고 (2차) | AVAILABLE → RESERVED → IN_TRANSIT → SOLD (미구매: 반납 확인 → AVAILABLE / UNSELLABLE) | IN_TRANSIT 중 취소 불가, 반품 시 AVAILABLE 복구 | 2차 |
| 주문      | PENDING → PAID → IN_PREPARATION → IN_DELIVERY → DELIVERED | IN_DELIVERY 이후 취소 불가 | 1차 |
| 예약      | CONFIRMED → IN_PROGRESS → COMPLETED | IN_PROGRESS 이후 취소 불가 | 2차 |
| 회원      | status / authStatus / grade 독립 관리 | - | 1차/2차 공통 |
| 결제      | PENDING → SUCCESS → CANCELLED / REFUNDED | IN_DELIVERY 기준 취소/환불 구분 | 1차/2차 공통 (Mock) |
| 배송      | READY → IN_DELIVERY → DELIVERED → RETURNED | - | 1차 (Mock) |
