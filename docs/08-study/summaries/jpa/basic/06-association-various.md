# 다양한 연관관계 매핑

> JPA 기본 | N:1, 1:N, 1:1, N:M 전략 선택

---

## 전체 흐름 한눈에 보기

```
연관관계 설계 3가지 고려사항
  1. 다중성: N:1 / 1:N / 1:1 / N:M
  2. 방향: 단방향 / 양방향
  3. 주인: FK 있는 쪽이 주인 (@JoinColumn)

실무 선택 기준
  N:1 단방향  → 기본 (가장 많이 사용)
  N:1 양방향  → 역방향 탐색 필요 시 추가
  1:N 단방향  → 비권장 (UPDATE SQL 추가 발생)
  1:1         → 주 테이블에 FK 권장 (객체지향 친화적)
  N:M         → @ManyToMany 절대 사용 금지
               → 연결 테이블을 엔티티로 승격 (@OneToMany + @ManyToOne)
```

---

## 다대일 [N:1] — 권장

### 단방향 (가장 많이 사용)

```java
@Entity
public class Member {
    @ManyToOne
    @JoinColumn(name = "TEAM_ID")
    private Team team;
}
```

- FK는 항상 N 쪽(Many 쪽) 테이블에 존재
- Member가 Team을 참조, Team은 Member를 모름

### 양방향

```java
@Entity
public class Team {
    @OneToMany(mappedBy = "team")
    private List<Member> members = new ArrayList<>();
}
```

- FK 있는 Member.team이 주인, Team.members는 mappedBy(읽기 전용)
- 양쪽 서로 참조 → 연관관계 편의 메소드 필수

---

## 일대다 [1:N] — 비권장

### 단방향 (쓰지 말 것)

```java
@Entity
public class Team {
    @OneToMany
    @JoinColumn(name = "TEAM_ID")  // @JoinColumn 필수 (없으면 중간 테이블 생성)
    private List<Member> members = new ArrayList<>();
}
```

**문제점**: 1인 Team이 연관관계 주인이지만 FK는 N인 MEMBER 테이블에 있다.
- Team을 저장/수정할 때 MEMBER 테이블에 **추가 UPDATE SQL** 발생
- 성능 문제 + 예상치 못한 쿼리 → **다대일 양방향으로 대체**

### 양방향 (공식 지원 X)

```java
// Member 쪽에 읽기 전용 매핑 추가 (편법)
@ManyToOne
@JoinColumn(name = "TEAM_ID", insertable = false, updatable = false)
private Team team;
```

- 공식적으로 존재하지 않는 매핑, 다대일 양방향 사용 권장

---

## 일대일 [1:1]

### FK 위치 선택

| 구분 | 주 테이블에 FK | 대상 테이블에 FK |
|------|--------------|----------------|
| 선호 | 객체지향 개발자 | DB 개발자 |
| JPA 편의 | 편리 | 불편 (단방향 JPA 미지원) |
| 장점 | 주 테이블 조회만으로 대상 존재 확인 가능 | 1:1→1:N 변경 시 테이블 구조 유지 |
| 단점 | FK에 null 허용 가능 | **지연 로딩 불가 (항상 즉시 로딩)** |

**권장: 주 테이블에 FK + 주 테이블 엔티티가 주인**

### 주 테이블 FK 단방향

```java
@Entity
public class Member {
    @OneToOne
    @JoinColumn(name = "LOCKER_ID")
    private Locker locker;
}
```

### 주 테이블 FK 양방향

```java
@Entity
public class Locker {
    @OneToOne(mappedBy = "locker")
    private Member member;  // 읽기 전용
}
```

- FK 있는 Member.locker가 주인, Locker.member는 mappedBy

### 대상 테이블 FK 양방향

- 사실 매핑 방법은 주 테이블 FK 양방향과 동일 (FK 있는 쪽이 주인)
- 단방향은 JPA 미지원

---

## 다대다 [N:M] — 실무 사용 금지

### @ManyToMany 문제점

```java
// 이렇게 쓰지 말 것
@ManyToMany
@JoinTable(name = "MEMBER_PRODUCT")
private List<Product> products;
```

