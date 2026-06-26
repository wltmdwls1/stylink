# 스프링과 문제 해결 - 트랜잭션

> 스프링 DB 1편 | DB1

---

## 전체 흐름 한눈에 보기

```
문제 3가지
  ① 트랜잭션 문제: JDBC 코드(Connection, SQLException)가 서비스 계층에 누수
  ② 예외 누수: SQLException(JDBC 예외)이 서비스 계층으로 전파
  ③ JDBC 반복: try/catch/finally 반복

해결 1 — 트랜잭션 추상화
  PlatformTransactionManager: JDBC/JPA/MongoDB 공통 인터페이스
  → 기술 교체 시 DI만 변경, 서비스 코드 유지

해결 2 — 트랜잭션 동기화
  TransactionSynchronizationManager (ThreadLocal 기반)
  DataSourceUtils.getConnection(): 동기화된 커넥션 반환
  DataSourceUtils.releaseConnection(): 동기화 커넥션 닫지 않고 유지
  → 커넥션 파라미터 전달 불필요

해결 3 — 트랜잭션 템플릿
  TransactionTemplate: try/catch/finally 반복 제거
  executeWithoutResult(status -> { 비즈니스 로직 })

해결 4 (최종) — @Transactional AOP
  서비스 계층에 @Transactional만 선언
  스프링이 CGLIB 프록시 생성 → 트랜잭션 코드 완전 분리
  서비스에 순수 비즈니스 로직만 남음

스프링 부트 자동 등록
  DataSource: application.properties에 url/username/password 설정
  TransactionManager: JDBC → DataSourceTransactionManager 자동 등록
```

---

## 문제점 분석

### 3계층 구조와 서비스 계층의 중요성

```
프레젠테이션 계층  (Controller)  → HTTP, 서블릿 처리
서비스 계층       (Service)     → 핵심 비즈니스 로직 (특정 기술 의존 금지!)
데이터 접근 계층  (Repository)  → JDBC, JPA 처리
```

> 서비스 계층은 UI가 바뀌어도, DB 기술이 바뀌어도 코드 변경이 없어야 한다.

### 기존 코드의 3가지 문제

| 문제 | 원인 |
|------|------|
| JDBC 기술 누수 | `Connection`, `DataSource` → 서비스 계층에 노출 |
| 예외 누수 | `SQLException` (JDBC 전용) → 서비스에서 `throws` 해야 함 |
| 반복 코드 | 모든 서비스 메서드에 `try-catch-finally` + 커밋/롤백 반복 |

---

## 해결 1 — 트랜잭션 추상화

### PlatformTransactionManager 인터페이스

```java
public interface PlatformTransactionManager extends TransactionManager {
    TransactionStatus getTransaction(@Nullable TransactionDefinition definition)
            throws TransactionException;  // 트랜잭션 시작 (또는 기존 참여)
    void commit(TransactionStatus status) throws TransactionException;
    void rollback(TransactionStatus status) throws TransactionException;
}
```

### 기술별 구현체

| 기술 | 구현체 |
|------|--------|
| JDBC | `DataSourceTransactionManager` |
| JPA | `JpaTransactionManager` (DataSourceTransactionManager 기능도 포함) |
| 둘 다 | `JpaTransactionManager` 사용 |

> 기술 변경 시 `@Bean PlatformTransactionManager` 구현체만 교체 — 서비스 코드 무변경.

---

## 해결 2 — 트랜잭션 동기화

### 기존 방식 문제: 커넥션 파라미터 전달

```java
// 서비스에서 커넥션을 만들어서 repository까지 파라미터로 전달해야 함
memberRepository.findById(con, fromId);  // Connection 파라미터 필수
memberRepository.update(con, fromId, money);
```

### 트랜잭션 동기화 매니저 (ThreadLocal 기반)

```
트랜잭션 매니저 동작 순서:
1. getTransaction() 호출 → DataSource에서 커넥션 생성
2. setAutoCommit(false) → 트랜잭션 시작
3. 커넥션을 TransactionSynchronizationManager(ThreadLocal)에 저장
4. 리포지토리에서 DataSourceUtils.getConnection() 호출
   → ThreadLocal에서 동기화된 커넥션 반환 (파라미터 전달 불필요!)
5. commit/rollback 후 ThreadLocal 정리 + setAutoCommit(true) + close
```

### MemberRepositoryV3 — DataSourceUtils 사용

```java
private Connection getConnection() throws SQLException {
    // 트랜잭션 동기화 매니저에 커넥션이 있으면 그것을 반환, 없으면 새로 생성
    Connection con = DataSourceUtils.getConnection(dataSource);
    return con;
}

private void close(Connection con, Statement stmt, ResultSet rs) {
    JdbcUtils.closeResultSet(rs);
    JdbcUtils.closeStatement(stmt);
    // 동기화된 커넥션은 닫지 않고 유지, 동기화 안 된 경우만 닫음
    DataSourceUtils.releaseConnection(con, dataSource);
}
```

> **핵심**: `con.close()` 직접 호출 금지 → 트랜잭션 중 커넥션이 끊어짐.  
> `DataSourceUtils.releaseConnection()`은 동기화된 커넥션을 트랜잭션 종료 전까지 보존.

---

## 해결 3 — 트랜잭션 매니저 직접 사용 (V3_1)

