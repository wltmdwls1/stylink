# 스프링 트랜잭션 전파1 - 기본

> 스프링 DB 2편 | DB2

---

## 전체 흐름 한눈에 보기

```
트랜잭션 전파 핵심 원칙 (REQUIRED 기준)

외부 트랜잭션 수행 중 → 내부 트랜잭션 시작
→ 내부가 외부에 "참여" (하나의 물리 트랜잭션으로 묶임)

논리 트랜잭션: 트랜잭션 매니저를 통해 사용하는 단위
물리 트랜잭션: 실제 DB 커넥션에 적용되는 단위

원칙:
  모든 논리 트랜잭션이 커밋되어야 물리 트랜잭션이 커밋
  하나의 논리 트랜잭션이라도 롤백되면 물리 트랜잭션은 롤백

핵심 동작:
  외부(신규): isNewTransaction=true  → 물리 커밋/롤백 실행
  내부(참여): isNewTransaction=false → 물리 커밋/롤백 실행 안 함
```

---

## 전파 기본 — inner_commit

```java
void inner_commit() {
    TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());
    // outer.isNewTransaction() = true (신규 트랜잭션)

    TransactionStatus inner = txManager.getTransaction(new DefaultTransactionAttribute());
    // "Participating in existing transaction" 로그 출력
    // inner.isNewTransaction() = false (기존 트랜잭션에 참여)

    txManager.commit(inner);  // 물리 커밋 없음 (내부라서)
    txManager.commit(outer);  // 실제 DB 커밋 발생
}
```

```
요청 흐름:
  외부 txManager.getTransaction() → conn0 획득 → setAutoCommit(false) → 동기화 매니저에 보관
  내부 txManager.getTransaction() → 동기화 매니저에서 기존 트랜잭션 확인 → 참여 (새 커넥션 없음)

응답 흐름:
  내부 commit() → isNewTransaction=false → 물리 커밋 호출 안 함 (중요!)
  외부 commit() → isNewTransaction=true  → conn0에 물리 커밋
```

> 내부 트랜잭션이 물리 커밋을 하면 트랜잭션이 끝나버려서 외부 트랜잭션을 이어갈 수 없다.  
> 따라서 **외부(처음 시작한) 트랜잭션만 물리 트랜잭션을 관리**한다.

---

## 외부 롤백 — outer_rollback

```java
void outer_rollback() {
    TransactionStatus outer = txManager.getTransaction(...);
    TransactionStatus inner = txManager.getTransaction(...);  // 참여
    txManager.commit(inner);     // 물리 커밋 없음
    txManager.rollback(outer);   // 물리 롤백 발생 → 내부 데이터까지 모두 롤백
}
```

논리 트랜잭션이 하나라도 롤백 → 전체 물리 트랜잭션 롤백.  
내부가 커밋했어도 외부가 롤백하면 내부에서 저장한 데이터도 모두 롤백된다.

---

## 내부 롤백 — inner_rollback + UnexpectedRollbackException

```java
void inner_rollback() {
    TransactionStatus outer = txManager.getTransaction(...);
    TransactionStatus inner = txManager.getTransaction(...);  // 참여

    txManager.rollback(inner);  // 물리 롤백 없음 → rollbackOnly=true 마크만 표시
    // 로그: "Participating transaction failed - marking existing transaction as rollback-only"

    assertThatThrownBy(() -> txManager.commit(outer))
            .isInstanceOf(UnexpectedRollbackException.class);
    // 커밋 호출했지만 rollbackOnly 마크 확인 → 물리 롤백 → UnexpectedRollbackException 발생
}
```

```
내부 롤백 흐름:
  내부 rollback() → 신규 아님 → 물리 롤백 안 함
  → 대신 TransactionSynchronizationManager에 rollbackOnly=true 표시

외부 commit() 흐름:
  신규 트랜잭션 → 물리 커밋 호출 전 rollbackOnly 확인
  → rollbackOnly=true 발견 → 물리 롤백 수행
  → UnexpectedRollbackException 발생 (개발자에게 명확히 알림)
```

> 커밋을 기대했는데 롤백이 되면 심각한 문제 → 스프링은 `UnexpectedRollbackException`으로 명확히 알려준다.

---

## REQUIRES_NEW — 물리 트랜잭션 분리

