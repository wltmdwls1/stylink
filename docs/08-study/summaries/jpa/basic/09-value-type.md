# 09. 값 타입

## 전체 흐름 한눈에 보기

```
JPA 타입
├── 엔티티 타입 (@Entity)  — 식별자 있음, 독립 생명주기, 공유 가능
└── 값 타입               — 식별자 없음, 엔티티에 종속, 공유 금지
    ├── 기본값 타입 (int, String, Integer ...)
    ├── 임베디드 타입 (@Embeddable) ← 실무에서 자주 사용
    └── 값 타입 컬렉션 (@ElementCollection) ← 실무에서 거의 쓰지 마라
```

---

## 1. 기본값 타입

`int`, `double`, `Integer`, `Long`, `String` — 엔티티가 삭제되면 같이 삭제. 공유 불가.

- 기본 타입(`int`, `double`)은 대입 시 값 복사 → 안전
- 래퍼 클래스/String은 참조를 공유하지만 변경 자체가 불가 → 안전

---

## 2. 임베디드 타입 (복합 값 타입)

여러 필드를 묶어 의미 있는 타입으로 만든 것. DB 테이블은 변하지 않고 객체만 정리됨.

```java
@Embeddable
public class Address {
    private String city;
    private String street;
    private String zipcode;
    // 기본 생성자 필수
    // setter 없이 불변으로 설계
}

@Entity
public class Member {
    @Embedded
    private Address homeAddress;
}
```

테이블: `MEMBER (CITY, STREET, ZIPCODE)` — 임베디드 타입 쓰기 전과 동일.

### 같은 타입을 두 번 쓸 때

```java
@Embedded
@AttributeOverrides({
    @AttributeOverride(name="city", column=@Column(name="work_city")),
    @AttributeOverride(name="street", column=@Column(name="work_street")),
    @AttributeOverride(name="zipcode", column=@Column(name="work_zipcode"))
})
private Address workAddress;
```

### 장점
- 재사용, 응집도 향상
- 값 타입에 비즈니스 메서드 추가 가능 (`period.isWork()`)

---

## 3. 값 타입과 불변 객체

임베디드 타입은 객체라서 **참조를 공유하면 side effect** 발생.

```java
// 위험 — address 하나를 member1, member2가 공유
member2.setAddress(member1.getAddress());
address.setCity("NewCity"); // → member1도 변경됨!
```

**해결책: 불변 객체**

```java
@Embeddable
public class Address {
    private final String city;
    // setter 없음 → 생성 후 변경 불가
    public Address(String city) { this.city = city; }
}

// 주소 변경 시 새 객체 생성
member.setAddress(new Address("NewCity"));
```

> 불변이라는 작은 제약으로 부작용이라는 큰 재앙을 막을 수 있다.

---

## 4. 값 타입 비교

```java
Address a = new Address("서울시");
Address b = new Address("서울시");

a == b          // false (참조 비교 — 동일성)
a.equals(b)     // true  (값 비교 — 동등성) ← 임베디드 타입에 equals/hashCode 재정의 필수
```

---

## 5. 값 타입 컬렉션 — 실무에서 거의 쓰지 마라

```java
@ElementCollection
@CollectionTable(name = "FAVORITE_FOOD",
    joinColumns = @JoinColumn(name = "MEMBER_ID"))
@Column(name = "FOOD_NAME")
private Set<String> favoriteFoods = new HashSet<>();
```

별도 테이블 필요. **변경 시 전체 DELETE 후 재INSERT** → 성능 최악.

### 값 타입 컬렉션의 심각한 제약

- 수정 시 연관된 모든 데이터 삭제 → 현재 값 전부 재저장
- 테이블의 모든 컬럼이 PK를 구성 → null 불가, 중복 불가
- 식별자 없어서 변경 추적 불가

### 실무 대안: 일대다 엔티티

```java
@Entity
public class AddressHistory {
    @Id @GeneratedValue
    private Long id;

    @Embedded
    private Address address;
}

// Member 쪽
@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
@JoinColumn(name = "MEMBER_ID")
private List<AddressHistory> addressHistory = new ArrayList<>();
```

값 타입 컬렉션처럼 동작하면서 식별자/추적/성능 문제 해결.

---

## 정리

| 구분 | 값 타입 | 엔티티 타입 |
|------|--------|-----------|
| 식별자 | X | O |
| 생명주기 | 엔티티에 종속 | 독립 |
| 공유 | 금지 (복사해서 사용) | 가능 |
| 변경 | 불변 객체 권장 | 가능 |

**판단 기준**: 식별자가 필요하고 지속 추적/변경이 필요하면 → 엔티티

---

## stylink 적용 포인트

**강의 권장 방식**: 임베디드 타입은 불변 객체로, 컬렉션은 엔티티로 올려라.

**stylink 적용**:
- `Address` 임베디드 타입 이미 설계 완료 → **setter 없이 불변 객체로 구현할 것**
  - 주소 변경 시 새 `Address` 객체 생성으로 교체
- 값 타입 컬렉션은 사용하지 않음 — 강의 방식 그대로 적용
  - 이력성 데이터(배송 주소 이력 등)가 필요하면 별도 엔티티로 설계
- `BaseEntity`의 `createdAt`, `updatedAt`은 기본값 타입 → 이미 올바르게 설계됨
