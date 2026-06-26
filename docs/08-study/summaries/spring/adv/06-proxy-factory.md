# 스프링이 지원하는 프록시

> Spring 고급편 | ADV

---

## 전체 흐름 한눈에 보기

```
챕터 5 남은 문제:
  인터페이스 있으면 JDK, 없으면 CGLIB → 조건부 선택 필요
  InvocationHandler(JDK) / MethodInterceptor(CGLIB) → 중복 구현
  메서드 필터 로직 → 공통화 불가
  ↓
ProxyFactory: JDK/CGLIB 자동 선택 추상화
  target에 인터페이스 있음 → JDK 동적 프록시 자동 사용
  target에 인터페이스 없음 → CGLIB 자동 사용
  proxyTargetClass=true → 항상 CGLIB (스프링 부트 기본값)
  ↓
Advice: 공통 부가 기능 (org.aopalliance.intercept.MethodInterceptor 구현)
  target 정보 없이 invocation.proceed()만 호출
  ↓
Pointcut: 어디에 적용할지 필터 (ClassFilter + MethodMatcher)
Advisor = Pointcut + Advice (1:1 조합)
  ↓
하나의 프록시에 여러 Advisor → target당 프록시 1개 (AOP 오해 주의!)
  ↓
남은 문제: 설정 파일 폭발 + 컴포넌트 스캔 불가 → 챕터 7 빈 후처리기
```

---

## ProxyFactory

챕터 5에서 JDK는 `InvocationHandler`, CGLIB는 `MethodInterceptor`를 각각 구현해야 했다.  
`ProxyFactory`는 이 선택과 구현을 추상화해 **Advice 하나로 양쪽을 커버**한다.

```java
// 인터페이스 있는 target → JDK 동적 프록시 자동 적용
ServiceInterface target = new ServiceImpl();
ProxyFactory proxyFactory = new ProxyFactory(target);
proxyFactory.addAdvice(new TimeAdvice()); // JDK/CGLIB 무관하게 동일 Advice
ServiceInterface proxy = (ServiceInterface) proxyFactory.getProxy();

// AopUtils로 프록시 종류 확인
AopUtils.isAopProxy(proxy)        // true (JDK or CGLIB 모두)
AopUtils.isJdkDynamicProxy(proxy) // true  ← 인터페이스 있으면
AopUtils.isCglibProxy(proxy)      // false
```

### proxyTargetClass 옵션

```java
proxyFactory.setProxyTargetClass(true); // 인터페이스 있어도 CGLIB 강제
```

| 조건 | 프록시 기술 |
|------|------------|
| 인터페이스 있음 (기본) | JDK 동적 프록시 |
| 인터페이스 없음 | CGLIB |
| `proxyTargetClass=true` | CGLIB (인터페이스 여부 무관) |
| **스프링 부트 AOP 기본값** | **proxyTargetClass=true → 항상 CGLIB** |

---

## Advice 만들기

Advice는 `org.aopalliance.intercept.MethodInterceptor`를 구현한다.

> 주의: CGLIB의 `MethodInterceptor`(`org.springframework.cglib.proxy`)와 **패키지가 다름**

```java
public class TimeAdvice implements MethodInterceptor {
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = invocation.proceed(); // target 호출 (target은 invocation 안에 있음)
        log.info("resultTime={}ms", System.currentTimeMillis() - startTime);
        return result;
    }
}
```

**target을 Advice에서 주입받지 않는 이유**: `ProxyFactory(target)` 생성 시 target이 넘어가고,  
`MethodInvocation` 안에 proxy, method, args, target이 모두 포함되어 있기 때문.

---

## Pointcut, Advice, Advisor

```
Pointcut  = 어디에 (ClassFilter + MethodMatcher)
Advice    = 무엇을 (부가 기능 로직)
Advisor   = Pointcut 1 + Advice 1 ("어디에 어떤 조언을")
```

### Pointcut 인터페이스

```java
public interface Pointcut {
    ClassFilter getClassFilter();      // 클래스 매칭
    MethodMatcher getMethodMatcher();  // 메서드 매칭
}
// 둘 다 true여야 Advice 적용
```

`MethodMatcher.isRuntime()`:
- `false` (정적): `matches(method, clazz)` 호출, 결과 캐싱
- `true` (동적): `matches(method, clazz, args)` 호출, 매 요청마다 실행

### Advisor 사용

```java
// Advisor = Pointcut + Advice
DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(Pointcut.TRUE, new TimeAdvice());
proxyFactory.addAdvisor(advisor);

// addAdvice()는 편의 메서드 — 내부적으로 DefaultPointcutAdvisor(Pointcut.TRUE, advice) 생성
proxyFactory.addAdvice(new TimeAdvice()); // 위와 동일
```

### 직접 만든 Pointcut

```java
NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
pointcut.setMappedNames("save"); // save 메서드만 Advice 적용

DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, new TimeAdvice());
proxyFactory.addAdvisor(advisor);
// save() → Advice 적용
// find() → Advice 미적용, target.find() 직접 호출
```

---

## 스프링이 제공하는 Pointcut

| Pointcut | 설명 |
|----------|------|
| `NameMatchMethodPointcut` | 메서드 이름 기반 (`*xxx*` 와일드카드) |
| `JdkRegexpMethodPointcut` | JDK 정규 표현식 |
| `TruePointcut` | 항상 참 |
| `AnnotationMatchingPointcut` | 애노테이션 기반 |
| **`AspectJExpressionPointcut`** | **AspectJ 표현식 — 실무 표준** |

---

## 여러 Advisor — 하나의 프록시

### 잘못된 방법 (프록시 N개)

```
proxy2(advisor2) → proxy1(advisor1) → target
// AOP N개면 프록시 N개 → 비효율
```

