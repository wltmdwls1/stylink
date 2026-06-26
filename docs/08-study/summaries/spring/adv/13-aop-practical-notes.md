# 스프링 AOP - 실무 주의사항

> Spring 고급편 | ADV

---

## 1. 프록시 내부 호출 문제

### 문제 상황

```java
@Component
public class CallServiceV0 {
    public void external() {
        log.info("call external");
        internal(); // this.internal() = 실제 객체 직접 호출
    }
    public void internal() {
        log.info("call internal");
    }
}
```

```
// callServiceV0.external() 호출 시 실행 순서:
CallLogAspect : aop=...external()   ← 프록시 거침 (AOP 적용)
CallServiceV0 : call external
CallServiceV0 : call internal       ← AOP 미적용! (직접 호출)
```

**원인**: `external()` 내부에서 `internal()` 호출 시 `this`(실제 객체)를 통해 직접 호출 → 프록시 우회.

---

### 해결책 1 — 자기 자신 주입 (비권장)

```java
@Component
public class CallServiceV1 {
    private CallServiceV1 callServiceV1; // 프록시가 주입됨

    @Autowired
    public void setCallServiceV1(CallServiceV1 callServiceV1) {
        this.callServiceV1 = callServiceV1;
    }
    public void external() {
        callServiceV1.internal(); // 프록시를 통한 외부 호출 → AOP 적용됨
    }
}
```

**문제**: Spring Boot 2.6+는 순환 참조 기본 금지.  
강제 허용: `spring.main.allow-circular-references=true` (권장 X)

---

### 해결책 2 — ObjectProvider 지연 조회 (중간 방법)

```java
@Component
@RequiredArgsConstructor
public class CallServiceV2 {
    private final ObjectProvider<CallServiceV2> callServiceProvider;

    public void external() {
        CallServiceV2 callServiceV2 = callServiceProvider.getObject(); // 호출 시점에 조회
        callServiceV2.internal(); // 프록시를 통해 → AOP 적용
    }
}
```

`getObject()` 시점에 컨테이너에서 조회 → 순환 참조 없음.  
`ApplicationContext`보다 `ObjectProvider`가 더 가벼운 선택.

---

### 해결책 3 — 구조 변경 (가장 권장)

```java
@Component
@RequiredArgsConstructor
public class CallServiceV3 {
    private final InternalService internalService; // 별도 빈으로 분리

    public void external() {
        internalService.internal(); // 외부 메서드 호출 → AOP 적용됨
    }
}

@Component
public class InternalService {
    public void internal() { ... }
}
```

**장점**: 코드 구조도 단일 책임 원칙에 맞게 정리됨.

---

### 해결책 비교

| 방법 | 권장도 | 문제 |
|------|--------|------|
| 자기 자신 주입 | 비권장 | 순환 참조, Spring 2.6+ 기본 금지 |
| ObjectProvider | 중간 | 어색한 코드 |
| **구조 변경** | **권장** | 없음 (코드도 더 깔끔해짐) |

> AOP는 `public` 메서드에만 적용. `private` 메서드는 AOP 불가.

---

## 2. 프록시 기술 한계 — 타입 캐스팅

### JDK 동적 프록시 → 구체 클래스 캐스팅 불가

```java
ProxyFactory proxyFactory = new ProxyFactory(target);
proxyFactory.setProxyTargetClass(false); // JDK 동적 프록시

MemberService proxy = (MemberService) proxyFactory.getProxy();
MemberServiceImpl impl = (MemberServiceImpl) proxy; // ClassCastException!
```

JDK Proxy는 `MemberService` 인터페이스 기반 → `MemberServiceImpl`을 모름.

### CGLIB → 구체 클래스 캐스팅 가능

```java
proxyFactory.setProxyTargetClass(true); // CGLIB

MemberService proxy = (MemberService) proxyFactory.getProxy();
MemberServiceImpl impl = (MemberServiceImpl) proxy; // 성공
```

CGLIB Proxy는 `MemberServiceImpl`을 상속 → 양쪽 모두 캐스팅 가능.

---

## 3. 프록시 기술 한계 — 의존관계 주입

```java
@SpringBootTest(properties = "spring.aop.proxy-target-class=false") // JDK 동적 프록시
public class ProxyDITest {
    @Autowired MemberService memberService;         // OK
    @Autowired MemberServiceImpl memberServiceImpl; // BeanNotOfRequiredTypeException!
}
```

```
BeanNotOfRequiredTypeException: expected MemberServiceImpl but was $Proxy54
```