```java
@Slf4j
@RequiredArgsConstructor
public class MemberServiceV3_1 {
    private final PlatformTransactionManager transactionManager;  // JDBC/JPA 공통
    private final MemberRepositoryV3 memberRepository;

    public void accountTransfer(String fromId, String toId, int money) throws SQLException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            bizLogic(fromId, toId, money);  // 커넥션 파라미터 전달 불필요!
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw new IllegalStateException(e);
        }
    }
}
```

---

## 해결 4 — 트랜잭션 템플릿 (V3_2)

```java
public class MemberServiceV3_2 {
    private final TransactionTemplate txTemplate;

    public MemberServiceV3_2(PlatformTransactionManager transactionManager,
                              MemberRepositoryV3 memberRepository) {
        this.txTemplate = new TransactionTemplate(transactionManager);  // 내부에 TxManager 보관
        this.memberRepository = memberRepository;
    }

    public void accountTransfer(String fromId, String toId, int money) throws SQLException {
        txTemplate.executeWithoutResult((status) -> {  // try/catch/commit/rollback 자동 처리
            try {
                bizLogic(fromId, toId, money);
            } catch (SQLException e) {
                throw new IllegalStateException(e);  // 체크 예외 → 언체크 예외로 변환
            }
        });
    }
}
```

템플릿 동작 규칙:
- 정상 수행 → 커밋
- 언체크 예외 → 롤백
- 체크 예외 → 커밋 (람다 내에서 체크 예외를 밖으로 던질 수 없어 언체크로 전환 필요)

---

## 해결 5 (최종) — @Transactional AOP (V3_3)

```java
@Slf4j
@RequiredArgsConstructor
public class MemberServiceV3_3 {
    private final MemberRepositoryV3 memberRepository;

    @Transactional  // 이것 하나로 트랜잭션 완성
    public void accountTransfer(String fromId, String toId, int money) throws SQLException {
        bizLogic(fromId, toId, money);  // 순수 비즈니스 로직만!
    }
}
```

### AOP 프록시 동작 원리

```
클라이언트 → 트랜잭션 프록시(CGLIB) → 실제 Service

프록시 내부:
  1. transactionManager.getTransaction()   // 트랜잭션 시작
  2. target.accountTransfer()              // 실제 서비스 호출
  3. transactionManager.commit/rollback()  // 결과에 따라 커밋/롤백
```

```java
// 프록시 확인 (테스트)
AopUtils.isAopProxy(memberService)     // true — CGLIB 프록시 적용됨
AopUtils.isAopProxy(memberRepository)  // false — @Transactional 없음
```

### 선언적 vs 프로그래밍 방식

| 방식 | 방법 | 실무 사용 |
|------|------|----------|
| **선언적** | `@Transactional` 어노테이션 | 거의 100% |
| 프로그래밍 | `TransactionManager` or `TransactionTemplate` 직접 | 테스트 일부 |

---

## 스프링 부트 자동 리소스 등록

### DataSource 자동 등록

```properties
# application.properties
spring.datasource.url=jdbc:h2:tcp://localhost/~/test
spring.datasource.username=sa
spring.datasource.password=
```

- 스프링 부트가 `HikariDataSource`를 `dataSource` 빈으로 자동 등록
- 개발자가 `DataSource` 빈을 직접 등록하면 자동 등록 비활성화

### TransactionManager 자동 등록

```
JDBC만 있으면 → DataSourceTransactionManager 자동 등록
JPA만 있으면  → JpaTransactionManager 자동 등록
둘 다 있으면  → JpaTransactionManager 자동 등록
              (JpaTransactionManager는 DataSource 기능도 포함)
```

빈 이름: `transactionManager`

---

## 면접 Q&A

**Q: PlatformTransactionManager를 사용하는 이유는?**
A: 트랜잭션을 처리하는 코드는 기술마다 다르다. JDBC는 `con.setAutoCommit(false)`, JPA는 `tx.begin()`처럼 직접 다르다. `PlatformTransactionManager`는 `getTransaction/commit/rollback` 메서드를 인터페이스로 추상화해서 서비스 계층이 특정 기술에 의존하지 않게 한다. JDBC에서 JPA로 바꿀 때 DI 구현체만 변경하면 서비스 코드는 그대로 유지된다.

**Q: 트랜잭션 동기화 매니저가 ThreadLocal을 사용하는 이유는?**
A: 같은 트랜잭션에서 서비스→리포지토리로 호출이 이어질 때 같은 커넥션을 사용해야 한다. 기존에는 커넥션을 파라미터로 전달했는데, ThreadLocal을 사용하면 해당 스레드 전용 저장소에 커넥션을 보관하므로 멀티스레드 환경에서도 안전하게 같은 커넥션을 공유할 수 있다. 파라미터 전달 없이 `DataSourceUtils.getConnection()`으로 언제든 동기화된 커넥션을 꺼낼 수 있다.

**Q: @Transactional은 어떻게 동작하는가?**
A: 스프링은 `@Transactional`이 붙은 빈에 CGLIB 기반 프록시를 생성한다. 클라이언트는 실제 서비스가 아닌 프록시를 호출하고, 프록시가 트랜잭션을 시작한 후 실제 서비스 메서드를 호출한다. 정상이면 커밋, 언체크 예외가 발생하면 롤백 후 예외를 다시 던진다. 서비스 계층에 트랜잭션 코드가 전혀 없어도 AOP 프록시가 처리한다.
