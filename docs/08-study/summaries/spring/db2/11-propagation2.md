# 스프링 트랜잭션 전파2 - 활용

> 스프링 DB 2편 | DB2

---

## 전체 흐름 한눈에 보기

```
요구사항: 회원 가입 시 DB 로그도 함께 저장
  문제: 로그 저장 실패 시 회원 가입도 롤백되어야 하는가? 아닌가?

케이스별 정리:

[케이스1] 서비스 트랜잭션 OFF
  MemberRepository @TX:ON, LogRepository @TX:ON (각각 독립)
  → 로그 실패 시 로그만 롤백, 회원은 커밋 → 데이터 정합성 문제

[케이스2] 단일 트랜잭션 (가장 단순)
  MemberService @TX:ON만, 나머지 @TX:OFF
  → 모두 하나의 트랜잭션 → 로그 실패 시 회원까지 롤백

[케이스3] 모두 ON (전파 커밋/롤백)
  MemberService @TX:ON, MemberRepository @TX:ON, LogRepository @TX:ON
  → 내부가 외부에 참여 → 로그 실패 시 rollbackOnly → 전체 롤백

[케이스4] 복구 시도 실패 (흔한 실수)
  서비스에서 예외 catch → 정상 흐름 변환 → 커밋 시도
  → rollbackOnly 이미 표시됨 → UnexpectedRollbackException

[케이스5] REQUIRES_NEW 복구 성공
  LogRepository @Transactional(REQUIRES_NEW)
  → 별도 물리 트랜잭션 → 로그 롤백, 회원 커밋 성공
```

---

## 예제 구조

```java
// Member 엔티티 + MemberRepository (@Transactional save)
// Log 엔티티 + LogRepository (@Transactional save — "로그예외" 포함 시 RuntimeException)

@Service
public class MemberService {
    public void joinV1(String username) {
        memberRepository.save(new Member(username));
        logRepository.save(new Log(username));  // 예외 시 위로 전파
    }

    public void joinV2(String username) {
        memberRepository.save(new Member(username));
        try {
            logRepository.save(new Log(username));
        } catch (RuntimeException e) {
            log.info("log 저장 실패, 정상 흐름 변환");  // 예외 복구 시도
        }
    }
}
```

---

## 케이스1 — 서비스 트랜잭션 OFF (각자 독립)

```
MemberService @TX:OFF
MemberRepository @TX:ON → 커밋 → Member 저장 완료
LogRepository @TX:ON    → 예외 → 롤백 → Log 없음

결과: Member는 있고, Log는 없음 → 데이터 정합성 문제!
```

---

## 케이스2 — 단일 트랜잭션 (가장 단순하고 권장)

```java
// 서비스에만 @Transactional, 리포지토리에서는 제거
@Transactional
public void joinV1(String username) { ... }

// 같은 쓰레드 → 트랜잭션 동기화 매니저 → 같은 커넥션 반환
// → MemberRepository, LogRepository 모두 같은 트랜잭션 사용
```

```
트랜잭션 전파, rollbackOnly, 신규 트랜잭션 같은 복잡한 개념 불필요.
다만 리포지토리를 단독으로 호출하는 클라이언트도 트랜잭션이 필요하다면
이 방법은 사용 불가 → 전파가 필요한 이유
```

---

## 케이스3 — 전파 커밋/롤백 (모두 ON)

```
[전파 커밋] outerTxOn_success
  MemberService (신규 트랜잭션) 시작 → conn0
  MemberRepository.save() → 기존에 참여 (isNewTransaction=false) → 논리 커밋만
  LogRepository.save() → 기존에 참여 → 논리 커밋만
  MemberService 종료 → 신규 트랜잭션 → 물리 커밋 → 전체 저장

[전파 롤백] outerTxOn_fail (로그예외 전달)
  LogRepository.save() → 예외 발생 → 트랜잭션 AOP → rollbackOnly=true 표시
  예외가 MemberService까지 전파
  MemberService 트랜잭션 AOP → 롤백 → Member, Log 모두 롤백
```

---

## 케이스4 — 복구 시도 실패 (흔한 실수)

```java
// joinV2: 예외를 catch해서 정상 흐름으로 변환하면 될 것 같지만...
@Test
void recoverException_fail() {
    // MemberService @TX:ON, MemberRepository @TX:ON, LogRepository @TX:ON
    assertThatThrownBy(() -> memberService.joinV2("로그예외_..."))
            .isInstanceOf(UnexpectedRollbackException.class);  // 여기서 예외 발생!
    assertTrue(memberRepository.find(username).isEmpty());  // Member도 롤백됨
}
```

```
왜 실패하는가?

LogRepository 예외 발생
→ 트랜잭션 AOP: 신규 아님 → 물리 롤백 안 함 → rollbackOnly=true 표시
→ 예외를 MemberService로 던짐

MemberService catch(e)로 예외 복구 → 정상 흐름으로 리턴

MemberService 트랜잭션 AOP: 정상 리턴 → 커밋 시도
→ 신규 트랜잭션 → rollbackOnly 확인 → rollbackOnly=true 발견
→ 물리 롤백 수행 + UnexpectedRollbackException 던짐

결론: 예외를 잡아도 rollbackOnly 플래그는 이미 표시됨 → 커밋 불가
```

