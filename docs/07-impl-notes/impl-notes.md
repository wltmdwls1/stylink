# 인프라 셋업 노트

> 각 인프라 구성 요소의 목적, 메서드 연결 구조, 핵심 포인트 정리.
> 소스 재분석 시 빠르게 파악하기 위한 레퍼런스.

---

## [인프라] 1. GlobalExceptionHandler

**목적:** 모든 예외 응답을 `ApiResponse.fail(message)` 형태로 통일. Controller까지 예외가 올라오면 여기서 잡아서 일관된 형식으로 내려줌.

**메서드 연결 구조:**
```
throw new BusinessException(ErrorCode.XXX)
    → handleBusinessException()
        → e.getErrorCode().getHttpStatus()   // HTTP 상태 코드 결정
        → e.getMessage()                      // ErrorCode 생성자에서 super(message) 연결됨
        → ApiResponse.fail(message)           // 응답 포장
```

**핵심 포인트:**
- `BusinessException`의 두 생성자 모두 `super(message)`로 연결 → 핸들러는 분기 없이 `e.getMessage()` 하나로 처리
- `NoHandlerFoundException`은 yml 설정 없으면 핸들러에 안 잡힘 (`throw-exception-if-no-handler-found: true` 필수)
- `warn` vs `error` 구분: 예측 가능한 예외(1~4번)는 warn, catch-all(5번)은 error + 스택트레이스
- `common` 모듈에 배치한 이유: `scanBasePackages = {"com.stylink"}` 덕분에 fo-api/bo-api 양쪽에 자동 적용

---

## [인프라] 2. Security 기본 설정

**목적:** JWT 기반 Stateless 인증 구조의 뼈대. 지금은 Swagger 열어두고 나머지 막는 최소 설정. JWT 필터 자리만 예약해둠.

**메서드 연결 구조:**
```
HttpSecurity
    → csrf disable             // REST API는 CSRF 불필요 (쿠키 세션 없음)
    → STATELESS                // 세션 안 씀 → JWT로 대체
    → permitAll: Swagger 경로  // 3단계에서 접속하려면 미리 열어둬야 함
    → anyRequest authenticated // 나머지 전부 차단
    → [JWT 필터 자리]           // 4단계에서 addFilterBefore로 연결
```

**핵심 포인트:**
- `SessionCreationPolicy.STATELESS` 설정 안 하면 Security가 세션 만들려 함 → JWT 인증이 무의미해짐
- Swagger 경로를 여기서 미리 열어야 3단계에서 바로 접속 가능
- JWT 필터는 4단계에서 `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`로 연결
- fo-api / bo-api 각각 별도 SecurityConfig → 나중에 권한 정책이 달라질 수 있으므로 분리 유지

---

## [인프라] 3. Swagger

**목적:** Controller/DTO 없이도 API 구조 확인 가능. 비즈니스 로직 구현 시 빠른 테스트 도구.

**메서드 연결 구조:**
```
SwaggerConfig → OpenAPI 빈 등록
    → springdoc이 @RestController 스캔
    → /v3/api-docs 자동 생성
    → /swagger-ui/index.html 에서 렌더링
```

**핵심 포인트:**
- 2단계 SecurityConfig에서 `/swagger-ui/**`, `/v3/api-docs/**` 열어줬기 때문에 접속 가능 (순서 중요)
- fo-api(8080), bo-api(8090) 각각 별도 SwaggerConfig → 서버별 제목/설명 다르게
- springdoc 2.x는 Spring Boot 3.x 전용 (`springdoc-openapi-starter-webmvc-ui`)
- 나중에 JWT 인증 붙으면 SwaggerConfig에 `SecurityScheme` 추가해야 토큰 넣고 테스트 가능
- 접속 URL: fo-api → `http://localhost:8080/swagger-ui/index.html`, bo-api → `http://localhost:8090/swagger-ui/index.html`

---

# 구현 전 체크리스트

> 각 도메인 구현 시작 전 반드시 읽고 들어갈 것.
> 설계 단계에서 결정한 내용들 — 구현 시 놓치기 쉬운 포인트 모음.

---

## 1. Entity 상태 전이 메서드 패턴

> 🕐 **적용 시점: 각 도메인 구현 시작 시 (Order/Inventory/Reservation)**

상태를 직접 변경하지 말 것. Entity에 전이 메서드를 두고 규칙을 강제할 것.

```java
// ❌ 금지
// order.setStatus(OrderStatus.PAID);

// ✅ 올바른 방식
// order.pay();
```

각 메서드에서 현재 상태 검증 후 전이. 잘못된 전이 시 BusinessException 던질 것.

**구현 대상:**
- `Order`: pay() / prepare() / ship() / deliver() / cancel() / requestReturn() / returned()
- `Reservation`: confirm() / start() / complete() / cancel()
- `Inventory`: reserve() / transit() / sell() / restore()

---

