# 스프링 AOP 구현

> Spring 고급편 | ADV

---

## 전체 흐름 한눈에 보기

```
AOP 구현1 (AspectV1): @Aspect + @Around("expression") → 기본 형태
  ↓
AOP 구현2 (AspectV2): @Pointcut으로 포인트컷 표현식 분리 → 재사용
  ↓
AOP 구현3 (AspectV3): 여러 @Pointcut 정의 + &&/||/! 조합
  @Around("allOrder() && allService()") → 교집합 대상에만 적용
  ↓
AOP 구현4 (AspectV4): 포인트컷 외부 클래스(Pointcuts)로 분리
  @Around("hello.aop.order.aop.Pointcuts.allOrder()") → 전체 경로 명시
  ↓
AOP 구현5 (AspectV5): 어드바이스 순서
  @Order는 클래스 단위 → Aspect를 내부 static 클래스로 분리 후 @Order 지정
  ↓
AOP 구현6 (AspectV6): 어드바이스 5종
  @Around / @Before / @AfterReturning / @AfterThrowing / @After
```

---

## 의존성 및 등록 방법

```groovy
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

@Aspect 클래스는 **컴포넌트 스캔 대상이 아님** — 반드시 등록 필요:

```java
// 방법 1
@Bean public AspectV1 aspectV1() { return new AspectV1(); }

// 방법 2
@Component @Aspect public class AspectV1 { ... }

// 방법 3 (테스트에서)
@Import(AspectV1.class) @SpringBootTest class AopTest { ... }
```

---

## 구현1 — 기초 (@Around + 표현식 직접)

```java
@Slf4j
@Aspect
public class AspectV1 {
    @Around("execution(* hello.aop.order..*(..))") // hello.aop.order 패키지 + 하위 패키지
    public Object doLog(ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("[log] {}", joinPoint.getSignature());
        return joinPoint.proceed();
    }
}
```

---

## 구현2 — 포인트컷 분리 (@Pointcut)

```java
@Aspect
public class AspectV2 {
    @Pointcut("execution(* hello.aop.order..*(..))")
    private void allOrder(){} // ← 포인트컷 시그니처 (이름처럼 사용)

    @Around("allOrder()")     // ← 시그니처로 참조
    public Object doLog(ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("[log] {}", joinPoint.getSignature());
        return joinPoint.proceed();
    }
}
```

- `@Pointcut` 메서드 반환 타입 `void` 필수, 코드 내용 비움
- 같은 클래스 내: `private` 가능
- 다른 클래스(Aspect)에서 참조: `public` 필요

---

## 구현3 — 여러 포인트컷 조합

```java
@Aspect
public class AspectV3 {
    @Pointcut("execution(* hello.aop.order..*(..))") // 패키지 기준
    public void allOrder(){}

    @Pointcut("execution(* *..*Service.*(..))") // 클래스 이름 *Service
    private void allService(){}

    @Around("allOrder()")
    public Object doLog(ProceedingJoinPoint joinPoint) throws Throwable { ... }

    @Around("allOrder() && allService()") // OrderService만 해당
    public Object doTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            log.info("[트랜잭션 시작] {}", joinPoint.getSignature());
            Object result = joinPoint.proceed();
            log.info("[트랜잭션 커밋] {}", joinPoint.getSignature());
            return result;
        } catch (Exception e) {
            log.info("[트랜잭션 롤백] {}", joinPoint.getSignature());
            throw e;
        } finally {
            log.info("[리소스 릴리즈] {}", joinPoint.getSignature());
        }
    }
}
```

| 대상 | 적용 어드바이스 |
|------|----------------|
| `OrderService` | `doLog()` + `doTransaction()` |
| `OrderRepository` | `doLog()` 만 |

포인트컷 조합 연산자: `&&` (AND), `||` (OR), `!` (NOT)

---

## 구현4 — 포인트컷 외부 클래스 분리

```java
// Pointcuts.java
public class Pointcuts {
    @Pointcut("execution(* hello.aop.order..*(..))") public void allOrder(){}
    @Pointcut("execution(* *..*Service.*(..))") public void allService(){}
    @Pointcut("allOrder() && allService()") public void orderAndService(){}
}

// 사용 시 전체 경로 지정
@Around("hello.aop.order.aop.Pointcuts.orderAndService()")
```

---

## 구현5 — 어드바이스 순서

**@Order는 클래스 단위**만 적용 가능 — Aspect 안의 메서드 단위 순서 지정 불가.

```java
public class AspectV5Order {
    @Aspect
    @Order(1) // 숫자 작을수록 먼저 실행 (= 프록시 체인에서 바깥)
    public static class TxAspect {
        @Around("Pointcuts.orderAndService()")
        public Object doTransaction(ProceedingJoinPoint joinPoint) throws Throwable { ... }
    }

    @Aspect
    @Order(2) // TxAspect 다음에 실행
    public static class LogAspect {
        @Around("Pointcuts.allOrder()")
        public Object doLog(ProceedingJoinPoint joinPoint) throws Throwable { ... }
    }
}
```

```java
@Import({AspectV5Order.LogAspect.class, AspectV5Order.TxAspect.class})
```

호출 순서: `TxAspect(@Order=1)` → `LogAspect(@Order=2)` → `target`

---

## 구현6 — 어드바이스 5종

```java
@Aspect
public class AspectV6Advice {
    // 전후 모두 처리, proceed() 필수
    @Around("Pointcuts.orderAndService()")
    public Object doTransaction(ProceedingJoinPoint joinPoint) throws Throwable { ... }