```java
void inner_rollback_requires_new() {
    TransactionStatus outer = txManager.getTransaction(new DefaultTransactionAttribute());
    // outer: conn0, isNewTransaction=true

    DefaultTransactionAttribute definition = new DefaultTransactionAttribute();
    definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    TransactionStatus inner = txManager.getTransaction(definition);
    // "Suspending current transaction, creating new transaction..."
    // inner: conn1, isNewTransaction=true (완전히 새로운 물리 트랜잭션)

    txManager.rollback(inner);   // conn1 물리 롤백
    // "Resuming suspended transaction after completion of inner transaction"
    txManager.commit(outer);     // conn0 물리 커밋 — 내부 롤백과 무관
}
```

```
REQUIRES_NEW 동작:
  내부 트랜잭션 시작 시 기존 트랜잭션(conn0)을 "보류(suspend)"
  새 커넥션(conn1)으로 완전히 새로운 물리 트랜잭션 시작
  내부 트랜잭션 종료 후 외부 트랜잭션(conn0) "재개(resume)"
  → 내부 롤백이 외부에 영향 없음 / 외부 롤백이 내부에 영향 없음

주의: DB 커넥션 2개 동시 사용 → 성능/데드락 주의
```

---

## 다양한 전파 옵션

| 옵션 | 기존 트랜잭션 없음 | 기존 트랜잭션 있음 | 비고 |
|------|-----------------|-----------------|------|
| `REQUIRED` (기본) | 새 트랜잭션 생성 | 기존에 참여 | 실무 95% |
| `REQUIRES_NEW` | 새 트랜잭션 생성 | 새 트랜잭션 생성 | 가끔 사용 |
| `SUPPORT` | 트랜잭션 없이 진행 | 기존에 참여 | - |
| `NOT_SUPPORT` | 트랜잭션 없이 진행 | 트랜잭션 없이 진행 (기존 보류) | - |
| `MANDATORY` | `IllegalTransactionStateException` 발생 | 기존에 참여 | - |
| `NEVER` | 트랜잭션 없이 진행 | `IllegalTransactionStateException` 발생 | - |
| `NESTED` | 새 트랜잭션 생성 | 중첩 트랜잭션 (savepoint) | JPA 불가 |

> `NESTED`: 중첩 트랜잭션은 외부의 영향을 받지만, 중첩 롤백이 외부에 영향 없음. JPA에서는 사용 불가.

### 주의 — 전파 옵션과 isolation/timeout/readOnly

```
isolation, timeout, readOnly 옵션은
트랜잭션이 처음 시작될 때만 적용된다.
기존 트랜잭션에 참여(REQUIRED)하는 경우에는 적용되지 않는다.
REQUIRES_NEW처럼 새 트랜잭션을 생성할 때는 적용된다.
```

---

## 면접 Q&A

**Q: 스프링 트랜잭션 전파에서 논리 트랜잭션과 물리 트랜잭션의 차이는?**
A: 물리 트랜잭션은 실제 DB 커넥션에 `setAutoCommit(false)` 로 시작하고 커밋/롤백으로 끝나는 실제 트랜잭션이다. 논리 트랜잭션은 트랜잭션 매니저를 통해 사용하는 단위다. 트랜잭션이 중첩될 때 여러 논리 트랜잭션이 하나의 물리 트랜잭션으로 묶인다. 원칙은 단순하다: 모든 논리 트랜잭션이 커밋되어야 물리 트랜잭션이 커밋되고, 하나라도 롤백되면 물리 트랜잭션이 롤백된다.

**Q: 내부 트랜잭션이 롤백되면 어떻게 되는가?**
A: REQUIRED(기본) 전파에서 내부 트랜잭션은 외부 트랜잭션에 참여하므로 (`isNewTransaction=false`) 물리 롤백을 직접 수행하지 않는다. 대신 트랜잭션 동기화 매니저에 `rollbackOnly=true` 를 표시한다. 이후 외부 트랜잭션이 커밋을 시도하면, 스프링이 `rollbackOnly` 플래그를 확인하고 물리 롤백을 수행한 뒤 `UnexpectedRollbackException` 을 발생시킨다. 커밋을 호출했지만 롤백이 발생했음을 명확하게 알려주기 위해서다.

**Q: REQUIRES_NEW는 언제 사용하고 주의사항은?**
A: 내부 트랜잭션의 성공/실패가 외부 트랜잭션에 영향을 주지 말아야 할 때 사용한다. 예를 들어 주문 처리 중에 로그를 남기는 경우, 로그 저장 실패가 주문 전체를 롤백시키면 안 되므로 로그 저장을 `REQUIRES_NEW` 로 분리한다. 주의사항은 외부 트랜잭션의 커넥션(conn0)을 보류하고 새 커넥션(conn1)을 추가로 획득하므로, DB 커넥션 2개를 동시에 사용한다는 점이다. 커넥션 풀이 부족하면 데드락이 발생할 수 있다.
