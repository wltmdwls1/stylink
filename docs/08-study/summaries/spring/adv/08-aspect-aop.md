# @Aspect AOP

> Spring 고급편 | ADV

---

## 전체 흐름 한눈에 보기

```
챕터 7: Advisor를 @Bean으로 등록 → AnnotationAwareAspectJAutoProxyCreator가 자동 프록시 생성
  ↓
문제: Advisor 클래스를 직접 만들어야 함 (Pointcut 클래스 + Advice 클래스 + 조합)
  ↓
@Aspect: 애노테이션 하나로 Advisor를 간결하게 정의
  @Aspect 클래스 → AnnotationAwareAspectJAutoProxyCreator가 Advisor로 자동 변환
  @Around("포인트컷 표현식") 메서드 = Advice
  ProceedingJoinPoint = 실제 호출 대상 + 파라미터 + 메서드 정보 포함
  ↓
AnnotationAwareAspectJAutoProxyCreator의 2가지 역할:
  1. @Aspect → Advisor 변환 후 캐싱 (BeanFactoryAspectJAdvisorsBuilder)
  2. Advisor 기반 프록시 자동 생성
  ↓
다음: 스프링 AOP 개념 (챕터 9)
```

---

## @Aspect로 Advisor 정의

```java
@Slf4j
@Aspect // 이 클래스를 Advisor로 인식
public class LogTraceAspect {
    private final LogTrace logTrace;

    @Around("execution(* hello.proxy.app..*(..))")  // Pointcut 표현식
    public Object execute(ProceedingJoinPoint joinPoint) throws Throwable {
        // Advice 로직
        TraceStatus status = null;
        try {
            String message = joinPoint.getSignature().toShortString();
            status = logTrace.begin(message);
            Object result = joinPoint.proceed(); // target 호출
            logTrace.end(status);
            return result;
        } catch (Exception e) {
            logTrace.exception(status, e);
            throw e;
        }
    }
}
```

| 요소 | 역할 |
|------|------|
| `@Aspect` | 이 클래스가 Advisor임을 선언 |
| `@Around("expression")` | Pointcut 표현식 (AspectJ 문법) |
| `@Around` 메서드 | Advice (부가 기능 로직) |
| `ProceedingJoinPoint` | target, args, 메서드 시그니처 등 모든 호출 정보 포함 |
| `joinPoint.proceed()` | 실제 target 메서드 호출 (`MethodInvocation.proceed()`와 동일 역할) |

**`ProceedingJoinPoint` 주요 메서드:**

```java
joinPoint.getTarget()    // 실제 호출 대상 객체
joinPoint.getArgs()      // 전달 인수
joinPoint.getSignature() // 메서드 시그니처 (클래스명.메서드명() 등)
joinPoint.proceed()      // target 메서드 실행
```

---

## 등록 방법

```java
// 방법 1: @Bean으로 등록
@Configuration
public class AopConfig {
    @Bean
    public LogTraceAspect logTraceAspect(LogTrace logTrace) {
        return new LogTraceAspect(logTrace);
    }
}

// 방법 2: @Component 컴포넌트 스캔
@Aspect
@Component
public class LogTraceAspect { ... }
```

---

## @Aspect 동작 원리

`AnnotationAwareAspectJAutoProxyCreator`가 2가지 역할을 한다.

### 1단계: @Aspect → Advisor 변환 및 저장

```
1. 로딩 시점에 자동 프록시 생성기 호출
2. 스프링 컨테이너에서 @Aspect 붙은 빈 전체 조회
3. BeanFactoryAspectJAdvisorsBuilder가 @Aspect 정보로 Advisor 생성
4. 생성된 Advisor를 내부 캐시에 저장 (재사용)
```

`BeanFactoryAspectJAdvisorsBuilder`:
- `@Aspect` 클래스에서 Pointcut, Advice, Advisor를 만들고 캐싱
- 이미 만들어진 Advisor는 캐시에서 바로 반환

