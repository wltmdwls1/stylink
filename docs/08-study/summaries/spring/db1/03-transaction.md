# 트랜잭션 이해

> 스프링 DB 1편 | DB1

---

## 전체 흐름 한눈에 보기

```
트랜잭션 ACID
  원자성: 모두 성공 or 모두 실패
  일관성: 무결성 제약 조건 항상 만족
  격리성: 동시 트랜잭션 간 독립 (격리 수준 4단계로 조정)
  지속성: 커밋된 데이터는 장애 시에도 보존

DB 세션
  커넥션 1개 = DB 세션 1개
  세션이 트랜잭션 시작/커밋/롤백 수행

자동 커밋 vs 수동 커밋
  set autocommit true  → 쿼리마다 자동 커밋
  set autocommit false → 수동 커밋 (= 트랜잭션 시작)
  커밋 전 변경 데이터: 해당 세션에서만 보임

DB 락
  변경 시 락 획득 필요 → 다른 세션은 대기 → 타임아웃 오류
  SELECT FOR UPDATE: 조회 시점에 락 획득

애플리케이션 트랜잭션 (V2)
  Service에서 Connection 생성 + setAutoCommit(false)
  Connection을 Repository 메서드 파라미터로 전달
  문제: 서비스 계층이 JDBC 코드(Connection, SQLException)에 직접 의존
```

---

## 트랜잭션 ACID

| 속성 | 설명 |
|------|------|
| **원자성** | 트랜잭션 내 작업은 모두 성공 or 모두 실패 |
| **일관성** | DB 무결성 제약 조건 항상 만족 |
| **격리성** | 동시 실행 트랜잭션이 서로 영향 안 줌 (격리 수준으로 조정) |
| **지속성** | 커밋된 데이터는 장애 발생해도 영구 보존 |

### 트랜잭션 격리 수준 (Isolation Level)

| 수준 | 설명 | 문제 |
|------|------|------|
| READ UNCOMMITTED | 커밋 안 된 데이터 읽기 가능 | Dirty Read |
| **READ COMMITTED** | 커밋된 데이터만 읽기 (일반적으로 사용) | Non-Repeatable Read |
| REPEATABLE READ | 트랜잭션 내 같은 쿼리 = 같은 결과 | Phantom Read |
| SERIALIZABLE | 완전 순서 실행 (성능 저하) | - |

---

## DB 세션

```
커넥션 요청 → DB가 내부에 세션 생성
세션: 해당 커넥션을 통한 모든 요청 처리
세션이 트랜잭션 시작 → 커밋/롤백 → 새 트랜잭션 시작 가능
커넥션 풀 10개 = DB 세션 10개
```

---

## 자동 커밋 vs 수동 커밋

```sql
-- 자동 커밋 (기본값): 쿼리마다 바로 반영
set autocommit true;
insert into member values ('data1', 10000); -- 즉시 커밋

-- 수동 커밋 (= 트랜잭션 시작)
set autocommit false;
insert into member values ('data3', 10000);
insert into member values ('data4', 10000);
commit;  -- 또는 rollback;
```

> **커밋 전 데이터**: 해당 세션에서만 보임, 다른 세션에는 안 보임.  
> 자동 커밋 모드에서 계좌이체 중 오류 발생 시 → 일부만 반영되는 심각한 문제.

---

## DB 락

### 락 동작 원리

```
세션1: UPDATE memberA (락 획득) → 커밋 전
세션2: UPDATE memberA 시도 → 락 없음 → 대기
세션1: commit → 락 반납
세션2: 락 획득 → UPDATE 수행 → commit
```

### 락 타임아웃

```sql
SET LOCK_TIMEOUT 60000;  -- 60초 대기 후 타임아웃 오류
```

### SELECT FOR UPDATE (조회 락)

```sql
-- 조회 시점에 락 획득 → 다른 세션이 해당 데이터 변경 불가
select * from member where member_id='memberA' for update;
```

> 조회 후 중요한 계산을 수행하는 동안 데이터 변경을 막아야 할 때 사용.

---

## 트랜잭션 적용

### V1 — 트랜잭션 없음 (문제)

