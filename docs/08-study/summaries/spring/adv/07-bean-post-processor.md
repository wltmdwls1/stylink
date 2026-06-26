# 빈 후처리기

> Spring 고급편 | ADV

---

## 전체 흐름 한눈에 보기

```
챕터 6 남은 문제:
  설정 파일에 프록시 생성 코드 N개 → 빈이 100개면 코드 100개
  @Component 컴포넌트 스캔 대상은 이미 실제 객체가 빈으로 등록됨 → 프록시 교체 불가
  ↓
BeanPostProcessor (빈 후처리기):
  빈 등록 직전에 객체를 조작하거나 다른 객체로 바꿔치기 가능
  → 실제 객체 대신 프록시를 반환하면 프록시가 빈으로 등록됨
  ↓
PackageLogTraceProxyPostProcessor (직접 구현):
  패키지 조건 체크 후 ProxyFactory로 프록시 생성해 반환
  → 설정 파일 1개, 컴포넌트 스캔 대상도 커버
  ↓
AnnotationAwareAspectJAutoProxyCreator (스프링 제공):
  Advisor만 @Bean으로 등록하면 자동으로 프록시 생성
  → 빈 후처리기 직접 작성 불필요
  ↓
AspectJExpressionPointcut:
  패키지 + 메서드 이름 동시 필터 (실무 표준)
  execution(* hello.proxy.app..*(..)) && !execution(* hello.proxy.app..noLog(..))
  ↓
다음: @Aspect (챕터 8)
```

---

## BeanPostProcessor 소개

```java
public interface BeanPostProcessor {
    // 객체 생성 후, @PostConstruct 초기화 발생 전
    Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException;
    // 객체 생성 후, @PostConstruct 초기화 발생 후
    Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException;
}
```

**동작 순서**:
```
생성(@Bean / @Component) → 빈 후처리기에 전달 → 조작/교체 → 반환 → 빈 저장소 등록
```

반환 객체가 그대로 빈으로 등록되기 때문에, **다른 객체를 반환하면 그 객체가 빈이 된다**.

> @PostConstruct가 동작하는 원리도 `CommonAnnotationBeanPostProcessor`라는 빈 후처리기가 초기화 메서드를 호출하기 때문이다.

---

## A를 B로 바꿔치기 예제

```java
static class AToBPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof A) {
            return new B(); // A 대신 B 반환 → beanA 이름으로 B 등록
        }
        return bean;
    }
}
```

```java
// 결과: beanA 이름으로 B 객체가 등록됨
B b = applicationContext.getBean("beanA", B.class); // 성공
applicationContext.getBean(A.class);                // NoSuchBeanDefinitionException
```

---

## 직접 구현: PackageLogTraceProxyPostProcessor

특정 패키지에 속한 빈만 프록시로 교체하는 빈 후처리기.

```java
public class PackageLogTraceProxyPostProcessor implements BeanPostProcessor {
    private final String basePackage;
    private final Advisor advisor;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        String packageName = bean.getClass().getPackageName();
        if (!packageName.startsWith(basePackage)) {
            return bean; // 패키지 불일치 → 원본 반환
        }
        // 패키지 일치 → 프록시로 교체
        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.addAdvisor(advisor);
        return proxyFactory.getProxy();
    }
}
```

```java
// 설정 파일: 빈 후처리기 1개 등록만으로 끝
@Bean
public PackageLogTraceProxyPostProcessor logTraceProxyPostProcessor(LogTrace logTrace) {
    return new PackageLogTraceProxyPostProcessor("hello.proxy.app", getAdvisor(logTrace));
}
// ProxyFactory 코드가 설정 파일에 없음 → 빈이 아무리 추가되어도 설정 불변
```

**결과**:
- v1 (인터페이스 있음): JDK 동적 프록시 적용
- v2 (구체 클래스): CGLIB 적용
- **v3 (@Component 컴포넌트 스캔): CGLIB 적용 — 컴포넌트 스캔 대상도 커버됨**

---

## 스프링이 제공하는 빈 후처리기

### 의존성 추가

```groovy
// 스프링 부트 3.x
implementation 'org.springframework.boot:spring-boot-starter-aop'
// 스프링 부트 4.x
implementation 'org.springframework.boot:spring-boot-starter-aspectj'
```

→ `aspectjweaver` 라이브러리 포함  
→ 스프링 부트가 AOP 관련 클래스 자동 등록 (`@EnableAspectJAutoProxy` 불필요)

### AnnotationAwareAspectJAutoProxyCreator

스프링 부트 자동 구성이 등록하는 빈 후처리기.  
**스프링 컨테이너에 등록된 모든 `Advisor`를 자동으로 찾아 필요하면 프록시를 생성**한다.

```java
// 빈 후처리기 직접 등록 불필요 — Advisor만 @Bean으로 등록하면 됨
@Bean
public Advisor advisor1(LogTrace logTrace) {
    NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
    pointcut.setMappedNames("request*", "order*", "save*");
    return new DefaultPointcutAdvisor(pointcut, new LogTraceAdvice(logTrace));
}
```

### 자동 프록시 생성기 동작 과정

```
1. 생성: 스프링이 빈 대상 객체 생성
2. 전달: 빈 후처리기에 객체 전달
3. Advisor 전체 조회: 스프링 컨테이너에서 모든 Advisor 빈 조회
4. 프록시 적용 대상 체크: Pointcut으로 각 클래스/메서드를 매칭
   → 메서드 중 하나라도 Pointcut 조건 충족 → 프록시 생성 대상
5. 프록시 생성: 대상이면 프록시를 생성해 반환
6. 빈 등록: 반환된 (프록시) 객체가 빈 저장소에 등록됨
```