### 2단계: Advisor 기반 프록시 생성

```
1. 빈 대상 객체 생성 (@Bean, @Component 등)
2. 빈 후처리기에 전달
3-1. 스프링 컨테이너에서 Advisor 빈 전체 조회
3-2. @Aspect 어드바이저 빌더에서 변환된 Advisor 전체 조회
4. Pointcut으로 프록시 적용 여부 판단 (클래스 + 메서드 매칭)
5. 대상이면 프록시 생성 후 반환
6. 반환된 객체 빈으로 등록
```

---

## 챕터별 진화 요약

| 챕터 | 방법 | 개발자가 할 일 |
|------|------|---------------|
| 4 | 수동 프록시 클래스 작성 | 프록시 클래스 N개 직접 작성 |
| 5 | JDK 동적 프록시 / CGLIB | InvocationHandler or MethodInterceptor 작성 |
| 6 | ProxyFactory + Advisor | Advice + Pointcut + Advisor 클래스 작성 + 설정 |
| 7 | 빈 후처리기 + Advisor 빈 등록 | Advisor를 @Bean으로 등록 (빈 후처리기 자동 처리) |
| **8** | **@Aspect** | **@Aspect 클래스에 @Around 메서드만 작성** |

---

## stylink 적용 포인트

실무에서 AOP를 적용할 때 99%가 이 방식이다.

```java
@Aspect
@Component
public class LoggingAspect {
    @Around("execution(* com.stylink.domain..*(..))")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        // 실행 시간 측정, 로깅 등
        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            log.info("{} - {}ms",
                joinPoint.getSignature().toShortString(),
                System.currentTimeMillis() - start);
        }
    }
}
```

`@Transactional`도 스프링 내부에서 이와 동일한 방식으로 동작한다.  
트랜잭션 처리 Advisor가 `@Aspect`로 정의되어 있고, `AnnotationAwareAspectJAutoProxyCreator`가 자동으로 적용한다.

---

## 면접 Q&A

**Q: @Aspect 애노테이션은 어떻게 동작하나?**  
A: `AnnotationAwareAspectJAutoProxyCreator` 빈 후처리기가 `@Aspect`가 붙은 빈을 `BeanFactoryAspectJAdvisorsBuilder`를 통해 Advisor로 변환하고 캐싱한다. 이후 빈 등록 시점에 해당 Advisor의 Pointcut 조건을 체크해 필요한 빈을 프록시로 교체한다.

**Q: @Around 메서드와 ProceedingJoinPoint의 역할은?**  
A: `@Around("포인트컷 표현식")` 메서드가 Advice(부가 기능 로직)가 되고, 메서드 파라미터인 `ProceedingJoinPoint`에 target 객체, 전달 인수, 메서드 시그니처 등 호출 정보가 담긴다. `joinPoint.proceed()`를 호출해야 실제 target 메서드가 실행된다. 호출하지 않으면 target이 실행되지 않는다.

**Q: Advisor 클래스를 직접 만드는 것과 @Aspect의 차이는?**  
A: 결과는 동일하다. Advisor 직접 작성은 Pointcut 클래스, Advice 클래스를 각각 만들고 `DefaultPointcutAdvisor`로 조합해야 한다. `@Aspect`는 하나의 클래스에서 `@Around` 메서드 하나로 Pointcut 표현식과 Advice 로직을 함께 정의해 훨씬 간결하다. 내부적으로는 `AnnotationAwareAspectJAutoProxyCreator`가 Advisor로 변환해주는 것이다.

**Q: 횡단 관심사(cross-cutting concerns)란?**  
A: 로깅, 트랜잭션, 보안 체크처럼 여러 계층(Controller, Service, Repository)에 걸쳐 반복적으로 나타나는 부가 기능을 말한다. 각 클래스에 중복 코드를 넣지 않고 하나의 @Aspect로 집중해서 관리하는 것이 AOP의 핵심 목적이다.