```java
public class MemberServiceV1 {
    public void accountTransfer(String fromId, String toId, int money) throws SQLException {
        memberRepository.update(fromId, fromMember.getMoney() - money);  // SQL 1
        validation(toMember);  // 여기서 예외 발생
        memberRepository.update(toId, toMember.getMoney() + money);   // SQL 2 실행 안 됨
    }
}
```

결과: SQL 1만 성공 → `fromId` 돈만 감소, `toId` 돈은 그대로 → **데이터 불일치**

### V2 — 커넥션 파라미터 전달 방식

```java
public class MemberServiceV2 {
    private final DataSource dataSource;

    public void accountTransfer(String fromId, String toId, int money) throws SQLException {
        Connection con = dataSource.getConnection();
        try {
            con.setAutoCommit(false);  // 트랜잭션 시작
            bizLogic(con, fromId, toId, money);
            con.commit();              // 성공 시 커밋
        } catch (Exception e) {
            con.rollback();            // 실패 시 롤백
            throw new IllegalStateException(e);
        } finally {
            release(con);
        }
    }

    private void bizLogic(Connection con, String fromId, String toId, int money) throws SQLException {
        Member fromMember = memberRepository.findById(con, fromId);   // 커넥션 파라미터 전달
        Member toMember = memberRepository.findById(con, toId);
        memberRepository.update(con, fromId, fromMember.getMoney() - money);
        validation(toMember);
        memberRepository.update(con, toId, toMember.getMoney() + money);
    }

    private void release(Connection con) {
        if (con != null) {
            try {
                con.setAutoCommit(true);  // 커넥션 풀 반환 전 auto-commit 복구 필수!
                con.close();
            } catch (Exception e) { log.info("error", e); }
        }
    }
}
```

### Repository — 커넥션 파라미터 버전

```java
// 트랜잭션 유지가 필요한 메서드는 파라미터로 커넥션 받음
public Member findById(Connection con, String memberId) throws SQLException {
    // ...
    // 중요: con.close() 호출 금지! 서비스에서 닫아야 함
    JdbcUtils.closeResultSet(rs);
    JdbcUtils.closeStatement(pstmt);
    // con은 여기서 닫지 않음
}
```

> **커넥션 풀 사용 시 주의**: `con.close()`는 실제 종료가 아닌 풀 반환.  
> 반환 전 `setAutoCommit(true)`로 복구하지 않으면 다음 사용자가 수동 커밋 상태의 커넥션을 받음.

### V2 남은 문제점

```
서비스 계층이 JDBC 코드에 직접 의존:
  Connection, SQLException → 순수한 비즈니스 로직이 아님
  DB 기술을 JPA로 바꾸면 서비스 코드도 전부 수정 필요

커넥션 파라미터 전달:
  모든 Repository 메서드에 Connection 파라미터 추가 → 번거로움
  → 다음 챕터: 스프링의 트랜잭션 매니저로 해결
```

---

## 면접 Q&A

**Q: 트랜잭션 ACID에 대해 설명하고, 격리성이 왜 완벽하게 보장하기 어려운지 설명하라.**
A: 원자성은 트랜잭션 내 작업이 모두 성공하거나 모두 실패해야 하는 성질, 일관성은 DB 무결성 제약 조건을 항상 만족해야 하는 성질, 지속성은 커밋된 데이터는 장애가 발생해도 유지되어야 하는 성질이다. 격리성은 동시 실행 트랜잭션이 서로 영향을 주지 않아야 하는 성질인데, 완벽한 격리를 위해서는 트랜잭션을 순서대로 실행해야 해서 동시 처리 성능이 크게 떨어진다. 이런 트레이드오프 때문에 ANSI 표준은 READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ, SERIALIZABLE 4단계 격리 수준을 제공한다.

**Q: DB 락이 필요한 이유와 SELECT FOR UPDATE를 사용하는 시나리오는?**
A: 여러 세션이 동시에 같은 데이터를 변경하면 원자성이 깨질 수 있다. 락은 한 세션이 데이터를 변경하는 동안 다른 세션이 같은 데이터를 변경하지 못하도록 막는다. `SELECT FOR UPDATE`는 조회 시점에도 락을 획득하는 구문으로, 조회 후 해당 값으로 중요한 연산을 수행할 때 그 사이에 데이터가 변경되면 안 되는 경우에 사용한다. 예를 들어 재고를 조회한 뒤 그 재고 기반으로 주문을 처리하는 경우가 해당된다.