### Pointcut의 2가지 역할

| 역할 | 시점 | 판단 기준 |
|------|------|-----------|
| **프록시 적용 여부** | 빈 등록 시 (생성 단계) | 해당 클래스의 메서드 중 하나라도 매칭 |
| **어드바이스 적용 여부** | 프록시 호출 시 (사용 단계) | 호출된 메서드가 Pointcut 조건 충족 여부 |

```
orderControllerV1.request()  → Pointcut 일치 → Advice 실행 후 target 호출
orderControllerV1.noLog()    → Pointcut 불일치 → Advice 없이 바로 target 호출
```

---

## AspectJExpressionPointcut — 실무 표준

`NameMatchMethodPointcut`은 메서드 이름만 체크 → 스프링 내부 빈에도 불필요하게 적용될 수 있다.  
`AspectJExpressionPointcut`은 **패키지 + 클래스 + 메서드**를 한 번에 필터링할 수 있다.

```java
// advisor2: 패키지 조건만 (noLog도 매칭됨)
pointcut.setExpression("execution(* hello.proxy.app..*(..))");
// * : 모든 반환 타입
// hello.proxy.app.. : 해당 패키지 + 하위 패키지
// *(..) : 모든 메서드 이름, 모든 파라미터

// advisor3: noLog() 제외 (실무 표준 패턴)
pointcut.setExpression(
    "execution(* hello.proxy.app..*(..)) && !execution(* hello.proxy.app..noLog(..))"
);
// && : 두 조건 모두 충족
// !  : 반대
```

---

## 하나의 프록시, 여러 Advisor

`advisor1`과 `advisor2`가 모두 같은 빈에 매칭될 때, 프록시 몇 개가 생성되나?

**프록시 1개** — 자동 프록시 생성기는 target당 프록시를 1개만 만들고, 해당 Advisor를 모두 담는다.

| 상황 | 결과 |
|------|------|
| advisor1만 매칭 | 프록시 1개 (advisor1 포함) |
| advisor1, advisor2 둘 다 매칭 | 프록시 1개 (advisor1, advisor2 모두 포함) |
| 둘 다 미매칭 | 프록시 생성 안 함, 원본 객체 등록 |

---

## 정리

| 문제 | 해결 |
|------|------|
| 설정 파일 폭발 | 빈 후처리기로 프록시 생성 집중 → 설정 1개 |
| 컴포넌트 스캔 미적용 | 빈 후처리기는 컴포넌트 스캔 빈도 처리 |
| 빈 후처리기 직접 작성 | `AnnotationAwareAspectJAutoProxyCreator` 자동 제공 |
| 패키지 조건 → 정교한 필터 | `AspectJExpressionPointcut` |

**개발자가 할 일**: `Advisor`만 스프링 빈으로 등록  
**스프링이 해주는 것**: 모든 빈에 Pointcut 매칭 → 필요하면 프록시 자동 생성

---

## stylink 적용 포인트

`@Transactional`, `@Cacheable` 등이 컴포넌트 스캔(`@Service`, `@Repository`)과 함께 동작하는 이유가 바로 이 챕터 원리다.

```
@Service
public class InventoryService {
    @Transactional // AOP Advisor 적용 대상
    public void hold(Long inventoryId) { ... }
}
```

`AnnotationAwareAspectJAutoProxyCreator`가 `InventoryService` 빈 등록 시점에  
`@Transactional` Advisor의 Pointcut을 체크 → 프록시 자동 생성 → 트랜잭션 처리.

---

## 면접 Q&A

**Q: 스프링 빈 후처리기란 무엇이고 어떤 역할을 하나?**  
A: `BeanPostProcessor`는 스프링이 빈을 생성하고 저장소에 등록하기 직전에 개발자가 개입할 수 있는 확장 포인트다. 반환 객체를 교체할 수 있어서 실제 객체 대신 프록시를 반환하면 프록시가 빈으로 등록된다. `@PostConstruct`도 내부적으로 `CommonAnnotationBeanPostProcessor`라는 빈 후처리기가 동작하는 원리다.

**Q: AnnotationAwareAspectJAutoProxyCreator가 하는 일은?**  
A: 스프링 부트 자동 구성이 등록하는 빈 후처리기다. 스프링 컨테이너에 등록된 모든 `Advisor`를 조회하고, 각 빈에 대해 Pointcut 매칭을 검사해 조건을 충족하면 자동으로 프록시를 생성한다. 개발자는 `Advisor`만 `@Bean`으로 등록하면 된다.

**Q: @Component로 등록한 클래스에 @Transactional이 동작하는 원리는?**  
A: `AnnotationAwareAspectJAutoProxyCreator`가 컴포넌트 스캔으로 등록되는 빈도 빈 후처리기를 거치게 되므로, `@Transactional`이 붙은 메서드를 가진 빈은 프록시로 교체된다. 컴포넌트 스캔 여부와 무관하게 빈 등록 시점에 후처리기가 동작하기 때문이다.

**Q: Pointcut이 프록시 생성 시점과 호출 시점에 각각 어떻게 쓰이나?**  
A: 두 가지 역할을 한다. 첫째, 빈 등록 시점에 해당 빈을 프록시로 생성할지 판단하는 데 쓰인다 — 메서드 중 하나라도 Pointcut에 매칭되면 프록시를 만든다. 둘째, 프록시가 실제 호출될 때 해당 메서드에 Advice를 적용할지 판단한다 — 매칭되면 Advice 실행 후 target 호출, 미매칭이면 Advice 없이 바로 target을 호출한다.
