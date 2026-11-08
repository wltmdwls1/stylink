# 연관관계 매핑 기초

> JPA 기본 | 단방향/양방향, 연관관계의 주인(mappedBy)

---

## 전체 흐름 한눈에 보기

```
[문제] FK를 Long으로 보관 → 객체 그래프 탐색 불가 (order.getMember() 불가)
  ↓
[해결] @ManyToOne + @JoinColumn → 참조로 연관 객체 접근

단방향: Member → Team (team 필드)
양방향: Team → Member 추가 (List<Member> members, mappedBy)

핵심 규칙
  - 주인(Owner) = FK 있는 곳 (@JoinColumn 쪽)
  - 주인만 FK 등록/수정 가능
  - 비주인(mappedBy) = 읽기 전용, DB에 영향 없음
  - 항상 양쪽에 값을 세팅할 것 (연관관계 편의 메소드 활용)
```

---

## 왜 연관관계가 필요한가

```java
// 나쁜 예 — FK를 Long으로 보관
public class Order {
    private Long memberId; // FK만 있음
}

// 팀을 찾으려면 별도 조회 필요
Member m = em.find(Member.class, order.getMemberId()); // 객체지향 X

// 좋은 예 — 참조로 보관
public class Order {
    @ManyToOne @JoinColumn(name = "MEMBER_ID")
    private Member member;
}
// 참조로 바로 탐색
Member m = order.getMember(); // 객체지향 O
```

테이블은 FK 조인으로 연관 테이블을 찾고, 객체는 참조로 연관 객체를 찾는다. 이 차이를 JPA가 메워준다.

---

## 단방향 연관관계

### 매핑 방법

```java
@Entity
public class Member {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne                        // 다대일 관계 (Member N : Team 1)
    @JoinColumn(name = "TEAM_ID")     // FK 컬럼명 지정
    private Team team;
}
```

### 저장 / 조회 / 수정

```java
// 저장
Team team = new Team();
em.persist(team);

Member member = new Member();
member.setTeam(team);  // 참조 세팅 → JPA가 TEAM_ID(FK) 자동 관리
em.persist(member);

// 조회 (객체 그래프 탐색)
Member findMember = em.find(Member.class, member.getId());
Team findTeam = findMember.getTeam(); // 참조로 바로 접근

// 수정
member.setTeam(teamB); // 참조만 바꾸면 UPDATE SQL 자동 생성
```

---

## 양방향 연관관계

### 매핑 방법

```java
// Member — 단방향과 동일
@Entity
public class Member {
    @ManyToOne
    @JoinColumn(name = "TEAM_ID")
    private Team team;
}

// Team — 역방향 컬렉션 추가
@Entity
public class Team {
    @OneToMany(mappedBy = "team")        // "team" = Member.team 필드명
    private List<Member> members = new ArrayList<>();
}
```

```java
// 역방향 조회
Team findTeam = em.find(Team.class, team.getId());
List<Member> members = findTeam.getMembers(); // Team → Member 탐색
```

테이블은 **FK 하나**로 양쪽 조인이 가능하다. 객체는 단방향 2개(Member→Team, Team→Member)를 만들어야 한다.

---

## 연관관계의 주인 (Owner)

### 왜 주인이 필요한가

객체 양방향 관계는 사실 **단방향 2개**다. FK는 하나인데 객체 참조는 두 곳에 있으므로, 어느 쪽이 FK를 관리할지 JPA에게 알려야 한다.

### 규칙

| 구분 | 설명 |
|------|------|
| **주인 (Owner)** | FK 가진 쪽, `@JoinColumn` 사용, FK 등록/수정 가능 |
| **비주인 (inverse)** | `mappedBy` 사용, **읽기 전용** — 여기서 값을 바꿔도 DB에 반영 안 됨 |

**주인 선택 기준: FK가 있는 곳** (비즈니스 중요도 기준 X)

```
Member.team  → 주인 (MEMBER.TEAM_ID FK를 가짐)
Team.members → 비주인 (mappedBy = "team")
```

### 흔한 실수

