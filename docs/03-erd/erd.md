# ERD (Entity Relationship Diagram)

> 상세 필드 및 테이블 구조를 정의합니다.
> 모든 상태값(status)은 Enum + 한글 설명 방식으로 관리합니다.
> 공통 필드: 모든 테이블에 `created_at`, `updated_at` 포함 (JPA Auditing 적용)

---

## 공통 Audit 필드

### Audit 컬럼 (전체 테이블 공통)

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| created_at | DATETIME | 생성 일시 |
| updated_at | DATETIME | 수정 일시 |
| created_by | BIGINT | 생성자 ID (member.id 또는 admin.id) |
| created_by_type | VARCHAR(20) | 생성자 유형 (MEMBER / ADMIN / SYSTEM) |
| updated_by | BIGINT | 수정자 ID |
| updated_by_type | VARCHAR(20) | 수정자 유형 (MEMBER / ADMIN / SYSTEM) |

> 이력성 테이블(append-only)은 `created_at`, `created_by`, `created_by_type` 3개만 적용
> 해당 테이블: `order_history`, `inventory_log`, `product_history`, `outbound_api_log`, `inbound_api_log`

---

## 1차 오픈 ERD

### 관계 요약
```
Member 1 ─── 1 MemberProfile
Member 1 ─── N Wishlist
Member 1 ─── 1 Cart ─── N CartItem
Member 1 ─── N Order ─── N OrderItem
                    │
                    ├─── 1 Payment
                    ├─── 1 Delivery
                    └─── N OrderHistory

Category 1 ─── N Category (self, 계층형)
Category 1 ─── N Product

Product 1 ─── N ProductHistory
Product 1 ─── 1 Inventory ─── N InventoryLog
Product 1 ─── N Wishlist
Product 1 ─── N CartItem
Product 1 ─── N OrderItem
```

---

### category (카테고리)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 카테고리 ID |
| name | VARCHAR(50) | NOT NULL | 카테고리명 |
| parent_id | BIGINT | FK → category.id, nullable | 상위 카테고리 ID (null = 대분류) |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

> 2단계 고정 (대분류: parent_id = null / 중분류: parent_id = 대분류 ID)
> depth 컬럼 없음 — parent_id IS NULL 여부로 대/중분류 구분

---

### member (고객)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 고객 ID |
| email | VARCHAR(100) | UNIQUE, NOT NULL | 이메일 (로그인 ID) |
| password | VARCHAR(255) | NOT NULL | BCrypt 암호화된 비밀번호 |
| name | VARCHAR(50) | NOT NULL | 이름 |
| phone | VARCHAR(255) | UNIQUE, NOT NULL | 전화번호 (AES256 암호화 저장, 조회 시 마스킹) |
| status | VARCHAR(20) | NOT NULL | 회원상태 (ACTIVE / WITHDRAWN) |
| auth_status | VARCHAR(20) | NOT NULL | 인증상태 (UNVERIFIED / VERIFIED) |
| grade | VARCHAR(20) | NOT NULL | 등급 (NORMAL / VIP) |
| total_purchase_amount | BIGINT | DEFAULT 0 | 누적 구매금액 (VIP 등급 기준) |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (MEMBER / SYSTEM) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

> 스타일링 스펙 정보(height/weight 등)는 member_profile 테이블로 분리

---

### member_profile (고객 스타일링 프로필)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 프로필 ID |
| member_id | BIGINT | FK → member.id, UNIQUE, NOT NULL | 고객 ID (1:1) |
| height | INT | nullable | 키 (cm) |
| weight | INT | nullable | 몸무게 (kg) |
| top_size | VARCHAR(10) | nullable | 상의 사이즈 (S/M/L/XL 등) |
| bottom_size | VARCHAR(10) | nullable | 하의 사이즈 |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (MEMBER / SYSTEM) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

> 2차 O2O 출장 스타일링에서만 사용하는 스타일링 스펙 정보
> 회원가입 시 선택 입력 (nullable) — 2차 예약 신청 시 ReservationService에서 필수 여부 체크
> Member와 1:1 관계 — 회원가입 시 빈 레코드로 생성

---

### admin (관리자)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 관리자 ID |
| email | VARCHAR(100) | UNIQUE, NOT NULL | 이메일 (로그인 ID) |
| password | VARCHAR(255) | NOT NULL | BCrypt 암호화된 비밀번호 |
| name | VARCHAR(50) | NOT NULL | 이름 |
| role | VARCHAR(20) | NOT NULL | 권한 (SUPER_ADMIN / MANAGER / STYLIST) |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (ADMIN / SYSTEM) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

