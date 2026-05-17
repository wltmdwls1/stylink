# JPA Entity 설계서

> ERD를 기반으로 JPA Entity 클래스 구조를 정의합니다.
> 실제 구현은 이 문서를 참고하여 직접 작성합니다.

---

## 설계 원칙

| 항목 | 결정 | 이유 |
|------|------|------|
| 연관관계 방향 | **단방향 우선** | 불필요한 역방향 참조 제거, 복잡도 감소 |
| Fetch 전략 | **ALL LAZY** | N+1 문제는 JPQL fetch join으로 명시적 제어 |
| BaseEntity | **일반/로그 분리** | append-only 테이블에 불필요한 수정자 컬럼 제거 |
| 상태값 | **Enum + 한글 설명** | `private final String description` 패턴 |
| 낙관적 락 | **Inventory만 @Version** | 동시 HOLD 중복 방지 핵심 |

---

## 공통 구조

### BaseEntity (일반 테이블용)

```java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private Long createdBy;          // member.id 또는 admin.id

    @Column(length = 20)
    private String createdByType;    // MEMBER / ADMIN / SYSTEM

    @Column
    private Long updatedBy;

    @Column(length = 20)
    private String updatedByType;
}
```

> `createdAt`, `updatedAt` → JPA Auditing 자동 처리
> `createdBy`, `createdByType`, `updatedBy`, `updatedByType` → Service 레이어에서 직접 세팅

---

### BaseLogEntity (append-only 테이블용)

```java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseLogEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private Long createdBy;

    @Column(length = 20)
    private String createdByType;
}
```

**적용 테이블:** `OrderHistory`, `InventoryLog`, `OutboundApiLog`, `InboundApiLog`

---

### AuditingConfig

```java
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // createdBy/updatedBy는 Service에서 직접 세팅하므로 AuditorAware 불필요
}
```

---

## Enum 목록

### MemberStatus
```java
public enum MemberStatus {
    ACTIVE("정상"),
    WITHDRAWN("탈퇴");
}
```

### AuthStatus
```java
public enum AuthStatus {
    UNVERIFIED("인증대기"),
    VERIFIED("인증완료");
}
```

### MemberGrade
```java
public enum MemberGrade {
    NORMAL("일반회원"),
    VIP("VIP");
}
```

### AdminRole
```java
public enum AdminRole {
    SUPER_ADMIN("최고관리자"),
    MANAGER("매니저");
}
```

### InventoryStatus
```java
public enum InventoryStatus {
    AVAILABLE("판매가능"),
    HOLD("선점됨"),
    TRANSFER("이동중"),     // 2차 전용
    SOLD("판매완료");
}
```

### OrderStatus
```java
public enum OrderStatus {
    PENDING("결제대기"),
    PAID("결제완료"),
    IN_PREPARATION("상품준비중"),
    SHIPPED("배송중"),
    DELIVERED("배송완료"),
    FAILED("결제실패"),
    CANCELLED("주문취소"),
    RETURN_REQUESTED("반품요청"),
    RETURNED("반품완료");
}
```

### PaymentStatus
```java
public enum PaymentStatus {
    PENDING("결제대기"),
    SUCCESS("결제완료"),
    FAILED("결제실패"),
    CANCEL_REQUESTED("취소요청"),
    CANCELLED("취소완료"),
    REFUND_REQUESTED("환불요청"),
    REFUNDED("환불완료");
}
```

### DeliveryStatus
```java
public enum DeliveryStatus {
    READY("배송준비"),
    SHIPPED("배송중"),
    DELIVERED("배송완료"),
    RETURN_REQUESTED("반품요청"),
    RETURNED("반품완료");
}
```

### ApiType (OutboundApiLog용)
```java
public enum ApiType {
    PG("PG결제"),
    DELIVERY("배송"),
    NOTIFICATION("알림"),
    AUTH("본인인증");
}
```

### ApiSource (InboundApiLog용)
```java
public enum ApiSource {
    DELIVERY("배송"),
    STYLIST("스타일리스트"),
    NOTIFICATION("알림"),
    AUTH("인증");
}
```

