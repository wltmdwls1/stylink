# 02. Querydsl 예제 도메인 모델

> 스프링 데이터 JPA 강의와 동일한 Member/Team 양방향 연관관계 모델 사용.

## 엔티티 구조

```
Member (N) ──── (1) Team
- id (PK)         - id (PK)
- username        - name
- age             - List<Member> members
- Team team (FK)
```

## 핵심 엔티티 패턴

```java
@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "username", "age"})   // 연관관계 필드 제외 (순환참조 방지)
public class Member {
    @Id @GeneratedValue @Column(name = "member_id")
    private Long id;
    private String username;
    private int age;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    public void changeTeam(Team team) {     // 연관관계 편의 메서드
        this.team = team;
        team.getMembers().add(this);        // 양방향 동기화
    }
}

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "name"})
public class Team {
    @Id @GeneratedValue @Column(name = "team_id")
    private Long id;
    private String name;

    @OneToMany(mappedBy = "team")
    private List<Member> members = new ArrayList<>();
}
```

## 테스트 데이터 셋업 패턴

```java
// Querydsl 전 테스트에서 공통으로 사용하는 기본 데이터
Team teamA = new Team("teamA"); Team teamB = new Team("teamB");
em.persist(teamA); em.persist(teamB);
em.persist(new Member("member1", 10, teamA));
em.persist(new Member("member2", 20, teamA));
em.persist(new Member("member3", 30, teamB));
em.persist(new Member("member4", 40, teamB));
em.flush(); em.clear();    // 영속성 컨텍스트 초기화 후 DB에서 조회
```

## 설계 원칙 (Querydsl 전반에 적용)

- `@NoArgsConstructor(PROTECTED)`: JPA 스팩 요구사항, 외부 직접 생성 차단
- `@ToString`에서 연관관계 필드 제외: `toString()` 호출 시 LAZY 로딩 + 순환참조 방지
- `changeTeam()`: 연관관계 주인(Member)이 양쪽 모두 동기화 — 주인 아닌 쪽(Team.members)은 읽기 전용
- 모든 연관관계는 `FetchType.LAZY` 기본 설정
