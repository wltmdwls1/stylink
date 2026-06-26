# 커넥션풀과 DataSource 이해

> 스프링 DB 1편 | DB1

---

## 전체 흐름 한눈에 보기

```
커넥션 풀이 필요한 이유
  매 요청마다 TCP 3-way handshake + DB 인증 = 수십 ms 지연
  → 커넥션을 미리 생성해두고 재사용 (기본 10개)
  → 별도 쓰레드에서 백그라운드로 채움 (앱 시작 속도 영향 없음)
  → 스프링 부트 2.0+: HikariCP 기본 제공

DataSource (커넥션 획득 추상화)
  DriverManager 직접 사용 → 변경 시 코드 수정 필요
  DataSource 인터페이스에 의존 → 구현체만 교체 (DI + OCP)
  스프링: DriverManagerDataSource (DriverManager를 DataSource로 래핑)
  설정과 사용의 분리: 설정 시 URL/USER/PW 입력, 사용 시 getConnection()만 호출
```

---

## 커넥션 풀

### 매번 커넥션 생성 시 문제

```
1. TCP/IP 커넥션 연결 (3-way handshake)
2. DB 인증 (ID/PW 전달)
3. DB 세션 생성
4. 커넥션 객체 반환
→ 매 요청마다 반복 = 응답 속도 저하
```

### 커넥션 풀 동작 방식

```
애플리케이션 시작
  → 커넥션 풀이 커넥션 N개 미리 생성 (기본 10개)
  → 별도 쓰레드에서 백그라운드로 채움 (앱 시작 속도에 영향 없음)

요청 시
  → 풀에서 커넥션 꺼내어 사용
  → 사용 후 풀에 반환 (close가 아닌 반환)

효과
  → 커넥션 생성 시간 제거 → 빠른 응답
  → 최대 커넥션 수 제한 → DB 보호
```

### HikariCP (스프링 부트 기본 커넥션 풀)

```java
HikariDataSource dataSource = new HikariDataSource();
dataSource.setJdbcUrl(URL);
dataSource.setUsername(USERNAME);
dataSource.setPassword(PASSWORD);
dataSource.setMaximumPoolSize(10);
dataSource.setPoolName("MyPool");
```

```
로그: MyPool - After adding stats (total=10, active=2, idle=8, waiting=0)
  total=10: 전체 커넥션 수
  active=2: 사용 중
  idle=8:   대기 중
  waiting=0: 커넥션 대기 중인 요청 수
```

---

## DataSource 인터페이스

```java
public interface DataSource {
    Connection getConnection() throws SQLException;
}
```

### DriverManager vs DataSource

| 항목 | DriverManager | DataSource |
|------|--------------|-----------|
| 커넥션 획득 시 | URL/USER/PW 매번 전달 | `getConnection()` 만 호출 |
| 설정과 사용 | 혼재 | **분리** (설정은 한 번, 사용은 여러 번) |
| 구현체 교체 | 코드 수정 필요 | 구현체만 교체 (DI + OCP) |

### 설정과 사용의 분리

```java
// 설정 — 한 곳에서 한 번
HikariDataSource dataSource = new HikariDataSource();
dataSource.setJdbcUrl(URL);
dataSource.setUsername(USERNAME);
dataSource.setPassword(PASSWORD);

// 사용 — 여러 곳에서 (URL/USER/PW 모름, 알 필요 없음)
Connection con = dataSource.getConnection();
```

> Repository는 `DataSource`만 주입받으면 됨 — URL/USERNAME/PASSWORD에 의존하지 않음.

---

## MemberRepositoryV1 — DataSource 적용

```java
@Slf4j
public class MemberRepositoryV1 {
    private final DataSource dataSource;  // DataSource 인터페이스에 의존

    public MemberRepositoryV1(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection getConnection() throws SQLException {
        Connection con = dataSource.getConnection();
        return con;
    }

    private void close(Connection con, Statement stmt, ResultSet rs) {
        JdbcUtils.closeResultSet(rs);    // 스프링 제공 편의 메서드
        JdbcUtils.closeStatement(stmt);
        JdbcUtils.closeConnection(con);
    }
}
```

### DriverManagerDataSource vs HikariDataSource 교체 시 차이

```
DriverManagerDataSource:
  conn0, conn1, conn2... → 매번 새 커넥션 생성

HikariDataSource:
  HikariProxyConnection wrapping conn0
  HikariProxyConnection wrapping conn0  ← 같은 conn0 재사용!
  → 커넥션 반환 후 재사용 확인
```

> Repository 코드는 전혀 변경 없음 — `DataSource` 인터페이스에만 의존하기 때문.

---

## 면접 Q&A

**Q: 커넥션 풀을 사용하는 이유는?**
A: DB 커넥션을 매번 새로 생성하면 TCP 연결, DB 인증, 세션 생성 과정이 반복되어 수십 ms 이상 지연이 발생한다. 커넥션 풀은 애플리케이션 시작 시 커넥션을 미리 생성해두고, 요청이 오면 바로 사용하고 반환하는 방식으로 커넥션 생성 시간을 없앤다. 또한 최대 커넥션 수를 제한해서 DB를 보호하는 효과도 있다.

**Q: DataSource 인터페이스를 사용하는 이유는?**
A: `DriverManager`를 직접 사용하면 커넥션 획득 시마다 URL/USERNAME/PASSWORD를 전달해야 하고, HikariCP로 변경할 때 관련 코드를 모두 수정해야 한다. `DataSource` 인터페이스에만 의존하면 설정(DataSource 생성 시 URL/PW 입력)과 사용(`getConnection()` 호출)이 분리되어, Repository는 `DataSource`만 주입받으면 되고 커넥션 풀 구현체를 교체해도 Repository 코드는 변경하지 않아도 된다. (DI + OCP)
