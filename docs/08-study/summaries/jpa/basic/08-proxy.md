# 08. 프록시와 연관관계 관리

## 전체 흐름 한눈에 보기

```
연관 엔티티를 언제 로딩할까?
  ├── 항상 함께 → EAGER (즉시 로딩)  ← 실무에서 쓰지 마라
  └── 필요할 때만 → LAZY (지연 로딩) ← 실무 표준
                              ↑
                          프록시가 담당

영속성 전이 (CASCADE)  : 부모 저장/삭제 시 자식도 함께
고아 객체 (orphanRemoval): 컬렉션에서 제거된 자식 자동 삭제
```

---

## 1. 프록시

### 기본 개념

```java
em.find()          // DB 조회 → 실제 엔티티 반환
em.getReference()  // DB 조회 안 함 → 프록시(가짜) 반환
```

프록시는 실제 클래스를 **상속**받아 만든 껍데기. 내부에 `target` 참조를 보관하고, 실제 메서드 호출 시 `target`으로 위임(delegate).

### 초기화 흐름

```
client.getName() 호출
  → 프록시: target이 null이면 영속성 컨텍스트에 초기화 요청
  → 영속성 컨텍스트: DB 조회 후 실제 Entity 생성, target에 연결
  → 이후 target.getName() 호출
```

**초기화는 한 번만** — 이후엔 target 참조를 그냥 사용.

### 핵심 주의사항

| 상황 | 동작 |
|------|------|
| 1차 캐시에 이미 있으면 | `getReference()`도 실제 엔티티 반환 |
| 타입 비교 | `==` 실패, **`instanceof` 사용** |
| 준영속 상태에서 초기화 | `LazyInitializationException` 발생 |

---

## 2. 즉시 로딩 vs 지연 로딩

### 지연 로딩 LAZY — 실무 표준

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "TEAM_ID")
private Team team;
```

`em.find(Member)` → member만 DB 조회. `team`은 프록시.  
`member.getTeam().getName()` 호출 시점에 Team SELECT 쿼리 발생.

### 즉시 로딩 EAGER — 사용 금지

```java
@ManyToOne(fetch = FetchType.EAGER)
```

`em.find(Member)` → Member + Team JOIN 해서 한 번에 조회.

**즉시 로딩의 문제점**
- JPQL에서 **N+1 문제** 발생 (Member 100건 조회 → Team SELECT 100번 추가)
- 예상치 못한 JOIN 쿼리
- 연관관계가 깊어질수록 통제 불가

### 기본값 주의

| 어노테이션 | 기본 fetch |
|-----------|-----------|
| `@ManyToOne` | **EAGER** ← 반드시 LAZY로 바꿀 것 |
| `@OneToOne` | **EAGER** ← 반드시 LAZY로 바꿀 것 |
| `@OneToMany` | LAZY (괜찮음) |
| `@ManyToMany` | LAZY (괜찮음) |

**결론: 모든 연관관계 LAZY. 필요하면 JPQL fetch join으로 해결.**

---

## 3. 영속성 전이 (CASCADE)

부모를 영속화할 때 자식도 자동으로 영속화하는 편의 기능. **연관관계 매핑과 무관**.

```java
@OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
private List<Child> children = new ArrayList<>();
```

`em.persist(parent)` → child도 함께 persist.

### CASCADE 종류

| 타입 | 용도 |
|------|------|
| **ALL** | 모두 적용 (가장 많이 사용) |
| **PERSIST** | 저장 시만 전이 |
| **REMOVE** | 삭제 시만 전이 |
| MERGE / REFRESH / DETACH | 드물게 사용 |

### 사용 조건

- 자식이 **하나의 부모에만 종속**될 때 (다른 엔티티가 자식을 참조하지 않을 때)
- 라이프사이클이 동일할 때

---

## 4. 고아 객체 (orphanRemoval)

부모 컬렉션에서 제거된 자식을 자동 DELETE.

```java
@OneToMany(mappedBy = "parent", orphanRemoval = true)
private List<Child> children;

// 사용
parent.getChildren().remove(0);  // → DELETE FROM CHILD WHERE ID=?
```

**주의**: `@OneToOne`, `@OneToMany`만 가능. 단일 소유자일 때만 사용.

---

## 5. CASCADE.ALL + orphanRemoval=true 조합

```java
@OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
```

- 부모 저장 → 자식 자동 저장
- 부모 삭제 → 자식 자동 삭제
- 컬렉션 제거 → 해당 자식 자동 삭제

**자식의 생명주기를 완전히 부모가 관리** → DDD의 Aggregate Root 패턴 구현에 적합.

---

## stylink 적용 포인트

**강의 권장 방식**: 모든 연관관계 LAZY, 필요한 경우만 fetch join.

**stylink 적용**:
- 모든 `@ManyToOne`, `@OneToOne`에 `fetch = FetchType.LAZY` 명시 필수
  - 기본값이 EAGER라 설정 안 하면 N+1 지뢰가 됨
- `Order → OrderItem`, `Order → Delivery` 관계에 `cascade = CascadeType.ALL`
  - 주문 생성 시 OrderItem / Delivery 함께 persist 가능
  - stylink의 오케스트레이터 패턴(OrderService)에서 Order만 저장해도 전체 반영
- `Reservation → ReservationItem` 등 완전 종속 관계에도 `ALL + orphanRemoval = true` 검토
  - 예약 취소 시 자식 항목도 자동 삭제 가능
- **주의**: 재고(`Inventory`)는 여러 서비스에서 참조 → CASCADE 절대 금지, 독립 트랜잭션으로 처리
