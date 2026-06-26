# 스프링 AOP - 실전 예제

> Spring 고급편 | ADV

---

## 핵심 패턴

> 커스텀 애노테이션 → @Aspect에서 `@annotation` 포인트컷으로 적용

---

## 로그 출력 AOP (@Trace)

### 애노테이션 정의

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Trace {}
```

### Aspect 구현

```java
@Slf4j
@Aspect
public class TraceAspect {
    @Before("@annotation(hello.aop.exam.annotation.Trace)")
    public void doTrace(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        log.info("[trace] {} args={}", joinPoint.getSignature(), args);
    }
}
```

### 적용

```java
@Service
public class ExamService {
    @Trace                            // ← 붙이기만 하면 자동 로그
    public void request(String itemId) { ... }
}

@Repository
public class ExamRepository {
    @Trace
    public String save(String itemId) { ... }
}
```

### 실행 결과

```
[trace] void ExamService.request(String) args=[data0]
[trace] String ExamRepository.save(String) args=[data0]
```

---

## 재시도 AOP (@Retry)

### 애노테이션 정의 (value 속성으로 횟수 설정)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retry {
    int value() default 3;
}
```

### Aspect 구현

```java
@Slf4j
@Aspect
public class RetryAspect {
    @Around("@annotation(retry)")  // 파라미터 이름과 일치
    public Object doRetry(ProceedingJoinPoint joinPoint, Retry retry) throws Throwable {
        int maxRetry = retry.value();
        Exception exceptionHolder = null;
        for (int retryCount = 1; retryCount <= maxRetry; retryCount++) {
            try {
                log.info("[retry] try count={}/{}", retryCount, maxRetry);
                return joinPoint.proceed();
            } catch (Exception e) {
                exceptionHolder = e;
            }
        }
        throw exceptionHolder;
    }
}
```

**핵심**: `@annotation(retry)` + `Retry retry` 파라미터 → 애노테이션 인스턴스를 어드바이스에 전달  
`retry.value()`로 설정된 재시도 횟수 접근.

### 적용

```java
@Trace
@Retry(value = 4)  // 최대 4번 재시도
public String save(String itemId) { ... }
```

### 테스트 등록

```java
@Import({TraceAspect.class, RetryAspect.class})
@SpringBootTest
public class ExamTest { }
```

---

## @annotation 파라미터 바인딩 패턴 (핵심)

```java
// 포인트컷 표현식의 이름 == 메서드 파라미터 이름이어야 함
@Around("@annotation(retry)")                // "retry"
public Object doRetry(..., Retry retry) { // "retry"
    retry.value(); // 애노테이션 속성 접근 가능
}
```

---

## stylink 적용 포인트

### 재시도 AOP 패턴 (외부 API 호출 시 유용)

```java
// 결제 API, 알림 API 등 Mock 호출에 적용 가능
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retry {
    int value() default 3;
}

// PG Mock 연동 메서드
@Retry(value = 3)
public PaymentResult requestPayment(PaymentRequest request) { ... }
```

### 로그 추적 AOP 패턴 (핵심 비즈니스 메서드에 적용)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Loggable {}

// InventoryService 핵심 메서드에
@Loggable
public void hold(Long inventoryId) { ... }

// Aspect
@Before("@annotation(com.stylink.common.annotation.Loggable)")
public void trace(JoinPoint joinPoint) {
    log.info("[{}] args={}", joinPoint.getSignature().toShortString(), joinPoint.getArgs());
}
```

---

## 면접 Q&A

**Q: 커스텀 AOP 애노테이션을 어떻게 구현하나?**  
A: `@Target(ElementType.METHOD)`, `@Retention(RetentionPolicy.RUNTIME)`으로 메서드에 붙이고 런타임에 유지되는 애노테이션을 정의한다. `@Aspect` 클래스에서 `@annotation(annotationParamName)` 포인트컷을 사용하고, 어드바이스 메서드에 동일한 이름으로 파라미터를 선언하면 애노테이션 인스턴스가 전달된다. `annotation.value()`처럼 속성에 접근할 수 있다.

**Q: @Retry AOP에서 재시도 횟수를 메서드마다 다르게 설정할 수 있나?**  
A: 애노테이션에 `int value() default 3` 같이 속성을 정의하면 된다. `@Retry(value = 5)` 처럼 메서드마다 다른 값을 설정할 수 있고, Aspect에서 `retry.value()`로 읽어서 사용한다.
