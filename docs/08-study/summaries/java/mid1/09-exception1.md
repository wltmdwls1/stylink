# 예외 처리1 - 이론

> 자바 중급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
예외 처리가 없으면?
  → 반환값으로 에러 표현 → 정상/에러 코드 뒤섞임 → 가독성 최악

자바 예외 계층
  Throwable
  ├── Error: JVM 수준 (OutOfMemoryError, StackOverflowError) → 잡으면 안 됨
  └── Exception
       ├── IOException, SQLException (체크 예외) → 반드시 처리
       └── RuntimeException (언체크 예외) → 처리 선택

체크 예외 vs 언체크 예외
  → 체크: 컴파일러가 처리를 강제 (throws 선언 or try-catch)
  → 언체크: 선택 사항 (RuntimeException 하위)

→ 실무 트렌드: 체크 예외 대신 언체크 예외 사용
```

---

## 예외 처리 이전의 코드 (반환값으로 에러 표현)

```java
// V1 — 에러를 반환값으로 표현 (레거시 C 스타일)
String result = client.connect();
if (!result.equals("success")) {
    log("연결 실패: " + result);
    client.disconnect();
    return;
}
String sendResult = client.send(data);
if (!sendResult.equals("success")) {
    log("전송 실패: " + sendResult);
    client.disconnect();
    return;
}
client.disconnect();
```

정상 흐름과 에러 처리가 뒤섞여 코드가 복잡해진다.  
에러 반환값을 체크하지 않으면 에러가 무시된다.

**V1의 남은 문제**: 정상 흐름과 에러 처리의 분리 필요.

---

## 자바 예외 계층

```
Throwable
├── Error (JVM 수준 오류)
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   └── ...
└── Exception (애플리케이션 예외)
    ├── RuntimeException (언체크 예외)
    │   ├── NullPointerException
    │   ├── IllegalArgumentException
    │   ├── IndexOutOfBoundsException
    │   └── ...
    └── 그 외 Exception (체크 예외)
        ├── IOException
        ├── SQLException
        └── ...
```

**Error**: JVM이 처리 불가 수준의 오류. `catch`하면 안 됨.  
**Exception**: 애플리케이션에서 처리 가능한 예외.

---

## 체크 예외 (Checked Exception)

`RuntimeException`이 아닌 `Exception` 하위 클래스.  
컴파일러가 처리를 **강제**한다 — `throws` 선언하거나 `try-catch`로 잡아야 한다.

```java
// IOException은 체크 예외
public void readFile(String path) throws IOException {  // 선언 필수
    FileReader fr = new FileReader(path);  // IOException 발생 가능
}

// 또는 catch
public void readFile(String path) {
    try {
        FileReader fr = new FileReader(path);
    } catch (IOException e) {
        log("파일 읽기 실패: " + e.getMessage());
    }
}
```

**장점**: 컴파일러가 처리를 강제해 누락이 없음.  
**단점**: 처리할 수 없는 예외도 throws를 계속 전파해야 하는 **체크 예외 체인** 문제.

---

## 언체크 예외 (Unchecked Exception = RuntimeException)

`RuntimeException` 하위. 처리를 **선택**할 수 있다.

```java
public class NetworkException extends RuntimeException {
    public NetworkException(String message) {
        super(message);
    }
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}

// throws 선언 없이도 됨
public void connect(String address) {
    if (address == null) {
        throw new NetworkException("주소가 없습니다");
    }
    // 연결 시도...
}
```

**실무 트렌드**: 비즈니스 예외를 `RuntimeException`으로 만든다.  
→ `throws` 체인 불필요, 처리할 수 없는 예외는 최상단에서 일괄 처리.

---

## try-catch-finally

```java
NetworkClient client = new NetworkClient(address);
try {
    client.connect();  // 예외 발생 가능
    client.send(data); // 예외 발생 가능
} catch (NetworkException e) {
    log("연결/전송 에러: " + e.getMessage());
} catch (Exception e) {
    log("예상치 못한 에러: " + e);
} finally {
    client.disconnect();  // 예외 여부와 무관하게 항상 실행
}
```

`finally` 블럭: 자원 해제(연결 해제, 파일 닫기 등)에 사용.  
Java 7+에서는 `try-with-resources`로 더 깔끔하게 처리 가능.

---

## try-with-resources

```java
// 자동으로 close() 호출 (AutoCloseable 구현체에 사용)
try (FileReader fr = new FileReader("file.txt");
     BufferedReader br = new BufferedReader(fr)) {
    String line = br.readLine();
} catch (IOException e) {
    log("파일 읽기 실패");
}
// try 블럭을 벗어나면 자동으로 fr.close(), br.close() 호출
```

---

## 면접 Q&A

**Q: 체크 예외와 언체크 예외의 차이는?**  
A: 체크 예외(`Exception` 직접 상속)는 컴파일러가 처리를 강제한다. 메서드에서 발생시키면 반드시 `throws` 선언하거나 `try-catch`로 잡아야 한다. 언체크 예외(`RuntimeException` 상속)는 선택 사항이다. 실무에서는 비즈니스 예외를 주로 언체크 예외로 만든다.

**Q: 실무에서 체크 예외보다 언체크 예외를 선호하는 이유는?**  
A: 체크 예외는 처리할 수 없는 경우에도 모든 메서드에 `throws`를 선언해야 하는 부담이 있다. 특히 깊은 레이어에서 발생한 예외를 최상단까지 전파할 때 중간 레이어가 모두 `throws`를 선언해야 해서 코드가 복잡해진다. 언체크 예외는 처리할 수 있는 곳에서만 잡고 나머지는 최상위 핸들러가 일괄 처리할 수 있어 코드가 간결하다.

**Q: finally가 필요한 이유는?**  
A: 예외 발생 여부와 무관하게 반드시 실행해야 하는 코드(자원 해제, 연결 종료 등)에 사용한다. 예외가 발생해 catch로 갔든 정상 실행됐든 finally는 항상 실행된다.
