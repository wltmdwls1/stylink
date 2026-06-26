# 자바 예외 이해

> 스프링 DB 1편 | DB1

---

## 전체 흐름 한눈에 보기

```
예외 계층
  Object → Throwable → Error (복구 불가, 잡지 말 것)
                     → Exception (체크 예외)
                         └─ RuntimeException (언체크 예외)

체크 예외: 잡거나 throws 선언 필수 (컴파일 오류)
언체크 예외: 잡거나 무시 가능 (throws 생략 가능)

체크 예외 문제점
  ① 복구 불가능한 예외를 서비스/컨트롤러가 알아야 함
  ② JDBC→JPA 변경 시 throws SQLException → throws JPAException (파급 효과)
  → 실무에서는 언체크 예외 권장

예외 전환
  catch (SQLException e) { throw new RuntimeSQLException(e); }
  → 반드시 기존 예외(e)를 포함해야 Caused by 스택 트레이스 확인 가능
```

---

## 예외 계층

```
Object
  └─ Throwable
       ├─ Error             (OutOfMemoryError 등, 잡으면 안 됨)
       └─ Exception         (체크 예외의 최상위)
            └─ RuntimeException  (언체크 예외의 최상위)
                 └─ 하위 예외들
```

| 종류 | 기준 | 처리 방식 |
|------|------|---------|
| **체크 예외** | `Exception` 상속 (`RuntimeException` 제외) | catch 또는 throws 필수 |
| **언체크 예외** | `RuntimeException` 상속 | 선택적 처리, throws 생략 가능 |

> `catch(Exception e)` → 하위의 모든 체크 예외 포함  
> `Throwable`은 잡으면 `Error`도 함께 잡힘 — 애플리케이션 로직에서 사용 금지.

---

## 체크 예외

```java
// 체크 예외 정의: Exception 상속
static class MyCheckedException extends Exception {
    public MyCheckedException(String message) { super(message); }
}

// 잡아서 처리
public void callCatch() {
    try {
        repository.call();
    } catch (MyCheckedException e) {
        log.info("예외 처리, message={}", e.getMessage(), e);
    }
}

// 밖으로 던지기: throws 선언 필수
public void callThrow() throws MyCheckedException {
    repository.call();
}
```

### 장단점

| 장점 | 단점 |
|------|------|
| 컴파일러가 예외 누락을 잡아줌 | 처리 불가능한 예외도 throws 선언 강제 |
| 안전한 예외 처리 보장 | 의존 관계 문제 발생 |

---

## 언체크 예외

```java
// 언체크 예외 정의: RuntimeException 상속
static class MyUncheckedException extends RuntimeException {
    public MyUncheckedException(String message) { super(message); }
}

// 잡아도 되고, 안 잡아도 됨
public void callThrow() {
    repository.call();  // throws 선언 없어도 자동으로 위로 전파
}
```

---

## 체크 예외의 문제점 (실무 핵심)

### 문제 1: 복구 불가능한 예외

```java
// 서비스/컨트롤러는 처리 불가능한데 선언 강제
class Service {
    public void logic() throws SQLException, ConnectException { ... }
}
class Controller {
    public void request() throws SQLException, ConnectException { ... }
}
```

→ `SQLException`은 DB 서버 다운, 잘못된 SQL 등 → 서비스에서 복구 불가  
→ 공통 예외 처리(`ControllerAdvice`)로 처리하면 충분

### 문제 2: 의존 관계 문제

```
JDBC → JPA 기술 변경 시:
  throws SQLException → throws JPAException 으로 모든 서비스/컨트롤러 수정 필요
  → OCP, DI 원칙이 체크 예외 때문에 깨짐
```

### 잘못된 우회 방법

```java
// throws Exception: 모든 체크 예외를 무효화 → 사용 금지
public void logic() throws Exception { ... }
```

---

## 언체크 예외 활용 (권장)

```java
// 체크 예외 → 언체크 예외로 전환 (Repository에서)
class Repository {
    public void call() {
        try {
            runSQL();
        } catch (SQLException e) {
            throw new RuntimeSQLException(e);  // 기존 예외 포함 필수!
        }
    }
}

// Service/Controller: 더 이상 throws 선언 불필요
class Service {
    public void logic() {
        repository.call();
        networkClient.call();  // throws 없이 자연스럽게 전파
    }
}
```

### 기술 변경 시 파급 효과

```
체크 예외: SQLException → JPAException 변경 시 모든 서비스/컨트롤러 수정
언체크 예외: 공통 예외 처리 부분 한 곳만 수정
```

---

## 예외 포함과 스택 트레이스

### 기존 예외 포함 (필수!)

```java
// 올바른 방법: cause 전달
catch (SQLException e) {
    throw new RuntimeSQLException(e);  // e 포함
}
```

```
출력 결과:
RuntimeSQLException: java.sql.SQLException: ex
    at Repository.call(...)
Caused by: java.sql.SQLException: ex   ← 원인 예외가 보임
    at Repository.runSQL(...)
```

### 기존 예외 미포함 (잘못된 방법)

```java
// 잘못된 방법: cause 미전달
catch (SQLException e) {
    throw new RuntimeSQLException();  // e 없이 새로 생성
}
```

```
출력 결과:
RuntimeSQLException: null   ← 원인 예외 정보 없음!
    at Repository.call(...)
    (Caused by 없음 → 원인 추적 불가)
```

> **로그에 예외 출력**: `log.info("ex", e)` — 마지막 파라미터에 예외 전달 시 스택 트레이스 자동 출력.

---

## 예외 선택 기준

| 상황 | 사용 |
|------|------|
| **기본** | 언체크(런타임) 예외 |
| 반드시 처리해야 하는 비즈니스 로직 | 체크 예외 (예: 계좌이체 실패, 포인트 부족, 로그인 불일치) |
| DB/네트워크 시스템 예외 | 언체크 예외로 변환 후 공통 처리 |

---

## 면접 Q&A

**Q: 체크 예외와 언체크 예외의 차이는? 실무에서 언체크 예외를 선호하는 이유는?**
A: 체크 예외는 `Exception`을 상속한 예외로 컴파일러가 처리를 강제한다 — 잡거나 `throws`로 선언해야 한다. 언체크 예외는 `RuntimeException`을 상속한 예외로 `throws` 선언 없이도 자동으로 전파된다. 실무에서 언체크 예외를 선호하는 이유는 두 가지다. 첫째, DB/네트워크 예외처럼 복구 불가능한 예외를 서비스/컨트롤러가 `throws`로 선언해야 하는 의존 관계 문제를 피할 수 있다. 둘째, JDBC→JPA처럼 기술이 변경되면 체크 예외는 모든 서비스/컨트롤러의 `throws` 선언을 바꿔야 하지만, 언체크 예외는 공통 예외 처리 한 곳만 수정하면 된다.

**Q: 예외 전환 시 왜 기존 예외를 반드시 포함해야 하는가?**
A: `catch(SQLException e) { throw new RuntimeSQLException(e); }` 에서 `e`를 생성자에 전달해야 한다. 전달하지 않으면 변환된 `RuntimeSQLException`의 스택 트레이스에 `Caused by`가 없어서 원인 예외(`SQLException`)를 확인할 수 없다. 운영 환경에서 장애 분석 시 원인을 추적하지 못하는 심각한 문제가 발생한다.
