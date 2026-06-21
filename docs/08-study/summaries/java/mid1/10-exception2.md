# 예외 처리2 - 실습

> 자바 중급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터] 예외 이론 (체크 vs 언체크)

실습: NetworkService 예외 처리 발전

V1 — 예외 없음: 반환값 방식 (정상/에러 뒤섞임)
V2 — 체크 예외: 모든 레이어에 throws 전파 → 코드 오염
V3 — 언체크 예외: 처리 가능한 곳에서만 잡음 → 간결
V4 — 사용자 정의 예외 계층: 비즈니스 예외를 의미있게 표현
V5 — 예외 포함해서 던지기 (exception chaining)

예외 전략 결론
  → 비즈니스 예외 = 언체크 예외
  → 로그에 스택트레이스 기록
  → 최상위 핸들러에서 일괄 처리 (Spring GlobalExceptionHandler)
```

---

## V3 — 언체크 예외로 정리

```java
// 사용자 정의 언체크 예외
public class NetworkException extends RuntimeException {
    public NetworkException(String message) { super(message); }
    public NetworkException(String message, Throwable cause) { super(message, cause); }
}

public class ConnectException extends NetworkException {
    public ConnectException(String message) { super(message); }
}

public class SendException extends NetworkException {
    private final String sendData;
    public SendException(String message, String sendData) {
        super(message);
        this.sendData = sendData;
    }
}
```

```java
// NetworkService — throws 선언 불필요
public class NetworkService {
    public void sendMessage(String data) {
        NetworkClient client = new NetworkClient(address);
        try {
            client.connect();   // ConnectException (언체크)
            client.send(data);  // SendException (언체크)
        } finally {
            client.disconnect();  // 항상 실행
        }
    }
}

// 최상위 Main에서만 잡음
try {
    networkService.sendMessage(data);
} catch (ConnectException e) {
    log("연결 실패: " + e.getMessage());
} catch (SendException e) {
    log("전송 실패, 데이터: " + e.getSendData());
} catch (NetworkException e) {
    log("네트워크 오류: " + e.getMessage());
}
```

중간 레이어(NetworkService)는 `throws` 없이 깔끔하다.

---

## V4 — 사용자 정의 예외 계층

```java
// 비즈니스 예외 계층 (실무 패턴)
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

public enum ErrorCode {
    USER_NOT_FOUND("사용자를 찾을 수 없습니다"),
    INVENTORY_NOT_AVAILABLE("재고가 없습니다"),
    PAYMENT_FAILED("결제에 실패했습니다");

    private final String message;
    // 생성자, getter
}

// 사용
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
```

---

## V5 — 예외 포함해서 던지기 (Exception Chaining)

외부 시스템 예외를 비즈니스 예외로 감쌀 때 원본 예외를 포함해야 스택트레이스가 유지된다.

```java
try {
    // DB 연결 등 외부 예외
    connection.connect();
} catch (SQLException e) {
    // 원본 cause를 포함해서 던짐
    throw new NetworkException("DB 연결 실패", e);
    //                                          ^ 이 cause가 없으면 근본 원인을 모름
}
```

로그에서 스택트레이스를 볼 때 cause가 있어야 실제 원인을 추적할 수 있다.

---

## Spring의 GlobalExceptionHandler 연결

실무에서 언체크 예외는 최상위 레이어에서 일괄 처리한다:

```java
// Spring MVC의 전역 예외 처리
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return ResponseEntity
            .status(e.getErrorCode().getStatus())
            .body(ApiResponse.error(e.getErrorCode().getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("Unexpected exception", e);  // 스택트레이스 기록
        return ResponseEntity.internalServerError()
            .body(ApiResponse.error("서버 오류가 발생했습니다"));
    }
}
```

---

## 예외 처리 안티패턴

```java
// 1. 예외 삼킴 — 최악의 패턴
try {
    doSomething();
} catch (Exception e) {
    // 아무것도 안 함 → 버그가 조용히 사라짐
}

// 2. 로그 + 다시 던지기 중복
try {
    doSomething();
} catch (Exception e) {
    log.error("에러: ", e);  // 여기서 한번
    throw e;  // 위쪽에서 또 잡아서 또 로그 → 중복 로그
}

// 3. cause 없이 감싸기
} catch (SQLException e) {
    throw new DataException("DB 오류");  // 원본 e가 사라짐 → 디버깅 불가
}
// 올바른 방법:
    throw new DataException("DB 오류", e);  // cause 포함
```

---

## 면접 Q&A

**Q: 예외를 잡을 때 구체적인 타입부터 잡는 이유는?**  
A: catch 블럭은 위에서부터 순서대로 매칭한다. `catch (Exception e)`를 먼저 쓰면 모든 예외가 거기서 걸려 구체적인 처리가 불가능하다. 구체적인 예외(`ConnectException`) → 부모(`NetworkException`) → 최상위(`Exception`) 순서로 써야 한다.

**Q: 예외를 cause 없이 다시 던지면 안 되는 이유는?**  
A: `throw new BusinessException("DB 오류")`처럼 원본 예외를 포함하지 않으면 근본 원인(`cause`)이 사라진다. 스택트레이스에 원본 예외 정보가 없어서 어디서, 왜 실패했는지 디버깅이 어렵다. `throw new BusinessException("DB 오류", originalException)`처럼 cause를 포함해야 한다.

**Q: GlobalExceptionHandler가 필요한 이유는?**  
A: 언체크 예외를 모든 곳에서 잡으면 중복 코드가 많아진다. Spring의 `@RestControllerAdvice`를 사용하면 한 곳에서 예외 타입별로 응답 포맷, 로그, HTTP 상태코드를 일관되게 처리할 수 있다.