---

### product (상품)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 상품 ID |
| category_id | BIGINT | FK → category.id, NOT NULL | 카테고리 ID |
| name | VARCHAR(100) | NOT NULL | 상품명 (현재) |
| price | BIGINT | NOT NULL | 가격 (현재) |
| description | TEXT | | 상품 설명 (현재) |
| image_url | VARCHAR(500) | | 대표 이미지 (현재, S3 URL) |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (ADMIN) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

> 현재 상품 정보(name, price 등)를 직접 보유
> 변경 시 변경 전 값을 product_history에 INSERT 후 product UPDATE

---

### product_history (상품 변경 이력) — append-only

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 이력 ID |
| product_id | BIGINT | FK → product.id, NOT NULL | 상품 ID |
| name | VARCHAR(100) | NOT NULL | 변경 전 상품명 |
| price | BIGINT | NOT NULL | 변경 전 가격 |
| description | TEXT | | 변경 전 상품 설명 |
| image_url | VARCHAR(500) | | 변경 전 대표 이미지 |
| created_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (ADMIN) |

> 상품 정보 변경 시 변경 전 값을 INSERT (append-only)
> 현재 상품 정보는 product 테이블에서 직접 조회
> 관리자 분쟁 해결 용도 — "언제 가격이 얼마였는가" 운영 기준 데이터

---

### wishlist (찜)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 찜 ID |
| member_id | BIGINT | FK → member.id, NOT NULL | 고객 ID |
| product_id | BIGINT | FK → product.id, NOT NULL | 상품 ID |
| quantity | INT | nullable | 희망 수량 (2차 예약 시 활성화) |
| memo | VARCHAR(255) | nullable | 스타일리스트에게 남기는 메모 (2차 예약 시 활성화) |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (MEMBER) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

**유니크 제약:** `(member_id, product_id)` — 동일 상품 중복 찜 방지

> quantity, memo: 1차(온라인)에서는 null / 2차 예약 확정 시점에 입력

---

### cart (장바구니)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 장바구니 ID |
| member_id | BIGINT | FK → member.id, UNIQUE, NOT NULL | 고객 ID (1:1) |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (MEMBER / SYSTEM) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

---

### cart_item (장바구니 상품)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 장바구니 상품 ID |
| cart_id | BIGINT | FK → cart.id, NOT NULL | 장바구니 ID |
| product_id | BIGINT | FK → product.id, NOT NULL | 상품 ID |
| quantity | INT | NOT NULL, DEFAULT 1 | 수량 |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (MEMBER) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

**유니크 제약:** `(cart_id, product_id)` — 동일 상품 중복 방지

---

### order (주문)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 주문 ID |
| order_no | VARCHAR(30) | UNIQUE, NOT NULL | 주문번호 (예: ORD-20260513-00001) |
| member_id | BIGINT | FK → member.id, NOT NULL | 고객 ID |
| order_type | VARCHAR(20) | NOT NULL, DEFAULT 'ONLINE' | 주문 유형 — ONLINE(1차 온라인) / FIELD(2차 출장 현장) (V6 추가) |
| reservation_id | BIGINT | FK → reservation.id, nullable | 2차 현장 주문일 때만 — 출장 예약 연결 (V6 추가) |
| status | VARCHAR(30) | NOT NULL | 주문상태 Enum |
| total_price | BIGINT | NOT NULL | 총 주문금액 |
| version | BIGINT | NOT NULL, DEFAULT 0 | 낙관적 락 — 결제 vs 결제대기 만료 배치 동시 처리 방지 (V4 추가) |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (MEMBER) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

**주문 상태값:**
`PENDING` / `PAID` / `IN_PREPARATION` / `IN_DELIVERY` / `DELIVERED` / `FAILED` / `CANCELLED` / `RETURN_REQUESTED` / `RETURNED`

> 2차 FIELD 주문은 `PENDING → PAID`(끝) / `FAILED`만 사용 — 배송 없음, 반품 불가(확장 포인트)
> 결제대기 15분 만료 배치는 `order_type = 'ONLINE'`만 대상

---

### order_item (주문 상품)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 주문 상품 ID |
| order_id | BIGINT | FK → order.id, NOT NULL | 주문 ID |
| product_id | BIGINT | FK → product.id, NOT NULL | 상품 ID |
| product_name | VARCHAR(100) | NOT NULL | 상품명 스냅샷 (주문 시점) |
| price | BIGINT | NOT NULL | 가격 스냅샷 (주문 시점) |
| quantity | INT | NOT NULL | 수량 |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (MEMBER / SYSTEM) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

