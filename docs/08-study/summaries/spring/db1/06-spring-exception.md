# 스프링과 문제 해결 - 예외 처리, 반복

> 스프링 DB 1편 | DB1

---

## 전체 흐름 한눈에 보기

```
문제 → 해결 순서

① 체크 예외 인터페이스 오염 문제
   인터페이스 메서드에도 throws SQLException 필요
   → 런타임 예외로 변환하면 인터페이스 순수하게 유지 가능

② 예외 구분 불가 문제
   MyDbException만 던지면 키 중복 vs SQL 오류 구분 불가
   → MyDuplicateKeyException (e.getErrorCode() == 23505) 직접 정의

③ DB 종속 errorCode 문제
   H2: 23505, MySQL: 1062 → 직접 처리 불가
   → SQLExceptionTranslator: sql-error-codes.xml 기반 자동 변환
      → DataAccessException (스프링 표준, 런타임 예외)

④ JDBC 반복 코드 문제
   → JdbcTemplate: 커넥션 동기화 + 예외 변환 + 반복 코드 자동 처리
```

---

## 체크 예외와 인터페이스 문제

### 체크 예외 사용 시 인터페이스도 오염

```java
// 체크 예외 사용 시: 인터페이스에도 throws 선언 필수
public interface MemberRepositoryEx {
    Member save(Member member) throws SQLException;  // JDBC 기술 종속!
    Member findById(String memberId) throws SQLException;
}
```

> 인터페이스가 `java.sql.SQLException`에 의존 → JDBC→JPA 변경 시 인터페이스도 변경 필요.  
> 인터페이스 도입 목적(기술 변경 용이)이 무의미해짐.

### 런타임 예외 사용 시: 인터페이스 순수 유지

```java
// 런타임 예외 사용 시: 인터페이스에 throws 선언 불필요
public interface MemberRepository {
    Member save(Member member);       // throws 없음!
    Member findById(String memberId);
    void update(String memberId, int money);
    void delete(String memberId);
}
```

---

## 런타임 예외 적용 (V4_1)

### MyDbException 정의

```java
public class MyDbException extends RuntimeException {
    public MyDbException() {}
    public MyDbException(String message) { super(message); }
    public MyDbException(String message, Throwable cause) { super(message, cause); }
    public MyDbException(Throwable cause) { super(cause); }
}
```

### MemberRepositoryV4_1 — 체크 예외를 런타임 예외로 변환

```java
public class MemberRepositoryV4_1 implements MemberRepository {
    @Override
    public Member save(Member member) {
        try {
            // ... JDBC 코드
        } catch (SQLException e) {
            throw new MyDbException(e);  // 기존 예외 포함 필수!
        } finally {
            close(con, pstmt, null);
        }
    }
}
```

### MemberServiceV4 — 이제 throws 선언 없음

```java
@Slf4j
@RequiredArgsConstructor
public class MemberServiceV4 {
    private final MemberRepository memberRepository;  // 인터페이스에 의존

    @Transactional
    public void accountTransfer(String fromId, String toId, int money) {
        // throws SQLException 제거됨 — 순수한 비즈니스 로직!
        bizLogic(fromId, toId, money);
    }
}
```

---

## 데이터 접근 예외 직접 정의 — 예외 구분

### 문제: MyDbException만 던지면 예외 종류 구분 불가

```
키 중복 오류: H2 errorCode = 23505
SQL 문법 오류: H2 errorCode = 42000
→ MyDbException 하나로는 복구 로직 작성 불가
```

### MyDuplicateKeyException 정의

```java
// MyDbException 상속 → 데이터베이스 예외 계층 형성
public class MyDuplicateKeyException extends MyDbException {
    public MyDuplicateKeyException(Throwable cause) { super(cause); }
}
```

### Repository — errorCode로 예외 구분

```java
catch (SQLException e) {
    if (e.getErrorCode() == 23505) {  // H2 키 중복 오류 코드
        throw new MyDuplicateKeyException(e);
    }
    throw new MyDbException(e);
}
```

### Service — 예외 구분으로 복구

```java
try {
    repository.save(new Member(memberId, 0));
} catch (MyDuplicateKeyException e) {
    // 키 중복 → 새 ID 생성 후 재시도 (예외 복구)
    String retryId = generateNewId(memberId);
    repository.save(new Member(retryId, 0));
} catch (MyDbException e) {
    // 복구 불가 → 공통 예외 처리로 전파
    throw e;
}
```

> **남은 문제**: DB마다 errorCode가 다름 (H2: 23505, MySQL: 1062) → 직접 관리 한계.

---

## 스프링 예외 추상화

### DataAccessException 계층

```
DataAccessException (RuntimeException)
  ├── Transient     (일시적 오류 — 재시도 가능)
  │     └── 락 타임아웃, 쿼리 타임아웃 등
  └── NonTransient  (비일시적 오류 — 재시도해도 같은 결과)
        ├── BadSqlGrammarException  (SQL 문법 오류)
        └── DuplicateKeyException   (키 중복)
```

- 모든 스프링 데이터 접근 예외는 **런타임 예외**
- JDBC/JPA/MongoDB 등 기술과 무관하게 공통 사용

### SQLExceptionTranslator — 자동 변환

```java
// 스프링이 제공하는 예외 변환기
SQLExceptionTranslator exTranslator = new SQLErrorCodeSQLExceptionTranslator(dataSource);
DataAccessException resultEx = exTranslator.translate("select", sql, e);
// → BadSqlGrammarException, DuplicateKeyException 등으로 자동 변환
```

