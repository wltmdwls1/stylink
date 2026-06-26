# 스프링 AOP - 포인트컷

> Spring 고급편 | ADV

---

## 포인트컷 지시자(PCD) 목록

| 지시자 | 매칭 기준 | 비고 |
|--------|-----------|------|
| **`execution`** | 메서드 실행 | 가장 많이 사용, 기능 가장 복잡 |
| `within` | 특정 타입 안의 메서드 | execution 타입 부분만 |
| `args` | 런타임 인수 타입 | 부모 타입 허용, 동적 판단 |
| `this` | AOP 프록시 객체 타입 | 파라미터 바인딩에 주로 사용 |
| `target` | 실제 target 객체 타입 | 파라미터 바인딩에 주로 사용 |
| `@target` | 실행 객체 클래스의 애노테이션 | 부모 메서드까지 포함 |
| `@within` | 타입의 애노테이션 | 해당 타입에 정의된 메서드만 |
| `@annotation` | 메서드의 애노테이션 | 실무에서 자주 사용 |
| `@args` | 인수 런타임 타입의 애노테이션 | 단독 사용 금지 |
| `bean` | 스프링 빈 이름 | 스프링 전용 |

---

## execution 문법

```
execution(접근제어자? 반환타입 선언타입?메서드이름(파라미터) 예외?)
```

?가 붙은 항목은 생략 가능.

### 예시 모음

```java
// 가장 정확한 매칭
execution(public String hello.aop.member.MemberServiceImpl.hello(String))

// 가장 생략된 매칭 (모든 메서드)
execution(* *(..))

// 메서드 이름 와일드카드
execution(* hello(..))    // 정확히 hello
execution(* hel*(..))     // hel로 시작
execution(* *el*(..))     // el 포함
```

### 패키지 매칭 (`.` vs `..`)

```java
execution(* hello.aop.member.*.*(..))  // hello.aop.member 패키지만
execution(* hello.aop.*.*(..))         // hello.aop 패키지만 (하위 제외)
execution(* hello.aop.member..*.*(..)) // hello.aop.member + 하위 패키지
execution(* hello.aop..*.*(..))        // hello.aop + 모든 하위 패키지
```

| 표현 | 의미 |
|------|------|
| `.` | 정확히 해당 위치의 패키지 |
| `..` | 해당 패키지 + 모든 하위 패키지 |

### 파라미터 매칭

| 표현 | 의미 |
|------|------|
| `(String)` | 정확히 String 타입 1개 |
| `()` | 파라미터 없음 |
| `(*)` | 타입 무관 1개 |
| `(*, *)` | 타입 무관 2개 |
| `(..)` | 개수·타입 무관 (0개 이상) |
| `(String, ..)` | String으로 시작, 이후 무관 |

### 부모 타입 매칭 (중요!)

```java
// 인터페이스 지정 → 구현체 메서드도 매칭됨
execution(* hello.aop.member.MemberService.*(..))
// MemberServiceImpl.hello() → 매칭 O

// 단, 부모에 없는 메서드는 미매칭
// MemberService에 internal()이 없으면:
// MemberServiceImpl.internal() → 매칭 X
```

---

## within

```java
within(hello.aop.member.MemberServiceImpl)  // 정확한 타입
within(hello.aop.member.*Service*)           // 와일드카드
within(hello.aop..*)                         // 하위 패키지 포함
```

**execution과 차이**: within은 **부모 타입 지정 불가**

```java
within(hello.aop.member.MemberService)        // 인터페이스 → 미매칭!
execution(* hello.aop.member.MemberService.*) // 이건 매칭됨
```

---

## args

**런타임** 시점에 전달된 인수의 타입으로 판단 → 부모 타입 허용

```java
// String hello(String) 메서드에 대해:
args(String)              // 매칭
args(Object)              // 매칭 (String이 Object 자식)
args(java.io.Serializable) // 매칭 (String이 구현)
args()                    // 미매칭

execution(* *(String))               // 매칭
execution(* *(java.io.Serializable)) // 미매칭! (정적, 시그니처 기준)
execution(* *(Object))               // 미매칭! (정적)
```

> args는 단독 사용 금지 — 반드시 execution 등과 함께 사용

---

## @target vs @within

```java
@ClassAop
class Child extends Parent {
    public void childMethod(){}
}
class Parent {
    public void parentMethod(){}
}
```

| | `@target` | `@within` |
|---|---|---|
| `childMethod()` | 적용 | 적용 |
| `parentMethod()` (상속) | **적용** | **미적용** |

```java
// @target: 인스턴스의 모든 메서드 (부모 클래스 메서드 포함)
@Around("execution(* hello.aop..*(..)) && @target(hello.aop.member.annotation.ClassAop)")

// @within: 해당 타입에 직접 선언된 메서드만
@Around("execution(* hello.aop..*(..)) && @within(hello.aop.member.annotation.ClassAop)")
```

> `@target`, `@within`, `args`, `@args` → 단독 사용 금지. execution과 함께 써야 함.

---

## @annotation

가장 실용적인 지시자 중 하나 — 메서드에 특정 애노테이션이 있을 때 적용.