    // 조인 포인트 실행 전
    @Before("Pointcuts.orderAndService()")
    public void doBefore(JoinPoint joinPoint) { ... }

    // 정상 반환 후 (returning 속성 이름 == 파라미터 이름)
    @AfterReturning(value = "Pointcuts.orderAndService()", returning = "result")
    public void doReturn(JoinPoint joinPoint, Object result) { ... }

    // 예외 발생 후 (throwing 속성 이름 == 파라미터 이름)
    @AfterThrowing(value = "Pointcuts.orderAndService()", throwing = "ex")
    public void doThrowing(JoinPoint joinPoint, Exception ex) { ... }

    // 정상/예외 무관 항상 실행 (finally와 동일)
    @After(value = "Pointcuts.orderAndService()")
    public void doAfter(JoinPoint joinPoint) { ... }
}
```

### 실행 순서 (정상)

```
@Around (before) → @Before → target 실행 → @AfterReturning → @After → @Around (after)
```

### 실행 순서 (예외)

```
@Around (before) → @Before → target 실행 → @AfterThrowing → @After → @Around (catch)
```

### JoinPoint / ProceedingJoinPoint 주요 메서드

| 메서드 | 설명 |
|--------|------|
| `getArgs()` | 메서드 인수 |
| `getThis()` | 프록시 객체 |
| `getTarget()` | 실제 대상 객체 |
| `getSignature()` | 실행 메서드 시그니처 |
| `proceed()` | 다음 어드바이스 또는 타겟 호출 (@Around 전용) |

- `@Around`에서만 `ProceedingJoinPoint` 사용
- 나머지 어드바이스는 `JoinPoint` 사용 (proceed() 없음 — 자동으로 target 호출)

---

## @Around 하나면 되는데 나머지가 필요한 이유

```java
// @Around인데 proceed()를 빠뜨리면 → target 미실행 → 치명적 버그
@Around("...")
public void doBefore(ProceedingJoinPoint joinPoint) {
    log.info("[before] {}", joinPoint.getSignature());
    // proceed() 빠짐! → target 실행 안 됨
}

// @Before는 proceed() 자체가 없으므로 이런 실수 불가
@Before("...")
public void doBefore(JoinPoint joinPoint) {
    log.info("[before] {}", joinPoint.getSignature());
    // 자동으로 target 호출됨
}
```

**제약이 오히려 설계를 명확하게 만든다.**
- `@Before` → 이 코드는 전처리만 한다는 의도 명확
- `@AfterReturning` → 정상 반환 후에만 동작한다는 의도 명확
- 다른 개발자가 코드를 보고 역할을 바로 파악 가능

---

## stylink 적용 포인트

```java
@Aspect
@Component
public class LoggingAspect {
    @Around("execution(* com.stylink.domain..*(..))")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            log.info("[{}] {}ms", joinPoint.getSignature().toShortString(),
                System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("[{}] 예외: {}", joinPoint.getSignature(), e.getMessage());
            throw e;
        }
    }
}
```

여러 어드바이스 순서 예시 (트랜잭션 + 로깅):
```java
@Order(1) // 트랜잭션이 바깥 (먼저 열고 나중에 닫음)
class TxAspect { ... }

@Order(2) // 로깅이 안쪽
class LoggingAspect { ... }
```

---

## 면접 Q&A

**Q: @Pointcut의 역할과 활용법은?**  
A: `@Pointcut`은 포인트컷 표현식을 메서드 형태로 정의해 재사용할 수 있게 한다. 메서드 반환 타입은 `void`, 코드 내용은 비워둔다. 같은 클래스 내에서는 `private`, 다른 Aspect에서 참조하려면 `public`으로 선언한다. 외부 참조 시 전체 패키지 경로를 포함해 `hello.aop.order.aop.Pointcuts.allOrder()`처럼 사용한다.

**Q: 여러 Aspect의 실행 순서를 어떻게 제어하나?**  
A: `@Order` 애노테이션을 사용하는데, 클래스 단위로만 적용 가능하다. 따라서 하나의 Aspect 클래스에 여러 Advice가 있을 때 순서 지정이 필요하면 Aspect를 별도 클래스(또는 static inner class)로 분리하고 각각에 `@Order`를 붙인다. 숫자가 작을수록 먼저 실행된다 (프록시 체인에서 바깥쪽).

**Q: @Around 하나로 모든 처리가 가능한데 @Before, @AfterReturning 등을 별도로 사용하는 이유는?**  
A: 두 가지 이유가 있다. 첫째, 코드 의도가 명확해진다 — `@Before`를 보면 "조인 포인트 실행 전에만 동작하는 코드"임을 바로 알 수 있다. 둘째, 실수를 방지한다 — `@Around`에서 `proceed()`를 빠뜨리면 target이 실행되지 않는 치명적 버그가 생기지만, `@Before`는 `proceed()`가 없어도 자동으로 target을 호출한다. 제약이 코드의 안전성을 높인다.

**Q: @AfterReturning에서 반환값을 변경할 수 있나?**  
A: 조회만 가능하고 변경은 불가하다. 반환값을 변경하려면 `@Around`를 사용해야 한다. `@AfterReturning`의 `returning` 속성으로 반환값을 받아 확인할 수 있지만, 그 값을 다른 것으로 바꿔서 반환하는 기능은 없다.