### AuditType (createdByType / updatedByType용)
```java
public enum AuditType {
    MEMBER("고객"),
    ADMIN("관리자"),
    SYSTEM("시스템");
}
```

---

## Entity 상세

---

### Category

```java
@Entity
@Table(name = "category")
public class Category extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;     // null = 대분류
}
```

**연관관계:** 자기참조 (대분류/중분류 2단계)
**특이사항:** children 컬렉션 없음 (단방향) — 하위 목록은 Repository에서 조회

---

### Member

```java
@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;                 // BCrypt 암호화

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;                    // AES256 암호화 저장

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthStatus authStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberGrade grade;

    @Column(nullable = false)
    private Long totalPurchaseAmount = 0L;
}
```

**특이사항:**
- `password` → BCrypt 해시값 저장
- `phone` → AES256 암호화 저장, 응답 시 마스킹 처리 (010-****-5678)
- Wishlist, Cart, Order와의 연관관계는 각 테이블에서 단방향으로 참조

---

### Admin

```java
@Entity
@Table(name = "admin")
public class Admin extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;                 // BCrypt 암호화

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminRole role;
}
```

---

### Product

```java
@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
}
```

**특이사항:**
- 변경 가능한 정보(name, price 등)는 모두 `ProductHistory`에서 관리
- 현재 유효 정보 조회: `ProductHistoryRepository.findByProductAndIsCurrent(product, "Y")`

---

### ProductHistory

```java
@Entity
@Table(name = "product_history",
    indexes = @Index(name = "idx_product_history_current",
                     columnList = "product_id, is_current"))
public class ProductHistory extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Long price;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private LocalDate saleStartDate;

    @Column(nullable = false)
    private LocalDate saleEndDate;

    @Column(nullable = false)
    private boolean isCurrent;              // true = 현재, false = 이력
}
```

**SCD Type 2 패턴:**
- 정보 변경 시 → 기존 레코드 `isCurrent = false`, `saleEndDate` 업데이트 + 새 레코드 생성
- 현재 유효 레코드는 항상 `isCurrent = true` 인 레코드 1개
- 유일성 보장: DB 제약 대신 `@Transactional` 애플리케이션 레벨에서 보장

---

### Wishlist

```java
@Entity
@Table(name = "wishlist",
    uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "product_id"}))
public class Wishlist extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column
    private Integer quantity;              // nullable — 2차 예약 시 활성화

    @Column(length = 255)
    private String memo;                   // nullable — 2차 예약 시 활성화
}
```

**특이사항:** `quantity`, `memo` → 1차(온라인)에서는 null / 2차 예약 확정 시 입력

---

### Cart

```java
@Entity
@Table(name = "cart")
public class Cart extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;
}
```

**특이사항:** Member와 1:1 관계, CartItem은 CartItemRepository에서 cart_id로 조회

---

### CartItem

```java
@Entity
@Table(name = "cart_item",
    uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "product_id"}))
public class CartItem extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;
}
```

---

### Order ⭐

```java
@Entity
@Table(name = "orders")           // order는 SQL 예약어 → orders로 매핑
public class Order extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String orderNo;        // 예: ORD-20260513-00001

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false)
    private Long totalPrice;
}
```

**특이사항:**
- 테이블명 `orders` — `order`는 SQL 예약어
- `orderNo` 생성 규칙: `ORD-{yyyyMMdd}-{5자리 시퀀스}`
- OrderItem, OrderHistory, Payment, Delivery는 각 Repository에서 order_id로 조회

---

### OrderItem

```java
@Entity
@Table(name = "order_item")
public class OrderItem extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_history_id", nullable = false)
    private ProductHistory productHistory;

    @Column(nullable = false, length = 100)
    private String productName;            // 주문 시점 스냅샷

    @Column(nullable = false)
    private Long price;                    // 주문 시점 스냅샷

    @Column(nullable = false)
    private Integer quantity;
}
```

**특이사항:**
- `productName`, `price` → 주문 당시 스냅샷 (상품 정보 변경돼도 유지)
- `productHistory` → 주문 당시 `isCurrent = Y` 레코드 참조

---

### OrderHistory