---

### order_history (주문 이력) — append-only

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 이력 ID |
| order_id | BIGINT | FK → order.id, NOT NULL | 주문 ID |
| status | VARCHAR(30) | NOT NULL | 변경된 주문 상태 |
| description | VARCHAR(255) | | 상태 변경 사유/설명 |
| created_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 |

---

### inventory (재고) ⭐ 핵심

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 재고 ID |
| product_id | BIGINT | FK → product.id, UNIQUE, NOT NULL | 상품 ID (1:1) |
| quantity | INT | NOT NULL, DEFAULT 0 | 재고 수량 |
| status | VARCHAR(20) | NOT NULL | 재고상태 Enum |
| version | BIGINT | NOT NULL, DEFAULT 0 | 낙관적 락 버전 (동시성 제어) |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (ADMIN / SYSTEM) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

**재고 상태값:** `AVAILABLE` / `RESERVED` / `IN_TRANSIT` / `SOLD` / `UNSELLABLE`(2차 전용, 반납 검수 불량)

> `version` 컬럼: JPA `@Version` 낙관적 락으로 동시 HOLD 중복 방지

---

### inventory_log (재고 변경 이력) — append-only

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 이력 ID |
| inventory_id | BIGINT | FK → inventory.id, NOT NULL | 재고 ID |
| from_status | VARCHAR(20) | NOT NULL | 변경 전 상태 |
| to_status | VARCHAR(20) | NOT NULL | 변경 후 상태 |
| quantity_change | INT | NOT NULL | 수량 변화량 |
| reason | VARCHAR(255) | | 변경 사유 |
| related_order_id | BIGINT | FK → order.id, nullable | 관련 주문 ID |
| created_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 |

---

### payment (결제) - Mock

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 결제 ID |
| order_id | BIGINT | FK → order.id, UNIQUE, NOT NULL | 주문 ID (1:1) |
| status | VARCHAR(30) | NOT NULL | 결제상태 Enum |
| amount | BIGINT | NOT NULL | 결제금액 |
| pg_transaction_id | VARCHAR(100) | | PG사 거래 ID (Mock) |
| fail_code | VARCHAR(50) | | 결제 실패 코드 (1차, V5) — PG 에러코드 그대로 / PG 응답 없으면 PG_CONNECTION_FAILED, PG_TIMEOUT |
| fail_message | VARCHAR(255) | | 결제 실패 메시지 (1차, V5) |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (MEMBER / SYSTEM) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

**결제 상태값:** `PENDING` / `SUCCESS` / `FAILED` / `CANCEL_REQUESTED` / `CANCELLED` / `REFUND_REQUESTED` / `REFUNDED`

---

### delivery (배송) - Mock

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 배송 ID |
| order_id | BIGINT | FK → order.id, UNIQUE, NOT NULL | 주문 ID (1:1) |
| status | VARCHAR(30) | NOT NULL | 배송상태 Enum |
| tracking_no | VARCHAR(50) | | 운송장 번호 (Mock) |
| address | VARCHAR(255) | NOT NULL | 배송지 주소 |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (SYSTEM) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

**배송 상태값:** `READY` / `SHIPPED` / `DELIVERED` / `RETURN_REQUESTED` / `RETURNED`

---

### outbound_api_log (외부 API 요청 로그) — append-only

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 로그 ID |
| api_type | VARCHAR(30) | NOT NULL | API 종류 (PG / DELIVERY / NOTIFICATION / AUTH) |
| request_body | JSON | | 요청 값 |
| response_body | JSON | | 응답 값 |
| status_code | INT | | HTTP 상태코드 |
| success | TINYINT(1) | NOT NULL | 성공 여부 |
| related_id | VARCHAR(50) | | 관련 주문번호 / 예약번호 |
| called_at | DATETIME | NOT NULL | 호출 시각 |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (SYSTEM) |

---

### inbound_api_log (외부 API 수신 로그) — append-only

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 로그 ID |
| source | VARCHAR(30) | NOT NULL | 수신 출처 (DELIVERY / STYLIST / NOTIFICATION / AUTH) |
| request_body | JSON | | 수신한 요청 값 |
| response_body | JSON | | 우리가 응답한 값 |
| status_code | INT | | HTTP 상태코드 |
| success | TINYINT(1) | NOT NULL | 처리 성공 여부 |
| related_id | VARCHAR(50) | | 관련 주문번호 / 예약번호 |
| received_at | DATETIME | NOT NULL | 수신 시각 |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (SYSTEM) |

