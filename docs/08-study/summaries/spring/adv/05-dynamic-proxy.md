# 동적 프록시 기술

> Spring 고급편 | ADV

---

## 전체 흐름 한눈에 보기

```
챕터 4 한계: 클래스 N개 → 프록시 N개 직접 작성
  ↓
리플렉션: 메서드를 Method 메타정보로 추상화 → 동적 호출 가능
  BUT 런타임 오류 → 컴파일에서 못 잡음 → 일반 사용 금지
  ↓
JDK 동적 프록시 (인터페이스 필수)
  InvocationHandler.invoke() 1개 구현
  Proxy.newProxyInstance()로 런타임에 프록시 자동 생성
  LogTraceBasicHandler → LogTraceFilterHandler (메서드 이름 필터)
  한계: 인터페이스 없는 클래스엔 불가
  ↓
CGLIB (인터페이스 없어도 됨)
  MethodInterceptor.intercept() 1개 구현
  Enhancer로 구체 클래스 상속해 런타임 프록시 생성
  제약: 기본 생성자 필요, final 클래스/메서드 불가
  ↓
남은 문제: JDK/CGLIB 자동 선택? InvocationHandler/MethodInterceptor 중복?
→ ProxyFactory (챕터 6)로 해결
```

---

## 리플렉션

**문제**: callA()와 callB()는 패턴이 같은데 호출 메서드만 달라서 공통화 불가

```java
// 리플렉션으로 메서드를 Method 메타정보로 추상화
Class classHello = Class.forName("hello.proxy...ReflectionTest$Hello");
Method methodCallA = classHello.getMethod("callA");
Object result = methodCallA.invoke(target); // target.callA() 동적 호출

// 공통 로직으로 추출 가능해짐
private void dynamicCall(Method method, Object target) throws Exception {
    log.info("start");
    Object result = method.invoke(target);
    log.info("result={}", result);
}
```

### 리플렉션 주의사항

```
컴파일 오류 없음 — getMethod("callZ") 오타도 런타임에야 예외 발생
→ 일반적으로 사용 금지
→ 프레임워크 개발, 매우 일반적인 공통 처리에만 사용
```

> JDK 동적 프록시가 내부적으로 리플렉션을 사용 — 우리가 직접 쓸 일은 거의 없음

---

## JDK 동적 프록시

**조건**: 인터페이스가 필수  
**핵심**: `InvocationHandler` 1개 구현 → 모든 인터페이스 기반 프록시에 공통 적용

```java
public interface InvocationHandler {
    Object invoke(Object proxy, Method method, Object[] args) throws Throwable;
    // proxy: 프록시 자신 / method: 호출된 메서드 / args: 인수
}
```

### TimeInvocationHandler 구현

```java
public class TimeInvocationHandler implements InvocationHandler {
    private final Object target;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = method.invoke(target, args); // 리플렉션으로 실제 호출
        long resultTime = System.currentTimeMillis() - startTime;
        log.info("resultTime={}ms", resultTime);
        return result;
    }
}
```

### 프록시 생성 및 사용

```java
AInterface target = new AImpl();
InvocationHandler handler = new TimeInvocationHandler(target);

// 런타임에 동적 프록시 생성 — AInterface를 구현한 $Proxy1 자동 생성
AInterface proxy = (AInterface) Proxy.newProxyInstance(
    AInterface.class.getClassLoader(),
    new Class[]{AInterface.class},
    handler
);
proxy.call();
// targetClass=class AImpl
// proxyClass=class com.sun.proxy.$Proxy1
```

**실행 순서**: client → `$Proxy1.call()` → `handler.invoke()` → `method.invoke(target)` → `AImpl.call()`

**핵심 효과**:
- AImpl, BImpl 각각 프록시 클래스 작성 불필요
- `TimeInvocationHandler` 1개로 모든 인터페이스에 공통 적용 (SRP)

---

## JDK 동적 프록시 실제 적용

### LogTraceBasicHandler

```java
public class LogTraceBasicHandler implements InvocationHandler {
    private final Object target;
    private final LogTrace logTrace;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        TraceStatus status = null;
        try {
            // 클래스명.메서드명() 동적 획득 — 프록시마다 하드코딩 불필요
            String message = method.getDeclaringClass().getSimpleName()
                    + "." + method.getName() + "()";
            status = logTrace.begin(message);
            Object result = method.invoke(target, args);
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
// 설정 파일 — 동적 프록시를 스프링 빈으로 등록
@Bean
public OrderControllerV1 orderControllerV1(LogTrace logTrace) {
    OrderControllerV1 impl = new OrderControllerV1Impl(orderServiceV1(logTrace));
    return (OrderControllerV1) Proxy.newProxyInstance(
        OrderControllerV1.class.getClassLoader(),
        new Class[]{OrderControllerV1.class},
        new LogTraceBasicHandler(impl, logTrace)
    );
}
```

### LogTraceFilterHandler — 메서드 이름 필터

```java
public class LogTraceFilterHandler implements InvocationHandler {
    private final String[] patterns;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 패턴 불일치 → 로그 없이 바로 실행
        if (!PatternMatchUtils.simpleMatch(patterns, method.getName())) {
            return method.invoke(target, args);
        }
        // 패턴 일치 → LogTrace 적용 ...
    }
}

// 설정: request*, order*, save* 패턴에만 로그 적용
private static final String[] PATTERNS = {"request*", "order*", "save*"};
// noLog() → 패턴 불일치 → 로그 없이 통과
```

**PatternMatchUtils.simpleMatch 패턴**:

| 패턴 | 의미 |
|------|------|
| `xxx` | 정확히 일치 |
| `xxx*` | xxx로 시작 |
| `*xxx` | xxx로 끝남 |
| `*xxx*` | xxx 포함 |

---

## CGLIB

**조건**: 인터페이스 없어도 됨 (구체 클래스 상속)  
**스프링 내장**: 별도 의존성 추가 불필요  
**핵심**: `MethodInterceptor` 1개 구현

```java
package org.springframework.cglib.proxy;
public interface MethodInterceptor extends Callback {
    Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
            throws Throwable;
    // obj: CGLIB 적용 객체 / method: 호출 메서드 / args: 인수
    // proxy: 실제 호출에 사용 (MethodProxy — 성능상 method 대신 권장)
}
```

### TimeMethodInterceptor 구현

```java
public class TimeMethodInterceptor implements MethodInterceptor {
    private final Object target;

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
            throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = proxy.invoke(target, args); // MethodProxy 사용 (성능)
        long resultTime = System.currentTimeMillis() - startTime;
        log.info("resultTime={}ms", resultTime);
        return result;
    }
}
```

### 프록시 생성 및 사용

```java
ConcreteService target = new ConcreteService();

Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(ConcreteService.class); // 상속할 클래스 지정
enhancer.setCallback(new TimeMethodInterceptor(target)); // 실행 로직
ConcreteService proxy = (ConcreteService) enhancer.create();

proxy.call();
// targetClass=class ConcreteService
// proxyClass=class ConcreteService$$EnhancerByCGLIB$$25d6b0e3
```

**CGLIB 클래스명 규칙**: `대상클래스$$EnhancerByCGLIB$$임의코드`

### CGLIB 제약

| 제약 | 이유 | 결과 |
|------|------|------|
| 기본 생성자 필요 | 자식 클래스 동적 생성 시 부모 생성자 호출 | 기본 생성자 없으면 오류 |
| `final` 클래스 불가 | 상속 불가 | 예외 발생 |
| `final` 메서드 불가 | 오버라이딩 불가 | 프록시 로직 동작 안 함 |

---

## JDK 동적 프록시 vs CGLIB

| | JDK 동적 프록시 | CGLIB |
|---|---|---|
| **조건** | 인터페이스 필수 | 구체 클래스만 있어도 됨 |
| **방식** | 인터페이스 `implement` | 구체 클래스 `extends` |
| **핸들러** | `InvocationHandler` | `MethodInterceptor` |
| **생성 방식** | `Proxy.newProxyInstance()` | `Enhancer.create()` |
| **클래스명** | `com.sun.proxy.$Proxy1` | `Xxx$$EnhancerByCGLIB$$xxx` |
| **제약** | 인터페이스 없으면 불가 | final, 기본 생성자 |

---

## 남은 문제 → 챕터 6 ProxyFactory

```
문제 1: 인터페이스 있으면 JDK, 없으면 CGLIB — 자동 선택 불가?
문제 2: InvocationHandler(JDK) vs MethodInterceptor(CGLIB) — 중복 구현?
문제 3: 특정 조건에만 프록시 로직 적용 — 공통화 불가?
→ 해결: 스프링 ProxyFactory (챕터 6)
         Advice(공통 로직) + Pointcut(조건) + Advisor 개념
```

---

## stylink 적용 포인트

**이 챕터 내용을 직접 사용하지 않음** — ProxyFactory/AOP가 추상화해주기 때문

**하지만 반드시 알아야 하는 이유**:
- `@Transactional` = 스프링이 JDK 동적 프록시 또는 CGLIB로 만든 프록시
- 인터페이스 없는 서비스 클래스에 `@Transactional`이 동작하는 이유: CGLIB
- "왜 `final` 메서드에 `@Transactional`이 동작하지 않나" = CGLIB 제약
- 스프링 AOP가 "왜 같은 클래스 내부 메서드 호출에 AOP가 안 되나" = 프록시 외부 호출만 가로챔

---

## 면접 Q&A

**Q: JDK 동적 프록시와 CGLIB의 차이는?**  
A: JDK 동적 프록시는 인터페이스를 기반으로, CGLIB는 구체 클래스를 상속받아 프록시를 생성한다. JDK는 `InvocationHandler`를, CGLIB는 `MethodInterceptor`를 구현해 로직을 작성한다. 스프링은 인터페이스가 있으면 JDK 동적 프록시, 없으면 CGLIB를 자동으로 선택한다.

**Q: 리플렉션을 일반적으로 사용하면 안 되는 이유는?**  
A: 리플렉션은 런타임에 동작하기 때문에 컴파일 시점에 오류를 잡을 수 없다. 메서드 이름 오타(`getMethod("callZ")`)도 컴파일 오류 없이 런타임에야 예외가 발생한다. 프레임워크 내부나 극히 일반적인 공통 처리에만 부분적으로 사용해야 한다.

**Q: `@Transactional`이 붙은 클래스에 인터페이스가 없어도 동작하는 이유는?**  
A: 스프링이 내부적으로 CGLIB를 사용해 구체 클래스를 상속하는 프록시를 자동 생성하기 때문이다. 단 `final` 클래스나 `final` 메서드에는 CGLIB 제약으로 적용되지 않는다.

**Q: CGLIB의 제약 조건은?**  
A: 세 가지다. 첫째, 부모 클래스의 기본 생성자가 필요하다. 둘째, `final` 클래스는 상속이 불가능해 예외가 발생한다. 셋째, `final` 메서드는 오버라이딩이 불가능해 프록시 로직이 동작하지 않는다. 스프링 `ProxyFactory`가 이런 단점을 추상화해서 편리하게 사용할 수 있게 해준다.