```java
// 적용 대상
public class MemberServiceImpl {
    @MethodAop("test value")
    public String hello(String param) { ... }
}

// Aspect
@Around("@annotation(hello.aop.member.annotation.MethodAop)")
public Object doAtAnnotation(ProceedingJoinPoint joinPoint) throws Throwable { ... }
```

---

## bean

스프링 빈 이름으로 AOP 적용 — 스프링 전용.

```java
@Around("bean(orderService) || bean(*Repository)")
public Object doLog(ProceedingJoinPoint joinPoint) throws Throwable {
    log.info("[bean] {}", joinPoint.getSignature());
    return joinPoint.proceed();
}
```

---

## 파라미터 전달

포인트컷 지시자에서 어드바이스 메서드로 정보를 직접 전달할 수 있다.

```java
// args: 메서드 인수 전달
@Around("allMember() && args(arg,..)")
public Object logArgs2(ProceedingJoinPoint joinPoint, Object arg) throws Throwable { ... }

// @Before + 타입 제한
@Before("allMember() && args(arg,..)")
public void logArgs3(String arg) { ... } // String 타입만 매칭

// this: 프록시 객체 전달
@Before("allMember() && this(obj)")
public void thisArgs(JoinPoint joinPoint, MemberService obj) { ... }

// target: 실제 대상 객체 전달
@Before("allMember() && target(obj)")
public void targetArgs(JoinPoint joinPoint, MemberService obj) { ... }

// @annotation: 애노테이션 객체 전달 → annotation.value() 접근 가능
@Before("allMember() && @annotation(annotation)")
public void atAnnotation(JoinPoint joinPoint, MethodAop annotation) {
    log.info("annotationValue={}", annotation.value());
}
```

---

## this vs target (프록시 종류에 따른 차이)

| | JDK 동적 프록시 | CGLIB |
|---|---|---|
| `this(MemberService)` | 매칭 O | 매칭 O |
| `target(MemberService)` | 매칭 O | 매칭 O |
| `this(MemberServiceImpl)` | **매칭 X** | 매칭 O |
| `target(MemberServiceImpl)` | 매칭 O | 매칭 O |

**이유**:
- JDK 동적 프록시 → 인터페이스 구현체 → `MemberServiceImpl`을 상속하지 않음
- CGLIB → 구체 클래스 상속 → `MemberServiceImpl`의 자식 타입

```java
// 설정 변경 (테스트용)
@SpringBootTest(properties = "spring.aop.proxy-target-class=false") // JDK
@SpringBootTest(properties = "spring.aop.proxy-target-class=true")  // CGLIB (기본값)
```

> 스프링 부트 기본값: `proxyTargetClass=true` → CGLIB → `this(구체클래스)` 항상 매칭됨

---

## stylink 적용 포인트

### 실무에서 가장 자주 쓰는 패턴

```java
// 패키지 기반 + 메서드 제외
@Around("execution(* com.stylink.domain..*(..)) && !execution(* com.stylink.domain..*(..)*Dto*(..))")

// 특정 애노테이션 기반 (가장 명시적)
@Around("@annotation(com.stylink.common.annotation.Loggable)")

// 빈 이름 기반 (간단할 때)
@Around("bean(*Service) || bean(*Repository)")
```

### @annotation 패턴 추천 (실용적)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Loggable {}

@Loggable
public InventoryResponse hold(Long inventoryId) { ... }

@Around("@annotation(com.stylink.common.annotation.Loggable)")
public Object log(ProceedingJoinPoint joinPoint) throws Throwable { ... }
```

---

## 면접 Q&A

**Q: execution 포인트컷에서 `.`과 `..`의 차이는?**  
A: `.`은 정확히 해당 위치의 패키지만을 의미한다. `hello.aop.member.*`는 `hello.aop.member` 패키지 안의 타입만 매칭한다. `..`은 해당 위치의 패키지와 모든 하위 패키지를 포함한다. `hello.aop.member..*`는 `hello.aop.member` 패키지 및 그 아래의 모든 하위 패키지까지 매칭한다.

**Q: execution의 부모 타입 매칭과 within의 차이는?**  
A: `execution`은 부모 타입(인터페이스)을 지정하면 그를 구현한 자식 타입의 메서드도 매칭한다. 단, 부모 타입에 선언된 메서드만 매칭 가능하다. `within`은 정확히 해당 타입만 매칭하며 부모 타입을 지정하면 미매칭이다.

**Q: args와 execution의 파라미터 매칭 차이는?**  
A: `execution`은 클래스 정보 기반으로 정적으로 판단한다. `execution(* *(Object))`는 메서드 시그니처에 정확히 `Object`가 있어야 매칭한다. `args`는 런타임에 전달된 인스턴스 타입으로 동적 판단한다. `args(Object)`는 런타임에 `Object`의 자식 타입인 `String`을 전달해도 매칭된다.

**Q: `@target`과 `@within`의 차이는?**  
A: `@target`은 인스턴스의 모든 메서드에 적용된다. 즉 부모 클래스에서 상속된 메서드에도 AOP가 적용된다. `@within`은 해당 타입 안에 직접 선언된 메서드에만 적용된다. `@ClassAop`가 붙은 자식 클래스가 부모 메서드를 상속받은 경우, `@target`은 부모 메서드에도 적용되지만 `@within`은 적용되지 않는다.