- 연결 테이블에 **부가 컬럼 추가 불가** (주문수량, 주문날짜 등)
- 엔티티와 테이블 불일치 → 예상치 못한 쿼리 발생
- **실무에서 절대 사용 금지**

### 연결 테이블 엔티티로 승격 (올바른 방법)

```java
// Member (1) ←→ (*) MemberProduct (*) ←→ (1) Product
@Entity
public class MemberProduct {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "MEMBER_ID")
    private Member member;

    @ManyToOne
    @JoinColumn(name = "PRODUCT_ID")
    private Product product;

    // 부가 컬럼 자유롭게 추가 가능
    private int orderAmount;
    private LocalDateTime orderDate;
}
```

- N:M → **@OneToMany + @ManyToOne** 으로 분해
- 연결 테이블에 독립 PK(대체키) 추가 권장 (복합 PK보다 유연)

---

## @JoinColumn 주요 속성

| 속성 | 설명 | 기본값 |
|------|------|--------|
| name | 매핑할 FK 이름 | 필드명_참조PK컬럼명 |
| referencedColumnName | 참조하는 대상 컬럼명 | 대상 테이블의 PK |
| foreignKey(DDL) | FK 제약조건 직접 지정 | |
| insertable / updatable | 등록/수정 가능 여부 (읽기 전용 매핑에 사용) | true |

## @ManyToOne / @OneToMany 주요 속성

| 속성 | @ManyToOne 기본값 | @OneToMany 기본값 |
|------|-----------------|-----------------|
| fetch | EAGER | LAZY |
| cascade | - | - |
| mappedBy | 없음 | 주인 필드명 지정 |

---

## 면접 Q&A

**Q: 일대다 단방향을 사용하지 않는 이유는?**  
A: 일대다 단방향에서는 Team 엔티티가 연관관계의 주인인데 FK는 MEMBER 테이블에 있다. 따라서 Team을 저장할 때 JPA가 MEMBER 테이블에 별도의 UPDATE SQL을 추가로 실행한다. 이는 성능 문제와 예상치 못한 쿼리를 유발하므로, 다대일 양방향 매핑을 사용하는 것이 권장된다.

**Q: 일대일 관계에서 FK를 어느 쪽 테이블에 두는 게 좋은가?**  
A: 주 테이블에 FK를 두는 것이 권장된다. 주 테이블 조회만으로 대상 테이블에 데이터가 있는지 확인할 수 있고, JPA 매핑이 편리하며 객체지향 개발자에게 익숙하다. 반면 대상 테이블에 FK를 두면 프록시의 한계로 지연 로딩 설정을 해도 항상 즉시 로딩되는 문제가 생긴다.

**Q: @ManyToMany를 실무에서 사용하지 않는 이유는?**  
A: @ManyToMany는 JPA가 자동으로 만드는 연결 테이블에 부가 컬럼을 추가할 수 없다. 실무에서는 연결 테이블에 반드시 주문 날짜, 수량 같은 추가 정보가 필요하다. 또한 예상치 못한 쿼리가 발생하기 쉽다. 대신 연결 테이블을 별도 엔티티로 승격하고 @OneToMany/@ManyToOne으로 분해해서 사용해야 한다.

**Q: 다대다 연결 테이블 엔티티의 PK 전략은?**  
A: 두 FK를 복합 PK로 사용하는 방법과 별도의 독립 PK를 추가하는 방법이 있다. 독립 PK 방식이 권장된다. 복합 PK는 @IdClass나 @EmbeddedId 설정이 복잡하고, 나중에 비즈니스가 바뀌어 복합 PK의 구성이 달라지면 수정 범위가 커진다. 독립 PK(AUTO_INCREMENT)를 두면 유연성이 높다.

**Q: @ManyToOne의 기본 fetch 전략이 EAGER인 문제점은?**  
A: Member를 조회하면 Team도 항상 즉시 조회한다. 불필요한 JOIN이 발생하고, JPQL에서 N+1 문제를 일으키기 쉽다. 실무에서는 `@ManyToOne(fetch = FetchType.LAZY)`로 명시적으로 지연 로딩을 설정하고, 필요한 경우에만 페치 조인으로 한 번에 조회하는 것이 권장된다.
