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
> 해당 테이블: `order_history`, `inventory_log`, `outbound_api_log`, `inbound_api_log`

---

## 1차 오픈 ERD

### 관계 요약
```
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

---

### admin (관리자)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 관리자 ID |
| email | VARCHAR(100) | UNIQUE, NOT NULL | 이메일 (로그인 ID) |
| password | VARCHAR(255) | NOT NULL | BCrypt 암호화된 비밀번호 |
| name | VARCHAR(50) | NOT NULL | 이름 |
| role | VARCHAR(20) | NOT NULL | 권한 (SUPER_ADMIN / MANAGER) |
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
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (ADMIN) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

> 가격, 상품명 등 변경 가능한 정보는 product_history에서 관리

---

### product_history (상품 이력)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 이력 ID |
| product_id | BIGINT | FK → product.id, NOT NULL | 상품 ID |
| name | VARCHAR(100) | NOT NULL | 상품명 |
| price | BIGINT | NOT NULL | 가격 |
| description | TEXT | | 상품 설명 |
| image_url | VARCHAR(500) | | 대표 이미지 (S3 URL) |
| sale_start_date | DATE | NOT NULL | 판매 시작일 |
| sale_end_date | DATE | NOT NULL | 판매 종료일 (현재: 9999-12-31) |
| is_current | TINYINT(1) | NOT NULL, DEFAULT 1 | 현재 유효 여부 (1=현재, 0=이력) |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (ADMIN) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

**인덱스:** `(product_id, is_current)` — 현재 유효 상품 조회 최적화

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
| status | VARCHAR(30) | NOT NULL | 주문상태 Enum |
| total_price | BIGINT | NOT NULL | 총 주문금액 |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| created_by | BIGINT | | 생성자 ID |
| created_by_type | VARCHAR(20) | | 생성자 유형 (MEMBER) |
| updated_by | BIGINT | | 수정자 ID |
| updated_by_type | VARCHAR(20) | | 수정자 유형 |

**주문 상태값:**
`PENDING` / `PAID` / `IN_PREPARATION` / `SHIPPED` / `DELIVERED` / `FAILED` / `CANCELLED` / `RETURN_REQUESTED` / `RETURNED`

---

### order_item (주문 상품)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 주문 상품 ID |
| order_id | BIGINT | FK → order.id, NOT NULL | 주문 ID |
| product_id | BIGINT | FK → product.id, NOT NULL | 상품 ID |
| product_history_id | BIGINT | FK → product_history.id, NOT NULL | 주문 당시 상품 이력 |
| product_name | VARCHAR(100) | NOT NULL | 상품명 스냅샷 |
| price | BIGINT | NOT NULL | 가격 스냅샷 |
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

**재고 상태값:** `AVAILABLE` / `HOLD` / `TRANSFER` / `SOLD`

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
