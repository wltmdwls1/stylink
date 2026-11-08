# 07. 나머지 기능들 (스프링 데이터 JPA)

## 요약 — 실무 결론부터

| 기능 | 실무 사용 여부 |
|------|--------------|
| Specifications | 사용 안 함 — JPA Criteria 기반, 코드 복잡. **QueryDSL 사용** |
| Query By Example | 사용 안 함 — LEFT JOIN 불가, 매칭 조건 단순. **QueryDSL 사용** |
| Projections | 단순 단일 엔티티 조회에만 사용. 복잡하면 **QueryDSL 사용** |
| 네이티브 쿼리 | 최후의 수단. DTO 변환은 **JdbcTemplate/MyBatis 권장** |

---

## 1. Specifications (명세) — 실무 비추천

JPA Criteria 기반 동적 조건 조합. `and()`, `or()`, `not()` 조합 가능하나 코드가 복잡.

```java
// 인터페이스 상속
public interface MemberRepository extends JpaRepository<Member, Long>,
    JpaSpecificationExecutor<Member> {}

// 사용
Specification<Member> spec = MemberSpec.username("m1").and(MemberSpec.teamName("teamA"));
List<Member> result = memberRepository.findAll(spec);
```

**결론**: 실무에서 JPA Criteria는 거의 안 씀. QueryDSL 사용.

---

## 2. Query By Example — 실무 비추천

엔티티 객체 자체를 쿼리 조건(Probe)으로 사용.

```java
Member member = new Member("m1");
member.setTeam(new Team("teamA"));

ExampleMatcher matcher = ExampleMatcher.matching().withIgnorePaths("age");
Example<Member> example = Example.of(member, matcher);
List<Member> result = memberRepository.findAll(example);
```

**한계**:
- INNER JOIN만 가능 (LEFT JOIN 불가)
- 중첩 OR 조건 불가 (`a = ?0 or (a = ?1 and b = ?2)` 형태)
- 문자는 starts/contains/ends/regex, 나머지는 `=` 매칭만

**결론**: 실무에서 QueryDSL 사용.

---

## 3. Projections — 단순 조회 시 유용

전체 엔티티 대신 필요한 필드만 선택 조회.

```java
// 인터페이스 기반 Closed Projection — SELECT 절 최적화 O
public interface UsernameOnly {
    String getUsername();
}
List<UsernameOnly> findProjectionsByUsername(String username);

// 클래스 기반 Projection — 생성자 파라미터 이름으로 매칭
public class UsernameOnlyDto {
    private final String username;
    public UsernameOnlyDto(String username) { this.username = username; }
}

// 동적 Projection — 타입 파라미터로 유연하게
<T> List<T> findProjectionsByUsername(String username, Class<T> type);
```

**주의**:
- **root 엔티티 필드만**: SELECT 최적화 O
- **연관 엔티티 필드 포함**: LEFT OUTER JOIN + 모든 필드 조회 → 최적화 X
- Open Projection (`@Value SpEL`): 모든 필드 조회 후 애플리케이션에서 계산 → 최적화 X

**결론**: 단순 단일 엔티티 필드 조회 시 유용. 복잡해지면 QueryDSL.

---

## 4. 네이티브 쿼리

```java
// 기본 사용
@Query(value = "select * from member where username = ?", nativeQuery = true)
Member findByNativeQuery(String username);

// 페이징 + Projection 조합 (실무에서 가장 실용적인 네이티브 쿼리 패턴)
@Query(value = "SELECT m.member_id as id, m.username, t.name as teamName " +
               "FROM member m left join team t ON m.team_id = t.team_id",
       countQuery = "SELECT count(*) from member",
       nativeQuery = true)
Page<MemberProjection> findByNativeProjection(Pageable pageable);
```

**제약**:
- Sort 파라미터 정렬 신뢰 불가
- 애플리케이션 로딩 시점 문법 확인 불가 (JPQL과 달리)
- 동적 쿼리 불가

**DTO 변환**: 네이티브 SQL → DTO 직접 변환은 복잡함 → **JdbcTemplate or MyBatis 권장**

---

## stylink 적용 포인트

- Specifications, Query By Example: 사용하지 않음 — Querydsl로 대체
- Projections: 관리자 화면 목록 조회처럼 단순히 id + 이름만 필요할 때 인터페이스 Projection 활용 고려
- 네이티브 쿼리: 재고 상태 집계나 통계 쿼리 등 JPQL로 표현 불가한 경우만 사용, DTO 변환 필요 시 JdbcTemplate 활용
- **동적 검색 (상품 목록 필터링, 예약 목록 필터링)**: 반드시 Querydsl — 다음 강의 시리즈로 학습 예정
