# 영속성 관리 - 영속성 컨텍스트

> JPA 기본 | JPA 내부 구조

---

## 전체 흐름 한눈에 보기

```
EntityManagerFactory (애플리케이션 전체 1개)
  └── 요청마다 EntityManager 생성 (커넥션풀에서 DB 커넥션 사용)
        └── 영속성 컨텍스트 (EntityManager 뒤에 존재하는 논리적 공간)
              ├── 1차 캐시 (@Id → Entity 맵)
              ├── 쓰기 지연 SQL 저장소
              └── 스냅샷 (변경 감지용 원본 보관)

엔티티 생명주기
  new → persist() → [영속] → flush() → DB 반영
                         ↓ detach/clear/close
                      [준영속] (더 이상 관리 안 됨)
                         ↓ remove()
                      [삭제] → flush() → DELETE SQL
```

---

## EntityManagerFactory vs EntityManager

| 구분 | EntityManagerFactory | EntityManager |
|------|---------------------|---------------|
| 생성 비용 | 크다 (애플리케이션 시작 시 1번) | 작다 (요청마다 생성/소멸) |
| 스레드 안전 | O (공유 가능) | X (스레드 간 공유 금지) |
| 역할 | EM 생성 공장 | 영속성 컨텍스트 접근 창구 |

**스프링 환경**: 여러 EntityManager → 하나의 PersistenceContext 공유 (N:1)  
**J2SE 환경**: EntityManager 1개 → PersistenceContext 1개 (1:1)

---

## 엔티티 생명주기 4가지

| 상태 | 설명 | 진입 방법 |
|------|------|----------|
| 비영속 (new) | 영속성 컨텍스트와 무관 | `new Member()` |
| 영속 (managed) | 영속성 컨텍스트가 관리 | `em.persist()`, `em.find()`, JPQL |
| 준영속 (detached) | 관리되다가 분리됨 | `em.detach()`, `em.clear()`, `em.close()` |
| 삭제 (removed) | 삭제 예약 상태 | `em.remove()` |

```java
// 비영속
Member member = new Member();
member.setId("member1");

// 영속 (아직 DB에 INSERT 안 됨)
em.persist(member);

// 준영속 (더 이상 dirty checking 안 됨)
em.detach(member);

// 삭제
em.remove(member);
```

---

## 영속성 컨텍스트의 5가지 이점

### 1. 1차 캐시

```java
em.persist(member);                          // 1차 캐시에 저장
Member find1 = em.find(Member.class, "m1"); // 1차 캐시 hit → DB 조회 없음
Member find2 = em.find(Member.class, "m2"); // 1차 캐시 miss → DB 조회 후 1차 캐시 저장
```

- 트랜잭션 범위 캐시 (요청 처리 후 사라짐) → 애플리케이션 전체 2차 캐시와 다름
- DB 조회 횟수 감소 (같은 트랜잭션 내에서만 유효)

### 2. 동일성(Identity) 보장

```java
Member a = em.find(Member.class, "member1");
Member b = em.find(Member.class, "member1");
a == b // true (같은 객체 참조)
```

1차 캐시로 **REPEATABLE READ** 격리 수준을 DB가 아닌 애플리케이션 차원에서 제공.

### 3. 쓰기 지연 (Transactional Write-behind)

```java
transaction.begin();
em.persist(memberA); // INSERT SQL 생성 → 쓰기 지연 저장소에 보관
em.persist(memberB); // INSERT SQL 생성 → 쓰기 지연 저장소에 보관
// 아직 DB에 전송 안 됨

transaction.commit(); // flush → DB 전송 → commit
```

- `persist()` 시점에 SQL 실행 X, **커밋 시점에 일괄 전송**
- 배치 INSERT 최적화(`hibernate.jdbc.batch_size`) 가능

### 4. 변경 감지 (Dirty Checking)

```java
Member memberA = em.find(Member.class, "memberA");
memberA.setUsername("hi"); // setter만 호출
// em.update(member) 없어도 됨!
transaction.commit(); // UPDATE SQL 자동 생성 후 flush
```

