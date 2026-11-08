# 05. 실무 활용 - 순수 JPA와 Querydsl

## 1. JPAQueryFactory 설정 패턴

```java
@Repository
public class MemberJpaRepository {
    private final EntityManager em;
    private final JPAQueryFactory queryFactory;

    public MemberJpaRepository(EntityManager em) {
        this.em = em;
        this.queryFactory = new JPAQueryFactory(em);
    }
}

// 또는 스프링 빈으로 등록
@Bean
JPAQueryFactory jpaQueryFactory(EntityManager em) {
    return new JPAQueryFactory(em);
}
// → @RequiredArgsConstructor로 주입 가능
```

> 동시성 문제 없음: 스프링이 주입하는 EntityManager는 프록시 — 실제 사용 시점에 트랜잭션 단위로 실제 영속성 컨텍스트 할당

---

## 2. 검색 조건 DTO 패턴

```java
@Data
public class MemberSearchCondition {
    private String username;
    private String teamName;
    private Integer ageGoe;  // Integer (null 체크 가능)
    private Integer ageLoe;
}
```

**결과 DTO (`@QueryProjection` 활용)**:
```java
@Data
public class MemberTeamDto {
    private Long memberId;
    private String username;
    private int age;
    private Long teamId;
    private String teamName;

    @QueryProjection
    public MemberTeamDto(Long memberId, String username, int age,
                         Long teamId, String teamName) { ... }
}
// → compileQuerydsl 실행 → QMemberTeamDto 생성
```

---

## 3. 동적 쿼리 — Builder vs Where 파라미터 비교

### BooleanBuilder 방식

```java
public List<MemberTeamDto> searchByBuilder(MemberSearchCondition condition) {
    BooleanBuilder builder = new BooleanBuilder();
    if (hasText(condition.getUsername()))  builder.and(member.username.eq(condition.getUsername()));
    if (hasText(condition.getTeamName())) builder.and(team.name.eq(condition.getTeamName()));
    if (condition.getAgeGoe() != null)    builder.and(member.age.goe(condition.getAgeGoe()));
    if (condition.getAgeLoe() != null)    builder.and(member.age.loe(condition.getAgeLoe()));

    return queryFactory
        .select(new QMemberTeamDto(member.id, member.username, member.age, team.id, team.name))
        .from(member)
        .leftJoin(member.team, team)
        .where(builder)
        .fetch();
}
```

### Where 파라미터 방식 (실무 권장)

```java
public List<MemberTeamDto> search(MemberSearchCondition condition) {
    return queryFactory
        .select(new QMemberTeamDto(member.id, member.username, member.age, team.id, team.name))
        .from(member)
        .leftJoin(member.team, team)
        .where(
            usernameEq(condition.getUsername()),
            teamNameEq(condition.getTeamName()),
            ageGoe(condition.getAgeGoe()),
            ageLoe(condition.getAgeLoe())
        )
        .fetch();
}

// 조건 메서드 — null 반환 시 자동 무시, 다른 쿼리에서도 재사용 가능
private BooleanExpression usernameEq(String username) {
    return isEmpty(username) ? null : member.username.eq(username);
}
private BooleanExpression teamNameEq(String teamName) {
    return isEmpty(teamName) ? null : team.name.eq(teamName);
}
private BooleanExpression ageGoe(Integer ageGoe) {
    return ageGoe == null ? null : member.age.goe(ageGoe);
}
private BooleanExpression ageLoe(Integer ageLoe) {
    return ageLoe == null ? null : member.age.loe(ageLoe);
}
```

**Where 파라미터 방식 장점**: 조건 메서드를 엔티티 조회 쿼리(`List<Member>`)에서도 동일하게 재사용 가능

---

## 4. 프로파일 분리 (테스트 격리)

```yaml
# src/main/resources/application.yml
spring.profiles.active: local

# src/test/resources/application.yml
spring.profiles.active: test
```

```java
@Profile("local")
@Component
public class InitMember {
    @PostConstruct
    public void init() { initMemberService.init(); }
    // local 환경에서만 샘플 데이터 100건 삽입
}
```

---

## 5. 조회 API 컨트롤러 패턴

```java
@RestController
@RequiredArgsConstructor
public class MemberController {
    private final MemberJpaRepository memberJpaRepository;

    @GetMapping("/v1/members")
    public List<MemberTeamDto> searchMemberV1(MemberSearchCondition condition) {
        return memberJpaRepository.search(condition);
    }
}
// GET /v1/members?teamName=teamB&ageGoe=31&ageLoe=35
```

---

## stylink 적용 패턴

**검색 조건 DTO 설계 예시**:
```java
@Data
public class ItemSearchCondition {
    private String name;              // 상품명 (부분 검색)
    private ItemStatus status;        // AVAILABLE / RESERVED / IN_TRANSIT / SOLD
    private Long categoryId;
    private Integer priceGoe;
    private Integer priceLoe;
}
```

**ItemQueryRepository 구현 패턴**:
```java
@Repository
@RequiredArgsConstructor
public class ItemQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<ItemDto> search(ItemSearchCondition cond) {
        return queryFactory
            .select(new QItemDto(...))
            .from(item)
            .where(
                nameContains(cond.getName()),
                statusEq(cond.getStatus()),
                categoryEq(cond.getCategoryId()),
                priceGoe(cond.getPriceGoe()),
                priceLoe(cond.getPriceLoe())
            )
            .fetch();
    }

    private BooleanExpression nameContains(String name) {
        return hasText(name) ? item.name.contains(name) : null;
    }
    private BooleanExpression statusEq(ItemStatus status) {
        return status != null ? item.status.eq(status) : null;
    }
}
```

- `JPAQueryFactory`는 `@Bean`으로 등록해 모든 Repository에서 공유
- 복잡한 조회는 `ItemQueryRepository` (별도 클래스), 기본 CRUD는 `JpaRepository`
- 조건 메서드(`nameContains`, `statusEq` 등)는 여러 쿼리에서 재사용