| | JDK 동적 프록시 | CGLIB |
|---|---|---|
| `@Autowired MemberService` | 성공 | 성공 |
| `@Autowired MemberServiceImpl` | **실패** | **성공** |

---

## 4. CGLIB의 기존 한계 (Spring 4.0 이후 해결됨)

| 문제 | 해결 시점 | 해결 방법 |
|------|-----------|-----------|
| 기본 생성자 필수 | Spring 4.0 | objenesis 라이브러리 |
| 생성자 2번 호출 | Spring 4.0 | objenesis 라이브러리 |
| final 클래스/메서드 사용 불가 | 미해결 | 실무에서 잘 쓰지 않으므로 무방 |

---

## 5. 스프링의 역사적 해결책 (요약)

```
Spring 3.2 : CGLIB를 스프링에 내장 (별도 라이브러리 불필요)
Spring 4.0 : objenesis → 기본 생성자 필수/생성자 2회 호출 문제 해결
Spring Boot 2.0 : proxyTargetClass=true 기본값 → CGLIB 기본 사용
```

**결론**: Spring Boot 2.0+를 사용하면 CGLIB 관련 문제가 대부분 해결되어 있다.

```
// Spring Boot 기본값 확인
memberService class=class ...MemberServiceImpl$$EnhancerBySpringCGLIB$$...
memberServiceImpl class=class ...MemberServiceImpl$$EnhancerBySpringCGLIB$$...
```

JDK 동적 프록시로 되돌리고 싶다면:
```properties
spring.aop.proxy-target-class=false
```

---

## stylink 적용 포인트

### 트랜잭션 AOP 내부 호출 주의

```java
@Service
public class OrderService {
    // 내부 호출 시 @Transactional 미적용!
    public void createOrder(...) {
        validateOrder(...); // private 또는 같은 클래스 내부 호출
        processPayment(...); // this.processPayment() → 트랜잭션 미적용!
    }

    @Transactional
    public void processPayment(...) { ... }
}
```

**올바른 구조**:
```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final PaymentService paymentService; // 별도 빈으로 분리

    public void createOrder(...) {
        paymentService.processPayment(...); // 외부 호출 → @Transactional 적용됨
    }
}

@Service
public class PaymentService {
    @Transactional
    public void processPayment(...) { ... }
}
```

---

## 면접 Q&A

**Q: 스프링 AOP 내부 호출 문제란?**  
A: 같은 빈 안에서 메서드가 자신의 다른 메서드를 호출할 때 `this`를 통해 직접 호출하게 된다. 이 경우 프록시를 거치지 않아 AOP가 적용되지 않는다. 가장 좋은 해결책은 내부 호출이 발생하지 않도록 해당 메서드를 별도 빈으로 분리하는 것이다. `@Transactional`이 가장 대표적인 케이스로, 같은 서비스 안에서 `@Transactional` 메서드를 내부 호출하면 트랜잭션이 적용되지 않는다.

**Q: JDK 동적 프록시 vs CGLIB 차이와 스프링 부트 기본값은?**  
A: JDK 동적 프록시는 인터페이스 기반 프록시를 생성하고, CGLIB는 구체 클래스를 상속해 프록시를 생성한다. JDK 동적 프록시는 구체 클래스 타입으로 캐스팅하거나 DI받을 수 없는 한계가 있다. 스프링 부트 2.0부터 `proxyTargetClass=true`가 기본값이 되어 CGLIB를 기본으로 사용한다. CGLIB는 구체 클래스 기반이므로 구체 클래스 타입으로도 캐스팅/DI가 가능하다. 기존에 CGLIB의 단점이었던 기본 생성자 필수와 생성자 2번 호출 문제는 스프링 4.0에서 objenesis 라이브러리로 해결됐다.

**Q: 실무에서 AOP 내부 호출 문제를 어떻게 피하나?**  
A: 두 가지 원칙을 지키면 대부분 피할 수 있다. 첫째, AOP가 필요한 기능(트랜잭션, 로깅 등)은 각각 별도의 스프링 빈으로 분리한다. 둘째, AOP는 `public` 메서드에만 적용하고, 내부 상세 구현은 `private`으로 두어 AOP 적용 대상이 되지 않도록 한다. `@Transactional`을 사용할 때 같은 서비스 내의 메서드를 직접 호출하면 트랜잭션이 적용되지 않는 버그가 생기므로, 트랜잭션이 필요한 부분은 별도 서비스로 분리하거나 클라이언트에서 직접 호출하도록 설계한다.
