# 04. 회원 도메인 개발

## 전체 흐름 한눈에 보기

```
Repository (EntityManager 직접)
  → Service (@Transactional 처리)
  → Test (@SpringBootTest + @Transactional → 롤백)
```

---

## 1. Repository 패턴

```java
@Repository
@RequiredArgsConstructor
public class MemberRepository {

    private final EntityManager em;  // 생성자 주입 (스프링 데이터 JPA 지원)

    public void save(Member member) { em.persist(member); }
    public Member findOne(Long id) { return em.find(Member.class, id); }
    public List<Member> findAll() {
        return em.createQuery("select m from Member m", Member.class).getResultList();
    }
    public List<Member> findByName(String name) {
        return em.createQuery("select m from Member m where m.name = :name", Member.class)
                 .setParameter("name", name).getResultList();
    }
}
```

- `@Repository`: 스프링 빈 등록 + **JPA 예외 → 스프링 예외로 자동 변환**
- `@PersistenceContext` 대신 `@RequiredArgsConstructor` + `final EntityManager em`으로 생성자 주입 가능 (스프링 데이터 JPA가 지원)

---

## 2. Service 패턴

```java
@Service
@Transactional(readOnly = true)  // 클래스 레벨: 조회 기본
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional  // 쓰기 메서드만 오버라이드
    public Long join(Member member) {
        validateDuplicateMember(member);
        memberRepository.save(member);
        return member.getId();
    }

    private void validateDuplicateMember(Member member) {
        List<Member> findMembers = memberRepository.findByName(member.getName());
        if (!findMembers.isEmpty()) {
            throw new IllegalStateException("이미 존재하는 회원입니다.");
        }
    }

    public List<Member> findMembers() { return memberRepository.findAll(); }
    public Member findOne(Long memberId) { return memberRepository.findOne(memberId); }
}
```

### @Transactional(readOnly = true) 전략

클래스 전체에 `readOnly = true` → 조회 메서드 성능 최적화 (플러시 생략).  
쓰기 메서드(`join` 등)에만 `@Transactional`을 별도 선언해서 오버라이드.

### 생성자 주입 권장 이유

| 방식 | 단점 |
|------|------|
| 필드 주입 (`@Autowired`) | 테스트 시 Mock 주입 어려움 |
| 생성자 주입 | `final` 키워드로 컴파일 시점 오류 검출 가능 |

**실무 패턴**: `@RequiredArgsConstructor` + `private final` 조합.

### 중복 검증 주의점

`validateDuplicateMember`는 애플리케이션 레벨 검증. **멀티스레드 환경에서 동시 요청 시 통과 가능** → **DB 유니크 제약조건 필수로 함께 적용**.

---

## 3. 테스트 패턴

```java
@SpringBootTest
@Transactional  // 테스트 후 자동 롤백
public class MemberServiceTest {

    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;

    @Test
    public void 회원가입() {
        // Given
        Member member = new Member();
        member.setName("kim");
        // When
        Long saveId = memberService.join(member);
        // Then
        assertEquals(member, memberRepository.findOne(saveId));
    }

    @Test
    public void 중복_회원_예외() {
        Member member1 = new Member(); member1.setName("kim");
        Member member2 = new Member(); member2.setName("kim");
        memberService.join(member1);
        // JUnit5: assertThrows 사용
        assertThrows(IllegalStateException.class, () -> memberService.join(member2));
    }
}
```

### 테스트 격리 설정

`test/resources/application.yml` 설정 시: 스프링 부트는 datasource 없으면 **자동으로 인메모리 DB(H2) + ddl-auto: create-drop** 적용 → 별도 설정 불필요.

| 어노테이션 | 역할 |
|-----------|------|
| `@SpringBootTest` | 스프링 컨텍스트 전체 로드 |
| `@Transactional` (테스트) | 테스트 종료 시 롤백 → DB 오염 없음 |

---

## stylink 적용 포인트

**강의 권장 방식**: 클래스 레벨 `readOnly=true`, 쓰기만 `@Transactional` 오버라이드, 생성자 주입, DB 유니크 제약.

**stylink 적용**:
- 모든 Service 클래스에 `@Transactional(readOnly = true)` 기본, 쓰기 메서드만 `@Transactional` 별도 선언
- `@RequiredArgsConstructor` + `private final` 생성자 주입 패턴 적용
- 비즈니스 검증(중복 체크 등)은 Service에서 하되, **DB 유니크 제약조건도 반드시 병행** (e.g., 이메일 unique)
- 테스트: `test/resources/application.yml` 분리 → 인메모리 DB로 단위 테스트
- Given/When/Then 패턴으로 테스트 작성 (비즈니스 로직 단위 테스트는 사용자 담당)