## 2. order_number 생성 로직

> 🕐 **적용 시점: OrderService 구현 시 (주문 생성 로직)**

형식: `ORD` + 날짜 8자리 + 시퀀스 5자리 = 16자리
예) `ORD2024123100001`

- 날짜는 `LocalDate.now()` 기준
- 시퀀스는 당일 주문 건수 기반 (DB에서 당일 max 조회 후 +1)
- 동시 주문 시 중복 방지 필요 → 유니크 제약으로 최종 방어
- OrderService에서 주문 생성 시 자동 발급

---

## 3. 전화번호 암호화 처리 (AES256 + SHA-256)

> 🕐 **적용 시점: MemberService 회원가입 구현 시 (Stylist도 동일하게 적용)**

저장 시:
```
전화번호 원문 → AES256(CBC/GCM) 암호화 → phone 저장
전화번호 원문 → SHA-256 해시           → phone_hash 저장
```

조회/인증 시:
```
입력된 전화번호 → SHA-256 해시 → phone_hash 로 DB 조회
```

화면 표시 시:
```
phone → AES256 복호화 → 원문 반환
```

- Member, Stylist 둘 다 동일하게 적용
- AES256 키는 환경변수로 관리 (`${AES_SECRET_KEY}`)
- 암호화/복호화 유틸 클래스를 common 모듈에 작성

---

## 4. 낙관적 락 예외 처리

> 🕐 **적용 시점: InventoryService.reserve() 구현 시**

Inventory.reserve() 호출 시 동시 요청이 들어오면 `OptimisticLockException` 발생.

```java
/*
try {
    inventory.reserve();
} catch (OptimisticLockException e) {
    throw new BusinessException(ErrorCode.INVENTORY_NOT_AVAILABLE);
}*/
```

- Service 레이어에서 catch 후 사용자 친화적 메시지로 변환
- 재시도 로직은 구현하지 않음 (실패 즉시 응답)

---

## 5. InventoryLog 기록 시점

> 🕐 **적용 시점: InventoryService 구현 시 (상태 변경 메서드마다)**

Inventory 상태 변경 시 반드시 InventoryLog도 함께 기록할 것.

```java
/*
inventory.reserve();
inventoryLogRepository.save(new InventoryLog(
    inventory.getId(),
    InventoryStatus.AVAILABLE,
    InventoryStatus.RESERVED,
    InventoryChangeReason.RESERVED_BY_ORDER,
    "주문번호: " + order.getOrderNumber()  // description
));*/
```

- InventoryService 내부에서 상태 변경과 로그 기록을 항상 함께 처리
- description은 선택이지만 주문번호/예약번호 등 맥락 정보 넣으면 추적에 유리

---

## 6. append-only 테이블 주의사항

> 🕐 **적용 시점: 각 해당 도메인 구현 시**

`BaseLogEntity` 사용 테이블은 UPDATE 금지.

- `ProductHistory` — 상품 변경 시 INSERT만 (상품 관리 구현 시)
- `OrderItem` — 주문 생성 시 INSERT만 (OrderService 구현 시)
- `OrderHistory` — 상태 변경 시 INSERT만 (OrderService 구현 시)
- `InventoryLog` — 상태 변경 시 INSERT만 (InventoryService 구현 시)
- `Wishlist` — 찜 추가 시 INSERT만, 삭제는 DELETE (WishlistService 구현 시)

---

## 7. 트랜잭션 경계 (사용자 담당)

> 🕐 **적용 시점: OrderService 구현 시 (@Transactional 위치 결정)**

핵심 흐름은 단일 트랜잭션으로 묶을 것.

- 주문 생성: 재고 RESERVED + Order 생성 + OrderHistory 기록
- 결제 완료: Payment SUCCESS + Order PAID + OrderHistory 기록
- 결제 실패: 전체 롤백 → 재고 자동 복구
- 반품 완료: 재고 AVAILABLE 복구 + 환불 처리 연계

부가 작업(알림, 로그)은 트랜잭션 밖에서 처리.

---

## 8. API 문서화 전환 시점

> 🕐 **적용 시점: 핵심 비즈니스 로직 3개 완성 후**

**현재: Swagger (springdoc-openapi)**
- 구현 중 빠른 API 확인용
- 어노테이션 기반으로 자동 생성

**전환 시점: 아래 3개 완성 후**
- `InventoryService` (재고 상태 전이)
- `OrderService` (주문 생성 → 결제)
- `PaymentService` (Mock 결제 처리)

**전환 후: Testcontainers + data.sql + Spring REST Docs**
- Testcontainers: 실제 MySQL 환경에서 격리된 테스트
- data.sql: 테스트용 최소 더미 데이터 (회원 1명, 상품 1개 등)
- Spring REST Docs: 테스트 코드 기반 API 문서 자동 생성
- 전환 시 Swagger 의존성 제거 + REST Docs 추가만 하면 됨
