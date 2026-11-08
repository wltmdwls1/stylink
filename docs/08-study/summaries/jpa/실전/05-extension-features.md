# 05. 확장 기능 (스프링 데이터 JPA)

## 1. 사용자 정의 리포지토리 구현

JpaRepository 인터페이스를 직접 구현하면 모든 메서드를 구현해야 함 → 커스텀 인터페이스 + Impl 패턴으로 해결.

```java
// 1. 커스텀 인터페이스 정의
public interface MemberRepositoryCustom {
    List<Member> findMemberCustom();
}

// 2. 구현 클래스 — 이름 규칙: 커스텀인터페이스명 + Impl (최신 권장)
@RequiredArgsConstructor
public class MemberRepositoryCustomImpl implements MemberRepositoryCustom {
    private final EntityManager em;

    @Override
    public List<Member> findMemberCustom() {
        return em.createQuery("select m from Member m", Member.class).getResultList();
    }
}

// 3. JpaRepository에 함께 상속
public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {
}
```

- 구현 클래스 이름 규칙: **`사용자정의인터페이스명 + Impl`** (스프링 데이터 2.x 이후 권장)
  - 구버전: `리포지토리인터페이스명 + Impl` (`MemberRepositoryImpl`)
- 실무에서는 QueryDSL, JDBC Template 커스텀 쿼리에 주로 사용
- 복잡한 조회 전용이면 그냥 `@Repository` 클래스로 별도 분리도 가능 (스프링 데이터 JPA와 무관하게 동작)

---

## 2. Auditing

### 스프링 데이터 JPA 방식 (실무 권장)

```java
// 설정 클래스
@EnableJpaAuditing
@SpringBootApplication
public class Application {
    @Bean
    public AuditorAware<String> auditorProvider() {
        // 실무: SecurityContextHolder에서 로그인 유저 ID 추출
        return () -> Optional.of(SecurityContextHolder.getContext()
            .getAuthentication().getName());
    }
}
```

```java
// BaseTimeEntity — 시간만 (대부분의 엔티티에 적용)
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
@Getter
public class BaseTimeEntity {
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    private LocalDateTime lastModifiedDate;
}

// BaseEntity — 시간 + 작성자 (필요한 엔티티만 상속)
public class BaseEntity extends BaseTimeEntity {
    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;
}
```

**실무 팁**: 저장 시점에 등록일·수정일 모두 동일 값으로 세팅됨 → 변경 컬럼만 봐도 마지막 수정자를 알 수 있어 유지보수 편리.

---

## 3. Web 확장 — 페이징과 정렬

Controller에서 `Pageable`을 파라미터로 받으면 끝.

```java
@GetMapping("/members")
public Page<MemberDto> list(Pageable pageable) {
    return memberRepository.findAll(pageable).map(MemberDto::new);
}
```

**요청 파라미터**: `/members?page=0&size=3&sort=id,desc&sort=username,desc`

```yaml
# 글로벌 기본값
spring:
  data:
    web:
      pageable:
        default-page-size: 20
        max-page-size: 2000
```

```java
// 개별 기본값 설정
@GetMapping("/members")
public Page<MemberDto> list(
    @PageableDefault(size = 12, sort = "username", direction = Sort.Direction.DESC)
    Pageable pageable) { ... }

// 여러 Pageable 구분
public String list(
    @Qualifier("member") Pageable memberPageable,
    @Qualifier("order") Pageable orderPageable) { ... }
// → /members?member_page=0&order_page=1
```

**Page는 0부터 시작** — 1부터 시작하려면 직접 `PageRequest` 생성 + 커스텀 응답 DTO 사용 (one-indexed-parameters 옵션은 응답의 pageNumber가 여전히 0 기반이라 한계 있음).

---

## 4. 도메인 클래스 컨버터 (참고용)

```java
// id로 자동으로 엔티티 조회 — 편의 기능이지만 실무에서 잘 안 씀
@GetMapping("/members/{id}")
public String findMember(@PathVariable("id") Member member) {
    return member.getUsername();
}
```

주의: 트랜잭션 없는 범위에서 조회 → **변경해도 DB 반영 안 됨**, 단순 조회만 사용.

---

## stylink 적용 포인트

**Auditing**:
- `BaseTimeEntity` (`createdDate`, `lastModifiedDate`) — 모든 엔티티 상속
- `BaseEntity extends BaseTimeEntity` — createdBy, lastModifiedBy 추가 (관리 엔티티용)
- `AuditorAware`: Spring Security JWT 필터에서 인증된 회원 ID 추출
- stylink는 이미 `BaseEntity` 구현되어 있음 — `@EnableJpaAuditing` + `AuditorAware` 빈 등록 확인

**사용자 정의 리포지토리**:
- Querydsl 도입 시 `OrderRepositoryCustom` + `OrderRepositoryCustomImpl` 패턴으로 복잡한 동적 쿼리 분리
- 조회 전용 복잡 쿼리는 `OrderQueryRepository` 같은 별도 클래스로 분리 (JpaRepository 상속 불필요)

**Web 페이징**:
- Controller에서 `Pageable` 파라미터 직접 수신
- 응답은 반드시 `Page<DTO>` — `page.map(dto -> new XxxDto(dto))` 활용
- 글로벌 기본 페이지 사이즈 설정: `spring.data.web.pageable.default-page-size: 20`