```java
@Entity
@Table(name = "order_history")
public class OrderHistory extends BaseLogEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(length = 255)
    private String description;
}
```

**특이사항:** append-only — `BaseLogEntity` 적용, 수정 없음

---

### Inventory ⭐ 핵심

```java
@Entity
@Table(name = "inventory")
public class Inventory extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryStatus status;

    @Version
    @Column(nullable = false)
    private Long version;                  // 낙관적 락 — 동시 HOLD 중복 방지
}
```

**특이사항:**
- `@Version` → JPA 낙관적 락, 동시에 HOLD 시도 시 하나만 성공
- 동시성 제어 핵심 컬럼 — 절대 임의로 수정 금지

---

### InventoryLog

```java
@Entity
@Table(name = "inventory_log")
public class InventoryLog extends BaseLogEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryStatus toStatus;

    @Column(nullable = false)
    private Integer quantityChange;

    @Column(length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_order_id")
    private Order relatedOrder;            // nullable
}
```

**특이사항:** append-only — `BaseLogEntity` 적용

---

### Payment

```java
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(nullable = false)
    private Long amount;

    @Column(length = 100)
    private String pgTransactionId;        // Mock PG 거래 ID
}
```

---

### Delivery

```java
@Entity
@Table(name = "delivery")
public class Delivery extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(length = 50)
    private String trackingNo;             // Mock 운송장 번호

    @Column(nullable = false, length = 255)
    private String address;
}
```

---

### OutboundApiLog

```java
@Entity
@Table(name = "outbound_api_log")
public class OutboundApiLog extends BaseLogEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApiType apiType;

    @Column(columnDefinition = "JSON")
    private String requestBody;

    @Column(columnDefinition = "JSON")
    private String responseBody;

    @Column
    private Integer statusCode;

    @Column(nullable = false)
    private Boolean success;

    @Column(length = 50)
    private String relatedId;              // 관련 주문번호 / 예약번호

    @Column(nullable = false)
    private LocalDateTime calledAt;
}
```

---

### InboundApiLog

```java
@Entity
@Table(name = "inbound_api_log")
public class InboundApiLog extends BaseLogEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApiSource source;

    @Column(columnDefinition = "JSON")
    private String requestBody;

    @Column(columnDefinition = "JSON")
    private String responseBody;

    @Column
    private Integer statusCode;

    @Column(nullable = false)
    private Boolean success;

    @Column(length = 50)
    private String relatedId;

    @Column(nullable = false)
    private LocalDateTime receivedAt;
}
```

---

## 연관관계 요약

| Entity | 연관관계 | 대상 | 방향 |
|--------|---------|------|------|
| Category | @ManyToOne | Category (self) | 단방향 |
| Product | @ManyToOne | Category | 단방향 |
| ProductHistory | @ManyToOne | Product | 단방향 |
| Wishlist | @ManyToOne | Member, Product | 단방향 |
| Cart | @OneToOne | Member | 단방향 |
| CartItem | @ManyToOne | Cart, Product | 단방향 |
| Order | @ManyToOne | Member | 단방향 |
| OrderItem | @ManyToOne | Order, Product, ProductHistory | 단방향 |
| OrderHistory | @ManyToOne | Order | 단방향 |
| Inventory | @OneToOne | Product | 단방향 |
| InventoryLog | @ManyToOne | Inventory, Order | 단방향 |
| Payment | @OneToOne | Order | 단방향 |
| Delivery | @OneToOne | Order | 단방향 |

---

## 주요 주의사항

| 항목 | 내용 |
|------|------|
| `order` 테이블명 | SQL 예약어 → `@Table(name = "orders")` 필수 |
| `@Version` | Inventory 전용 — 임의 수정 절대 금지 |
| 전화번호 암호화 | Service에서 AES256 암호화 후 저장, 조회 시 복호화 + 마스킹 |
| ProductHistory | `isCurrent = Y` 레코드가 항상 유일하게 유지되어야 함 |
| ALL LAZY | @OneToOne도 LAZY 적용 — 프록시 객체 주의 |
| Enum 저장 | `@Enumerated(EnumType.STRING)` — ORDINAL 절대 사용 금지 |
