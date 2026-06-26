# JDBC 이해

> 스프링 DB 1편 | DB1

---

## 전체 흐름 한눈에 보기

```
JDBC 등장 이유
  DB마다 연결/SQL전달/결과수신 방법이 다름 → 표준 인터페이스 필요
  java.sql.Connection / Statement / ResultSet → 표준화

JDBC 최신 데이터 접근 기술
  SQL Mapper (JdbcTemplate, MyBatis): SQL 직접 작성, 반복 코드 제거
  ORM (JPA, Hibernate):              SQL 자동 생성, 객체-테이블 매핑
  → 모두 내부에서 JDBC 사용 → JDBC 원리 이해 필수

JDBC 개발 패턴 (V0)
  getConnection() → prepareStatement(sql) → setXxx(파라미터) → execute
  finally에서 역순으로 close (rs → stmt → con) → 리소스 누수 방지
```

---

## JDBC 표준 인터페이스

```java
java.sql.Connection  // 연결
java.sql.Statement   // SQL 실행
java.sql.ResultSet   // 결과 조회
```

- DB 벤더가 각자의 **JDBC 드라이버** 구현체 제공
- `DriverManager.getConnection(url, user, password)` → URL 접두어로 드라이버 자동 선택
- DB 교체 시: JDBC 드라이버만 교체, 애플리케이션 코드 유지 (단, SQL은 DB별로 다를 수 있음)

### 최신 데이터 접근 기술 비교

| 기술 | 특징 | 대표 기술 |
|------|------|---------|
| SQL Mapper | SQL 직접 작성, 반복 코드 제거 | JdbcTemplate, MyBatis |
| ORM | SQL 자동 생성, 객체-테이블 매핑 | JPA, Hibernate |

> 두 기술 모두 내부에서 JDBC 사용 → **JDBC 원리는 필수 기본기**

---

## 데이터베이스 연결

```java
public abstract class ConnectionConst {
    public static final String URL = "jdbc:h2:tcp://localhost/~/test";
    public static final String USERNAME = "sa";
    public static final String PASSWORD = "";
}

public class DBConnectionUtil {
    public static Connection getConnection() {
        try {
            Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

### DriverManager 연결 흐름

```
1. DriverManager.getConnection(url, user, pw) 호출
2. 등록된 드라이버 목록 순서대로 URL 확인
   - jdbc:h2 → H2 드라이버가 처리
   - 처리 불가 → 다음 드라이버로 넘김
3. 해당 드라이버가 DB에 연결 → Connection 구현체 반환
```

---

## JDBC CRUD 개발 패턴 (MemberRepositoryV0)

### 등록 (INSERT)

```java
public Member save(Member member) throws SQLException {
    String sql = "insert into member(member_id, money) values(?, ?)";
    Connection con = null;
    PreparedStatement pstmt = null;
    try {
        con = getConnection();
        pstmt = con.prepareStatement(sql);
        pstmt.setString(1, member.getMemberId());  // ? 순서대로 바인딩
        pstmt.setInt(2, member.getMoney());
        pstmt.executeUpdate();  // INSERT/UPDATE/DELETE → 영향받은 row 수 반환
        return member;
    } catch (SQLException e) {
        log.error("db error", e);
        throw e;
    } finally {
        close(con, pstmt, null);  // 역순으로 close (누수 방지)
    }
}
```

### 조회 (SELECT)

```java
public Member findById(String memberId) throws SQLException {
    String sql = "select * from member where member_id = ?";
    Connection con = null;
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
        con = getConnection();
        pstmt = con.prepareStatement(sql);
        pstmt.setString(1, memberId);
        rs = pstmt.executeQuery();  // SELECT → ResultSet 반환
        if (rs.next()) {            // cursor 이동, 데이터 있으면 true
            Member member = new Member();
            member.setMemberId(rs.getString("member_id"));
            member.setMoney(rs.getInt("money"));
            return member;
        } else {
            throw new NoSuchElementException("member not found memberId=" + memberId);
        }
    } finally {
        close(con, pstmt, rs);
    }
}
```

> `rs.next()`: 최초 cursor는 데이터를 가리키지 않음 → 한 번 이상 호출해야 데이터 접근 가능.  
> 여러 건 조회는 `while(rs.next())`, 단건은 `if(rs.next())`.

### 수정/삭제 (UPDATE/DELETE)

```java
public void update(String memberId, int money) throws SQLException {
    String sql = "update member set money=? where member_id=?";
    // ...prepareStatement → setXxx → executeUpdate()
}

public void delete(String memberId) throws SQLException {
    String sql = "delete from member where member_id=?";
    // ...prepareStatement → setXxx → executeUpdate()
}
```

### 리소스 정리 — finally에서 역순 close

```java
private void close(Connection con, Statement stmt, ResultSet rs) {
    if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
    if (stmt != null) { try { stmt.close(); } catch (SQLException e) {} }
    if (con != null) { try { con.close(); } catch (SQLException e) {} }
}
```

> **리소스 누수 주의**: finally에서 반드시 close. 빠뜨리면 커넥션이 반환되지 않아 **커넥션 부족 장애** 발생.  
> `PreparedStatement`(Statement 자식)를 사용해야 SQL Injection 방지.

---

## 면접 Q&A

**Q: JDBC가 등장한 이유와 해결한 문제는?**
A: JDBC 이전에는 DB마다 커넥션 연결 방법, SQL 전달 방법, 결과 수신 방법이 달라서 DB를 교체하면 애플리케이션 코드를 모두 수정해야 했고 개발자가 DB마다 사용법을 새로 배워야 했다. JDBC는 `Connection`, `Statement`, `ResultSet`을 표준 인터페이스로 정의했고 각 DB 벤더가 드라이버 구현체를 제공한다. 덕분에 DB 교체 시 드라이버만 교체하면 되고, 개발자는 JDBC 표준 인터페이스 하나만 배우면 된다.

**Q: PreparedStatement를 사용해야 하는 이유는?**
A: `Statement`를 쓰면 SQL을 문자열로 직접 조합하게 되는데, 이 경우 사용자 입력값에 SQL이 포함될 수 있어 SQL Injection 공격에 취약하다. `PreparedStatement`는 `?` 파라미터 바인딩 방식으로 입력값을 SQL이 아닌 데이터로 처리해서 SQL Injection을 원천 차단한다.
