# 03. 공통 인터페이스 기능 (스프링 데이터 JPA)

## 핵심 요약

```
순수 JPA 리포지토리 (직접 구현) → 스프링 데이터 JPA JpaRepository<T, ID> 상속으로 대체
인터페이스 선언만 해도 구현체를 스프링이 자동 생성 (프록시)
```

---

## 1. 순수 JPA 리포지토리 패턴 (비교용)

```java
@Repository
public class MemberJpaRepository {
    @PersistenceContext
    private EntityManager em;

    public Member save(Member member) { em.persist(member); return member; }
    public void delete(Member member) { em.remove(member); }
    public List<Member> findAll() { return em.createQuery("select m from Member m", Member.class).getResultList(); }
    public Optional<Member> findById(Long id) { return Optional.ofNullable(em.find(Member.class, id)); }
    public long count() { return em.createQuery("select count(m) from Member m", Long.class).getSingleResult(); }
}
```

엔티티마다 이 코드를 반복 작성 → 스프링 데이터 JPA로 제거.

---

## 2. 스프링 데이터 JPA — 인터페이스만 선언

```java
public interface MemberRepository extends JpaRepository<Member, Long> {
    // 끝. 구현체는 스프링이 프록시로 자동 생성
}
```

- `@Repository` 생략 가능 (스프링 데이터 JPA가 자동 컴포넌트 스캔 + JPA 예외 → 스프링 예외 변환)
- 실제 클래스: `com.sun.proxy.$ProxyXXX`

---

## 3. JpaRepository 인터페이스 계층

```
Repository (스프링 데이터)
  └─ CrudRepository: save, findOne, exists, count, delete...
       └─ PagingAndSortingRepository: findAll(Sort), findAll(Pageable)
            └─ JpaRepository (스프링 데이터 JPA): flush, saveAndFlush, deleteInBatch, ...
```

---

## 4. 주요 메서드

| 메서드 | 설명 |
|--------|------|
| `save(S)` | 신규 → persist, 기존 → merge (id 없으면 신규) |
| `delete(T)` | `EntityManager.remove()` |
| `findById(ID)` | `EntityManager.find()` → `Optional<T>` |
| `getReferenceById(ID)` | `EntityManager.getReference()` — 프록시 조회 (`getOne` deprecated) |
| `findAll(Pageable)` | 페이징 + 정렬 |
| `count()` | 전체 카운트 |
| `existsById(ID)` | 존재 여부 (`exists` deprecated) |

---

## 5. 설정

```java
// 스프링 부트 사용 시 생략 가능 (@SpringBootApplication이 패키지 자동 스캔)
@Configuration
@EnableJpaRepositories(basePackages = "study.datajpa.repository")
public class AppConfig {}
```

---

## stylink 적용 포인트

- 모든 Repository를 `JpaRepository<엔티티, Long>` 상속으로 정의
- `@Repository` 어노테이션 불필요 (이미 스프링 데이터 JPA가 처리)
- 기본 CRUD는 JpaRepository 제공 메서드만 사용, 커스텀 쿼리는 다음 챕터(쿼리 메소드)로 추가
- `save()` 동작 주의: id가 있으면 merge → **신규 엔티티에 id를 직접 세팅하면 merge가 호출될 수 있음** (Persistable 구현으로 해결 — 확장 기능 챕터에서 상세)