```java
// 잘못된 예 — 비주인에만 값 세팅
team.getMembers().add(member); // mappedBy 쪽 → DB 반영 안 됨
// member.setTeam(team) 없음
em.persist(member);
// 결과: MEMBER.TEAM_ID = null ← FK가 저장 안 됨!

// 올바른 예 — 주인에 값 세팅
member.setTeam(team);          // 주인 → DB에 FK 저장됨
team.getMembers().add(member); // 순수 객체 상태 유지용 (선택이지만 권장)
```

---

## 연관관계 편의 메소드

양쪽 모두 값을 세팅하는 것을 잊지 않도록 **주인 쪽 엔티티에** 편의 메소드를 만든다.

```java
@Entity
public class Member {

    @ManyToOne
    @JoinColumn(name = "TEAM_ID")
    private Team team;

    public void changeTeam(Team team) {
        this.team = team;                    // 주인 세팅 (DB 반영)
        team.getMembers().add(this);         // 역방향도 동기화 (순수 객체)
    }
}
```

---

## 양방향 매핑 주의사항

### 무한 루프

양방향 관계에서 `toString()`, Lombok `@ToString`, Jackson JSON 직렬화 시 순환 참조가 발생할 수 있다.

```
Member.toString() → Team.toString() → Member.toString() → ...
```

**해결책**:
- 엔티티를 Controller에서 직접 반환 금지 → DTO 변환 후 반환
- `@JsonIgnore` 사용 (임시방편)
- Lombok `@ToString(exclude = "team")` 사용

### 정리

```
1차: 단방향 매핑만으로 연관관계 설계 완료
2차: JPQL에서 역방향 탐색이 필요할 때 양방향 추가
     → 테이블에 영향 없음, 코드만 추가
```

---

## 면접 Q&A

**Q: 연관관계의 주인이란 무엇이고, 어떻게 결정하나?**  
A: 양방향 연관관계에서 FK를 실제로 관리(등록/수정/삭제)하는 쪽을 주인이라고 한다. 주인은 `@JoinColumn`을 사용하고, 비주인은 `mappedBy`로 주인 필드명을 지정한다. 주인은 비즈니스 중요도가 아닌 **FK의 위치**를 기준으로 결정한다. FK가 있는 엔티티의 필드가 주인이 된다.

**Q: mappedBy 쪽에 값을 세팅하면 어떻게 되나?**  
A: DB에 반영되지 않는다. `mappedBy`가 붙은 필드는 읽기 전용이고, JPA는 이 값을 무시한다. 예를 들어 `team.getMembers().add(member)`만 하고 `member.setTeam(team)`을 하지 않으면 TEAM_ID가 null로 저장된다.

**Q: 양쪽에 모두 값을 세팅해야 하는 이유는?**  
A: JPA 관점에서는 주인 쪽만 세팅하면 DB에 올바르게 반영된다. 하지만 순수 객체 상태에서는 비주인 쪽에 값이 없으면 `team.getMembers()`가 빈 리스트를 반환한다. 특히 같은 트랜잭션 내에서 1차 캐시를 사용하는 경우 역방향 컬렉션에 값이 없어 버그가 발생한다. 따라서 순수 객체 상태까지 고려해 항상 양쪽에 값을 세팅해야 한다. 이를 연관관계 편의 메소드로 캡슐화한다.

**Q: 단방향과 양방향 매핑의 차이는?**  
A: 단방향은 한쪽 엔티티만 상대방을 참조한다. 테이블 구조는 단방향이든 양방향이든 동일하다(FK 하나). 양방향은 역방향 탐색(`team.getMembers()`)이 가능하도록 반대쪽 엔티티에 컬렉션 필드(`@OneToMany(mappedBy)`)를 추가한 것이다. 양방향 추가는 테이블에 영향을 주지 않으므로, 처음에는 단방향만 설계하고 JPQL에서 역방향 탐색이 필요할 때 양방향을 추가하는 방식을 권장한다.

**Q: @OneToMany 단독(일대다 단방향)으로 주인을 Team 쪽에 두면 안 되나?**  
A: 가능하지만 비권장이다. 일대다 단방향에서는 팀을 저장할 때 MEMBER 테이블의 FK도 같이 관리해야 하므로 UPDATE SQL이 추가로 발생한다. 성능 문제와 예상치 못한 쿼리 발생을 피하기 위해 **다대일(@ManyToOne) 쪽을 주인으로** 두는 것이 표준이다.