---

## TODO

- [ ] diagrams.net ERD XML 생성 (위 테이블 구조 기반)

## 2차 오픈 테이블 (설계 확정, ERD 상세 미작성)

### stylist (스타일리스트)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 스타일리스트 ID |
| name | VARCHAR(50) | NOT NULL | 이름 |
| email | VARCHAR(100) | UNIQUE, NOT NULL | 이메일 (bo-api 로그인 ID) |
| password | VARCHAR(255) | NOT NULL | BCrypt 암호화 비밀번호 (Admin과 별도 계정 체계) |
| phone | VARCHAR(255) | NOT NULL | AES256 암호화 전화번호 |
| phone_hash | VARCHAR(64) | UNIQUE, NOT NULL | SHA-256 해시 (중복체크/검색용) |
| profile_image_url | VARCHAR(500) | | S3 이미지 URL |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |

> Stylist는 Admin과 별도 계정 체계 — bo-api에 Stylist 계정으로 로그인하여 담당 예약 조회 + 현장 구매확정 입력

### stylist_schedule (스타일리스트 가능 일정)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 일정 ID |
| stylist_id | BIGINT | FK → stylist.id, NOT NULL | 스타일리스트 ID |
| available_at | DATETIME | NOT NULL | 예약 가능 일시 |
| is_booked | TINYINT(1) | NOT NULL, DEFAULT 0 | 예약 여부 (동시성 제어 — @Version) |
| version | BIGINT | NOT NULL, DEFAULT 0 | 낙관적 락 |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |

### reservation (예약)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 예약 ID |
| member_id | BIGINT | FK → member.id, NOT NULL | 고객 ID |
| stylist_id | BIGINT | FK → stylist.id, NOT NULL | 스타일리스트 ID (즉시 확정) |
| stylist_schedule_id | BIGINT | FK → stylist_schedule.id, NOT NULL | 예약 일정 |
| status | VARCHAR(20) | NOT NULL | CONFIRMED / IN_PROGRESS / COMPLETED / CANCELLED |
| scheduled_at | DATETIME | NOT NULL | 출장 예정 일시 |
| zip_code | VARCHAR(10) | NOT NULL | 출장지 우편번호 — 서비스 지역 판단 (서울 한정, V2 추가) |
| address | VARCHAR(255) | | 출장 주소 |
| started_at | DATETIME | nullable | 출장 시작 시각 — IN_PROGRESS 전환 시 (V6, styling_session에서 이동) |
| completed_at | DATETIME | nullable | 현장 완료 시각 — COMPLETED 전환 시, 반납 지연 알림 기준 (V6) |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |

> PENDING 없음 — 고객이 가능 일정 선택 시 즉시 CONFIRMED (동기 트랜잭션)

### reservation_item (예약 상품 — 예약 ↔ 재고 연결)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 예약 상품 ID |
| reservation_id | BIGINT | FK → reservation.id, NOT NULL | 예약 ID |
| inventory_id | BIGINT | FK → inventory.id, NOT NULL | 재고 ID (옷 한 벌) |
| returned_at | DATETIME | nullable | 반납 확인 시각 — NULL이면 미반납 |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |

**유니크 제약:** `(reservation_id, inventory_id)`

> 1차 `order_item`과 같은 역할 — 예약 1건에 재고 여러 벌 연결 (V3 추가)
> 출발(RESERVED→IN_TRANSIT) / 취소(RESERVED→AVAILABLE) / 반납 확인 / 반납 지연 알림 대상을 이 테이블로 조회
> 반납 확인 대상: 예약 COMPLETED + 재고 IN_TRANSIT + `returned_at IS NULL`
> `returned_at`이 필요한 이유: 같은 재고가 반납 후 다른 예약으로 다시 나가면, 옛 예약(COMPLETED)과의 연결이 남아 있어 반납 대상으로 잘못 잡힘

### ~~styling_session (스타일링 세션)~~ — V6에서 삭제

> 예약 1건 = 세션 1건이고 칸이 `started_at`/`completed_at`뿐이라 `reservation`과 중복 → 두 칸을 `reservation`으로 옮기고 테이블 삭제
> 현장 판매 기록은 `orders`(order_type = FIELD, reservation_id)가 담당
