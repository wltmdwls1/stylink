# 02. 예제 도메인 모델 (스프링 데이터 JPA)

## 도메인 구조

```
Member (N) ←→ (1) Team
- Member.team: 연관관계 주인 (FK 보유)
- Team.members: 읽기 전용 (mappedBy)
```

## 핵심 엔티티 패턴

```java
@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "username", "age"})  // 연관관계 필드 제외
public class Member {
    @Id @GeneratedValue
    @Column(name = "member_id")
    private Long id;
    private String username;
    private int age;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    // 연관관계 편의 메서드 — 양방향 동기화
    public void changeTeam(Team team) {
        this.team = team;
        team.getMembers().add(this);
    }
}
```

## 롬복 사용 원칙

| 어노테이션 | 원칙 |
|-----------|------|
| `@Setter` | 실무에서 가급적 사용 금지 |
| `@NoArgsConstructor(PROTECTED)` | JPA 스펙상 기본 생성자 필요, PROTECTED로 외부 직접 호출 차단 |
| `@ToString` | **연관관계 필드 제외** (무한루프 방지) |

## 테스트 패턴 — flush + clear

```java
em.persist(member1); // ...
em.flush();   // DB에 SQL 강제 실행
em.clear();   // 영속성 컨텍스트 초기화 → 이후 조회 시 DB에서 새로 로딩
List<Member> members = em.createQuery("select m from Member m", Member.class).getResultList();
```

`flush() + clear()` 조합: 실제 DB 조회 및 지연 로딩 동작 검증 시 필수 패턴.

## stylink 적용 포인트

- 모든 연관관계 `FetchType.LAZY` 기본
- `@ToString`에서 연관관계 필드 항상 제외
- 연관관계 편의 메서드(`changeTeam` 패턴)로 양방향 관계 항상 양쪽 동기화
- `@NoArgsConstructor(PROTECTED)` 패턴으로 무분별한 기본 생성자 호출 차단