---

## 케이스5 — REQUIRES_NEW 복구 성공

```java
// LogRepository에 REQUIRES_NEW 적용
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void save(Log logMessage) { ... }
```

```java
@Test
void recoverException_success() {
    // MemberService @TX:ON, MemberRepository @TX:ON, LogRepository @TX(REQUIRES_NEW)
    memberService.joinV2("로그예외_...");  // 예외 없이 정상 완료

    assertTrue(memberRepository.find(username).isPresent());  // Member 저장 완료
    assertTrue(logRepository.find(username).isEmpty());       // Log만 롤백
}
```

```
흐름:
  MemberService 시작 → 신규 트랜잭션(conn0)
  MemberRepository.save() → 참여 (conn0)
  LogRepository.save() → REQUIRES_NEW → conn0 보류, 신규 트랜잭션(conn1)

  LogRepository 예외 발생
  → 신규 트랜잭션(conn1) → 물리 롤백 → rollbackOnly 표시 없음, 그냥 끝
  → 예외를 MemberService로 던짐

  MemberService catch(e) → 예외 복구 → 정상 리턴
  MemberService 트랜잭션 AOP → 커밋 시도 → rollbackOnly 없음 → conn0 물리 커밋

  결과: Member 커밋(conn0), Log 롤백(conn1) — 각각 독립!
```

---

## REQUIRES_NEW 주의사항

```
REQUIRES_NEW = DB 커넥션 동시에 2개 사용
  HTTP 요청 1개당 conn0 + conn1 동시 점유
  → 커넥션 풀 고갈 위험 → 성능 이슈

대안: 구조 변경 (순차 호출)
  MemberFacade가 MemberService + LogService를 순차적으로 호출
  → 각각 별도의 트랜잭션으로 순차 실행 → 커넥션 1개씩만 사용
  (구조가 복잡해지는 단점, 하지만 커넥션 효율적)
```

---

## 정리 — 케이스별 비교

| 케이스 | 구조 | 로그 예외 시 결과 |
|--------|------|----------------|
| 서비스 TX OFF | 각자 독립 | Member 저장, Log 롤백 (정합성 문제) |
| 단일 TX | 서비스만 ON | 전체 롤백 |
| 전파 (모두 ON) | joinV1 사용 | 전체 롤백 |
| 복구 REQUIRED | joinV2 + 예외 catch | UnexpectedRollbackException (실패) |
| 복구 REQUIRES_NEW | LogRepository REQUIRES_NEW | Member 저장, Log 롤백 (성공) |

---

## 면접 Q&A

**Q: 트랜잭션 전파에서 내부 트랜잭션의 예외를 서비스에서 catch로 잡으면 복구가 되는가?**
A: 안 된다. REQUIRED(기본) 전파에서 내부 트랜잭션은 외부 트랜잭션에 참여하기 때문에, 내부에서 예외가 발생하면 트랜잭션 AOP가 `rollbackOnly=true`를 트랜잭션 동기화 매니저에 표시하고 예외를 밖으로 던진다. 서비스에서 그 예외를 catch해서 정상 흐름으로 복구해도, 외부 트랜잭션이 커밋을 시도할 때 `rollbackOnly` 플래그를 확인하고 물리 롤백을 수행하며 `UnexpectedRollbackException`을 던진다. 이 방식으로 복구하려면 REQUIRES_NEW로 내부 트랜잭션을 물리적으로 분리해야 한다.

**Q: REQUIRES_NEW의 실무 사용 시 주의사항은?**
A: REQUIRES_NEW는 기존 트랜잭션(conn0)을 보류하고 새 커넥션(conn1)을 추가로 획득하므로, HTTP 요청 하나에 DB 커넥션 2개를 동시에 점유한다. 트래픽이 많은 서비스에서는 커넥션 풀이 고갈될 수 있다. 대안으로 REQUIRES_NEW 없이 구조를 변경하는 방법이 있다: 예를 들어 파사드(Facade) 클래스에서 MemberService와 LogService를 순차적으로 호출하면 각각 별도 트랜잭션으로 처리하되 커넥션은 하나씩만 사용한다. 구조가 다소 복잡해지지만 성능이 중요한 곳에서는 이 방법을 고려해야 한다.

**Q: 회원과 로그를 하나의 트랜잭션으로 묶는 가장 단순한 방법은?**
A: 서비스 계층에만 `@Transactional`을 붙이고 리포지토리의 `@Transactional`을 제거하는 것이다. 같은 쓰레드에서 실행되면 트랜잭션 동기화 매니저가 동일한 커넥션을 반환하므로, 서비스가 호출하는 모든 리포지토리가 같은 트랜잭션 범위 안에 들어간다. 논리 트랜잭션, rollbackOnly, 신규 트랜잭션 여부 같은 복잡한 개념이 필요 없어서 가장 단순하다. 단, 리포지토리를 직접 호출하는 다른 클라이언트도 트랜잭션이 필요하다면 이 방법은 사용할 수 없고 전파를 써야 한다.