### 올바른 방법 (프록시 1개)

```java
proxyFactory.addAdvisor(advisor2); // 등록 순서대로 호출
proxyFactory.addAdvisor(advisor1);
ServiceInterface proxy = (ServiceInterface) proxyFactory.getProxy();

// 호출 순서: proxy → advisor2 → advisor1 → target
```

**핵심**: 스프링 AOP는 **target당 프록시 1개**를 만들고, 여러 Advisor를 그 안에 담는다.  
"AOP 적용 수만큼 프록시가 생성된다"는 것은 오해다.

---

## 실제 적용 (LogTraceAdvice)

```java
public class LogTraceAdvice implements MethodInterceptor {
    private final LogTrace logTrace;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        TraceStatus status = null;
        try {
            Method method = invocation.getMethod();
            String message = method.getDeclaringClass().getSimpleName()
                    + "." + method.getName() + "()";
            status = logTrace.begin(message);
            Object result = invocation.proceed();
            logTrace.end(status);
            return result;
        } catch (Exception e) {
            logTrace.exception(status, e);
            throw e;
        }
    }
}
```

```java
// 설정 파일 — NameMatchMethodPointcut으로 noLog() 제외
private Advisor getAdvisor(LogTrace logTrace) {
    NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
    pointcut.setMappedNames("request*", "order*", "save*"); // noLog() 패턴 불일치 → 미적용
    return new DefaultPointcutAdvisor(pointcut, new LogTraceAdvice(logTrace));
}

@Bean
public OrderControllerV1 orderControllerV1(LogTrace logTrace) {
    OrderControllerV1Impl impl = new OrderControllerV1Impl(orderServiceV1(logTrace));
    ProxyFactory factory = new ProxyFactory(impl);
    factory.addAdvisor(getAdvisor(logTrace));
    return (OrderControllerV1) factory.getProxy(); // JDK 동적 프록시 ($Proxy50 등)
}
// V2 (구체 클래스): factory.getProxy() → CGLIB (Xxx$$EnhancerBySpringCGLIB 등)
```

---

## 남은 문제 → 챕터 7 빈 후처리기

```
문제1 — 설정 파일 폭발:
  빈 100개 → 설정 파일에서 프록시 100개 수동 생성
  → 빈이 늘수록 설정도 그만큼 늘어남

문제2 — 컴포넌트 스캔 대상에 미적용:
  @Component로 등록된 빈은 이미 실제 객체가 스프링 컨테이너에 올라감
  → 설정 파일로 프록시 교체 불가

→ 해결: 빈 후처리기 (챕터 7)
  빈 등록 시점에 후처리기가 실제 객체를 프록시로 바꿔치기
```

---

## stylink 적용 포인트

`ProxyFactory`를 직접 쓰지 않더라도, 이 챕터의 개념은 스프링 AOP 전체의 기반이다.

| 현상 | 이유 |
|------|------|
| `@Transactional` 클래스에 인터페이스 없어도 동작 | 스프링 부트가 CGLIB 기본 적용 |
| `final` 메서드에 `@Transactional` 안 됨 | CGLIB는 오버라이딩으로 프록시 생성 → final 불가 |
| 같은 클래스 내부 메서드 호출 시 AOP 미적용 | 프록시를 거치지 않는 내부 호출 → 챕터 15 주의사항 |
| `@Cacheable`, `@Async` 동일 제약 | 모두 프록시 기반 |

stylink 서비스에서 `InventoryService.hold()`나 `OrderService.order()`에 `@Transactional`을 붙이면  
스프링이 자동으로 프록시를 생성해 트랜잭션 경계를 처리한다 — 이 챕터가 그 내부 원리다.

---

## 면접 Q&A

**Q: ProxyFactory가 JDK 동적 프록시와 CGLIB 중 무엇을 선택하는 기준은?**  
A: target 클래스에 인터페이스가 있으면 JDK 동적 프록시, 없으면 CGLIB를 선택한다. `proxyTargetClass=true`로 설정하면 인터페이스 여부에 관계없이 CGLIB를 사용한다. 스프링 부트는 이 옵션이 기본값이라 항상 CGLIB로 동작한다.

**Q: Advice, Pointcut, Advisor의 관계는?**  
A: Pointcut은 "어디에 적용할지" 필터링(ClassFilter + MethodMatcher), Advice는 "무엇을 실행할지" 부가 기능 로직이다. Advisor는 Pointcut 1개와 Advice 1개를 묶은 단위로, "어디에 어떤 조언을 할지" 담고 있다. ProxyFactory에 Advisor를 등록하면 Pointcut 조건이 맞는 메서드에만 Advice가 적용된다.

**Q: 스프링 AOP를 적용할 때 AOP 개수만큼 프록시가 생성되나?**  
A: 아니다. 스프링 AOP는 target마다 프록시를 1개만 생성하고, 그 안에 여러 Advisor를 담는다. 예를 들어 `@Transactional`과 `@Cacheable`을 동시에 붙여도 프록시는 1개이고, 두 Advisor가 순서대로 체인을 이룬다.

**Q: `MethodMatcher.isRuntime()`이 무엇이고 언제 쓰나?**  
A: `isRuntime()`이 `false`면 정적 매칭 — `matches(method, clazz)`만 호출하고 결과를 캐싱해 성능에 유리하다. `true`면 동적 매칭 — 매 요청마다 `matches(method, clazz, args)`를 호출해 런타임 파라미터 기반 조건 분기가 가능하다. 대부분의 경우 정적 매칭으로 충분하고, 실무에서는 AspectJ 표현식 Pointcut을 쓰므로 직접 구현할 일은 거의 없다.