### 변환 원리: sql-error-codes.xml

```xml
<!-- 스프링 내부 설정 파일 (직접 수정 불필요) -->
<bean id="H2" class="org.springframework.jdbc.support.SQLErrorCodes">
    <property name="badSqlGrammarCodes">
        <value>42000,42001,42101,42122...</value>
    </property>
    <property name="duplicateKeyCodes">
        <value>23001,23505</value>
    </property>
</bean>
<bean id="MySQL" class="org.springframework.jdbc.support.SQLErrorCodes">
    <property name="duplicateKeyCodes">
        <value>1062</value>
    </property>
</bean>
```

> DataSource에서 DB 종류를 감지해 자동으로 알맞은 errorCode 표를 적용한다.

---

## 스프링 예외 추상화 적용 (V4_2)

```java
public class MemberRepositoryV4_2 implements MemberRepository {
    private final DataSource dataSource;
    private final SQLExceptionTranslator exTranslator;

    public MemberRepositoryV4_2(DataSource dataSource) {
        this.dataSource = dataSource;
        this.exTranslator = new SQLErrorCodeSQLExceptionTranslator(dataSource);
    }

    @Override
    public Member save(Member member) {
        String sql = "insert into member(member_id, money) values(?, ?)";
        try {
            // ... JDBC 코드
        } catch (SQLException e) {
            throw exTranslator.translate("save", sql, e);  // 자동 변환!
        }
    }
}
```

구현체 교체 시 서비스 코드 무변경:

```java
// 빈 등록만 변경
@Bean
MemberRepository memberRepository() {
    return new MemberRepositoryV4_2(dataSource); // V4_1 → V4_2로만 교체
}
```

---

## JDBC 반복 문제 해결 — JdbcTemplate (V5)

### 반복되는 JDBC 코드

```
모든 메서드에서 반복:
  커넥션 조회/동기화
  PreparedStatement 생성 + 파라미터 바인딩
  쿼리 실행
  결과 바인딩
  예외 → 스프링 예외 변환
  리소스 종료 (finally)
```

### MemberRepositoryV5 — JdbcTemplate으로 모두 해결

```java
public class MemberRepositoryV5 implements MemberRepository {
    private final JdbcTemplate template;

    public MemberRepositoryV5(DataSource dataSource) {
        template = new JdbcTemplate(dataSource);
    }

    @Override
    public Member save(Member member) {
        String sql = "insert into member(member_id, money) values(?, ?)";
        template.update(sql, member.getMemberId(), member.getMoney());
        return member;
    }

    @Override
    public Member findById(String memberId) {
        String sql = "select * from member where member_id = ?";
        return template.queryForObject(sql, memberRowMapper(), memberId);
    }

    @Override
    public void update(String memberId, int money) {
        String sql = "update member set money=? where member_id=?";
        template.update(sql, money, memberId);
    }

    @Override
    public void delete(String memberId) {
        String sql = "delete from member where member_id=?";
        template.update(sql, memberId);
    }

    private RowMapper<Member> memberRowMapper() {
        return (rs, rowNum) -> {
            Member member = new Member();
            member.setMemberId(rs.getString("member_id"));
            member.setMoney(rs.getInt("money"));
            return member;
        };
    }
}
```

### JdbcTemplate이 자동으로 처리하는 것

| 기능 | 처리 방식 |
|------|---------|
| 커넥션 동기화 | `DataSourceUtils.getConnection()` 내부 호출 |
| 예외 변환 | `SQLExceptionTranslator` 내부 적용 |
| 리소스 정리 | `finally` 블록 자동 처리 |
| 반복 코드 | 템플릿 콜백 패턴으로 제거 |

---

## 최종 정리 — 서비스 계층 순수성 달성

| 문제 | 해결 방법 |
|------|---------|
| 트랜잭션 코드 누수 | `@Transactional` AOP |
| 예외 기술 종속 | 런타임 예외 + 스프링 예외 추상화 |
| 인터페이스 오염 | 체크 예외 → 런타임 예외 변환 |
| JDBC 반복 코드 | `JdbcTemplate` |

---

## 면접 Q&A

**Q: 체크 예외를 사용하면 인터페이스에 왜 문제가 생기는가?**
A: 체크 예외는 메서드 시그니처에 `throws`로 선언해야 한다. 구현 클래스가 `throws SQLException`을 선언하려면 인터페이스 메서드에도 `throws SQLException`이 있어야 한다. 결과적으로 특정 기술에 종속되지 않아야 할 인터페이스가 `java.sql.SQLException`이라는 JDBC 기술에 오염된다. 런타임 예외는 `throws` 선언이 불필요하므로 인터페이스를 기술 독립적으로 유지할 수 있다.

**Q: 스프링의 DataAccessException을 사용하는 이유는?**
A: 직접 만든 예외(`MyDbException`)로는 DB마다 다른 errorCode를 관리하기 어렵다. 스프링은 `sql-error-codes.xml`에 주요 DB(H2, MySQL, Oracle 등)의 errorCode를 정의하고, `SQLExceptionTranslator`가 `SQLException`을 `BadSqlGrammarException`, `DuplicateKeyException` 등 의미 있는 예외로 자동 변환한다. 또한 `DataAccessException`은 모두 런타임 예외이므로 서비스 계층이 JDBC/JPA 여부와 무관하게 같은 스프링 예외로 처리할 수 있다.