**동작 원리**: flush() 시점에 1차 캐시의 스냅샷(영속 시점 원본)과 현재 엔티티를 비교  
→ 변경된 경우 UPDATE SQL 자동 생성 → 쓰기 지연 저장소 등록 → DB 전송

### 5. 지연 로딩 (Lazy Loading)

연관 엔티티를 실제 사용하는 시점에 SQL 실행. (08강 프록시에서 상세 설명)

---

## 플러시 (Flush)

영속성 컨텍스트의 변경 내용을 DB에 **동기화** (컨텍스트를 비우는 게 아님).

### 플러시 발생 시점

| 방법 | 설명 |
|------|------|
| `em.flush()` | 직접 호출 |
| `transaction.commit()` | 자동 호출 |
| JPQL 쿼리 실행 | 자동 호출 |

JPQL 실행 전 자동 flush 이유: `persist()` 후 바로 JPQL로 조회하면 DB에 없어서 결과가 안 나오는 문제를 방지.

### 플러시 모드

```java
em.setFlushMode(FlushModeType.AUTO);   // 기본값: 커밋 또는 쿼리 실행 시
em.setFlushMode(FlushModeType.COMMIT); // 커밋할 때만
```

**핵심**: 플러시는 영속성 컨텍스트를 비우지 않는다. 변경 내용을 DB와 동기화하는 것.

---

## 준영속 상태

영속 상태의 엔티티가 영속성 컨텍스트에서 분리된 상태 → dirty checking, 지연 로딩 불가.

```java
em.detach(entity); // 특정 엔티티만 준영속
em.clear();        // 영속성 컨텍스트 전체 초기화 (1차 캐시 전부 날아감)
em.close();        // 영속성 컨텍스트 종료
```

---

## 면접 Q&A

**Q: 영속성 컨텍스트란 무엇인가?**  
A: 엔티티를 영구 저장하는 논리적 환경이다. EntityManager를 통해 접근하며 내부적으로 1차 캐시, 쓰기 지연 SQL 저장소, 스냅샷을 관리한다. 1차 캐시를 통해 동일성 보장과 반복 가능한 읽기를 애플리케이션 레벨에서 제공하고, 쓰기 지연으로 INSERT SQL을 모아 커밋 시 일괄 전송한다.

**Q: Dirty Checking은 어떻게 동작하나?**  
A: 엔티티가 영속 상태가 될 때 스냅샷(원본 복사본)을 1차 캐시에 저장해둔다. flush() 호출 시 현재 엔티티와 스냅샷을 비교해 달라진 필드가 있으면 UPDATE SQL을 자동 생성한다. 개발자가 `em.update()`를 호출하지 않아도 setter로 값을 바꾸면 트랜잭션 커밋 시 자동으로 UPDATE가 나간다.

**Q: 쓰기 지연의 장점은?**  
A: 여러 INSERT를 한 번에 모아 DB에 전송할 수 있어 네트워크 비용을 줄인다. `hibernate.jdbc.batch_size` 설정으로 JDBC 배치 INSERT를 활용하면 성능을 높일 수 있다. 또한 커밋 직전까지 SQL을 보내지 않으므로 비즈니스 로직 중간에 문제가 생기면 롤백 시 SQL 자체가 전송되지 않는다.

**Q: flush와 commit의 차이는?**  
A: flush는 영속성 컨텍스트의 변경 내용을 DB에 동기화(SQL 전송)하는 것이고, commit은 트랜잭션을 확정하는 것이다. commit 시 flush가 자동 호출되지만, flush만 해서는 트랜잭션이 완료되지 않는다. flush는 영속성 컨텍스트를 비우지 않는다.

**Q: 준영속 상태에서 발생하는 대표적인 문제는?**  
A: LazyInitializationException이다. 트랜잭션이 끝나면 EntityManager가 닫히고 엔티티가 준영속 상태가 되는데, 이 시점에 지연 로딩이 설정된 연관 엔티티에 접근하면 프록시를 초기화할 영속성 컨텍스트가 없어 예외가 발생한다. 스프링에서는 OSIV(Open Session In View) 패턴으로 이 문제를 해결하거나, 트랜잭션 안에서 미리 페치한다.
