# 02. 도메인 분석 설계

## 전체 흐름 한눈에 보기

```
요구사항 분석
  → 도메인 모델 설계 (엔티티 다이어그램)
  → 테이블 설계 (ERD)
  → 엔티티 클래스 개발
  → 엔티티 설계 주의점 적용
```

---

## 1. 도메인 모델 설계 핵심 결정

### 다대다 관계 처리
주문 ↔ 상품은 다대다지만, **중간 엔티티(OrderItem)로 풀어낸다**.
- `Order 1 ↔ N OrderItem N ↔ 1 Item`
- 중간 테이블에 `orderPrice`, `count` 같은 추가 컬럼이 필요하기 때문

### 상품 상속 구조
Item(공통) ← Book / Album / Movie **단일 테이블 전략(SINGLE_TABLE)** 선택.
- `DTYPE` 컬럼으로 구분
- 단순하고 빠른 조회를 위해 JOIN 전략보다 단일 테이블 전략 선택

### 연관관계 주인 결정 원칙
**FK가 있는 곳이 연관관계 주인**

| 관계 | 주인(FK 보유) | mappedBy |
|------|-------------|---------|
| Member ↔ Order | Order.member | Member.orders |
| Order ↔ OrderItem | OrderItem.order | Order.orderItems |
| Order ↔ Delivery | Order.delivery | Delivery.order |
| Category ↔ Item | @ManyToMany (실무 금지) | - |

---

## 2. 엔티티 설계시 3대 주의점

### ① 엔티티에 Setter 쓰지 말자

변경 포인트가 분산되어 유지보수가 어려워진다.
- 예제에서는 설명 편의상 Setter를 열었지만, **실무에서는 비즈니스 메서드로만 변경**
- Getter는 모두 열어도 괜찮음 (호출만으로 데이터가 변하지 않음)

### ② 모든 연관관계는 LAZY로

```java
// 반드시 명시적으로 LAZY 설정
@ManyToOne(fetch = FetchType.LAZY)
@OneToOne(fetch = FetchType.LAZY)
```

- `@ManyToOne`, `@OneToOne` 기본값이 EAGER → **반드시 LAZY로 변경**
- EAGER는 JPQL에서 N+1 문제 유발
- 연관 엔티티 필요 시 fetch join으로 해결

### ③ 컬렉션은 필드에서 초기화

```java
// 올바른 방법 — 필드 초기화
private List<Order> orders = new ArrayList<>();

// 위험한 방법 — 생성자나 메서드에서 초기화
public void init() {
    this.orders = new ArrayList<>(); // 하이버네이트가 감싼 PersistentBag이 교체됨
}
```

하이버네이트가 영속화 시 컬렉션을 `PersistentBag`으로 감싸는데, 나중에 교체하면 내부 메커니즘이 깨질 수 있음.

---

## 3. 연관관계 편의 메서드

양방향 관계에서 양쪽 세팅을 한 번에 처리하는 메서드. **연관관계 주인 쪽에 작성**.

```java
// Order 엔티티 내 — 연관관계 편의 메서드
public void setMember(Member member) {
    this.member = member;
    member.getOrders().add(this);  // 반대편도 함께 세팅
}

public void addOrderItem(OrderItem orderItem) {
    orderItems.add(orderItem);
    orderItem.setOrder(this);
}
```

---

## 4. 임베디드 타입 불변 설계

```java
@Embeddable
@Getter  // Setter 없음
public class Address {
    private String city;
    private String street;
    private String zipcode;

    protected Address() {}  // JPA 스펙: protected 기본 생성자 필수

    public Address(String city, String street, String zipcode) {
        this.city = city;
        this.street = street;
        this.zipcode = zipcode;
    }
}
```

- `protected` 기본 생성자: JPA가 리플렉션으로 객체 생성할 때 필요, public보다 protected가 안전
- Setter 없음: 불변 객체 → 공유해도 안전

---

## 5. 테이블명 / 컬럼명 전략

스프링 부트 기본 네이밍 전략 (`SpringPhysicalNamingStrategy`):
- 카멜케이스 → 언더스코어: `memberPoint` → `member_point`
- 대문자 → 소문자

테이블명이 `ORDER`가 아닌 `ORDERS`인 이유: `ORDER BY` 예약어 충돌 방지.

---

## stylink 적용 포인트

**강의 권장 방식**: Setter 금지, 모든 연관관계 LAZY, 컬렉션 필드 초기화, 연관관계 편의 메서드 활용.

**stylink 적용**:
- 엔티티 Setter 대신 비즈니스 메서드로 상태 변경 (`changeStatus()`, `cancel()` 등) — 이미 이 방향으로 설계됨
- 모든 `@ManyToOne`, `@OneToOne`에 `fetch = FetchType.LAZY` 명시 필수 (기본값 EAGER 주의)
- 컬렉션(`orderItems`, `reservationItems` 등) 전부 필드에서 `new ArrayList<>()` 초기화
- `Address` 임베디드 타입: `protected` 기본 생성자 + Setter 없음 → 이미 올바르게 설계됨
- `@ManyToMany` 절대 사용 금지 — stylink 설계에서도 이미 중간 엔티티로 풀어냄
