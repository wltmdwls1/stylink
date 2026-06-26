# 싱글톤 컨테이너

> 스프링 기본편 | DEEP

---

## 전체 흐름 한눈에 보기

```
문제: 웹 애플리케이션은 수천 건의 요청이 동시에 옴
  → AppConfig.memberService()가 호출될 때마다 new 객체 생성
  → 초당 100 요청 = 초당 100개 객체 생성·소멸 → 메모리 낭비!

해결1: 싱글톤 패턴
  → private 생성자 + static 인스턴스
  → 문제: DIP 위반, 테스트 어려움, private 생성자로 유연성↓

해결2: 스프링 싱글톤 컨테이너 (싱글톤 레지스트리)
  → 싱글톤 패턴 없이도 빈을 싱글톤으로 관리
  → DIP, OCP, 테스트, 유연성 모두 OK

주의: 싱글톤 빈은 반드시 무상태(stateless)로 설계!
  → 상태 필드가 있으면 다른 스레드의 요청 결과가 섞임 (치명적 버그)

@Configuration + CGLIB
  → @Bean 메서드가 여러 번 호출되어도 싱글톤 보장
  → 바이트코드 조작으로 이미 등록된 빈이면 그것을 반환
```

---

## 싱글톤 패턴의 문제점

```java
// 고전적 싱글톤 패턴
public class SingletonService {
    private static final SingletonService instance = new SingletonService();
    public static SingletonService getInstance() { return instance; }
    private SingletonService() {}  // 외부 new 차단
}
```

| 문제 | 설명 |
|------|------|
| DIP 위반 | 클라이언트가 구체 클래스(`getInstance()`)에 직접 의존 |
| OCP 위반 가능성 | 변경·확장 시 클라이언트 코드 수정 가능성 |
| 테스트 어려움 | 전역 상태라 테스트 간 독립성 보장 어려움 |
| 유연성 저하 | private 생성자 → 자식 클래스 생성 불가 |
| 구현 코드 증가 | 패턴 자체에 코드가 추가됨 |

---

## 스프링 싱글톤 컨테이너 (싱글톤 레지스트리)

스프링은 위 단점을 모두 해결하면서 싱글톤으로 빈을 관리한다.

```java
// getBean()을 여러 번 호출해도 같은 인스턴스!
MemberService s1 = ac.getBean("memberService", MemberService.class);
MemberService s2 = ac.getBean("memberService", MemberService.class);
assertThat(s1).isSameAs(s2); // true
```

- 싱글톤 패턴 코드 없이도 싱글톤 보장
- DIP, OCP, 테스트, private 생성자로부터 자유로움

---

## 싱글톤 방식의 핵심 주의사항: 무상태(stateless) 설계

**스프링 빈은 절대로 상태(필드)를 가지면 안 된다!**

```java
// 문제 있는 설계 (stateful)
public class StatefulService {
    private int price;  // ← 공유 필드!

    public void order(String name, int price) {
        this.price = price;  // 다른 스레드의 요청값이 덮어씌워짐!
    }
    public int getPrice() { return price; }
}
```

```
스레드A: order("userA", 10000) → this.price = 10000
스레드B: order("userB", 20000) → this.price = 20000  ← 덮어씀!
스레드A: getPrice() → 20000  ← userA가 원하는 값이 아님!
```

**올바른 설계 (stateless)**:
```java
public class StatelessService {
    public int order(String name, int price) {
        return price;  // 지역변수로 반환, 필드 없음
    }
}
```

필드 대신 **지역변수, 파라미터, ThreadLocal**을 사용하라.

---

## @Configuration과 CGLIB

AppConfig에서 `memberRepository()`가 여러 곳에서 호출되는데, 왜 싱글톤이 보장될까?

```java
@Configuration
public class AppConfig {
    @Bean
    public MemberService memberService() {
        return new MemberServiceImpl(memberRepository()); // 호출1
    }
    @Bean
    public OrderService orderService() {
        return new OrderServiceImpl(memberRepository(), discountPolicy()); // 호출2
    }
    @Bean
    public MemberRepository memberRepository() {
        return new MemoryMemberRepository(); // new가 두 번 불리는 것처럼 보임
    }
}
```

실제로는 **CGLIB 바이트코드 조작**으로 AppConfig가 프록시 객체로 대체됨:

```java
// CGLIB 내부 동작 (의사 코드)
public MemberRepository memberRepository() {
    if (스프링 컨테이너에 이미 등록되어 있으면) {
        return 기존 빈; // 싱글톤!
    }
    return new MemoryMemberRepository(); // 최초 1회만
}
```

```
// 클래스 확인
bean.getClass() = AppConfig$$EnhancerBySpringCGLIB$$...  ← 프록시!
```

| 조건 | 싱글톤 보장 |
|------|------------|
| `@Configuration` + `@Bean` | O (CGLIB 프록시 동작) |
| `@Bean`만 (Configuration 없음) | X (매 호출마다 new) |

→ **항상 @Configuration과 @Bean을 함께 사용하라.**

---

## 면접 Q&A

**Q: 스프링이 싱글톤 패턴의 단점 없이 싱글톤을 보장하는 방법은?**
A: 스프링 컨테이너(싱글톤 레지스트리)가 빈 객체를 직접 생성하고 관리하기 때문이다. 클라이언트 코드에 싱글톤 패턴 코드가 없어도 스프링이 내부적으로 동일 인스턴스를 유지한다. 덕분에 DIP, OCP를 지킬 수 있고 테스트도 자유롭다.

**Q: 싱글톤 빈에서 상태를 가지면 왜 위험한가?**
A: 싱글톤 빈은 여러 스레드가 동시에 사용하는 공유 객체다. 상태(필드)를 가지면 스레드A가 저장한 값을 스레드B가 덮어쓰거나 반대로 읽을 수 있어, 사용자A의 데이터가 사용자B에게 노출되는 치명적인 버그가 발생한다. 따라서 스프링 빈은 반드시 무상태(stateless)로 설계해야 한다.

**Q: @Configuration 없이 @Bean만 사용하면 어떻게 되나?**
A: 스프링 빈으로는 등록되지만 싱글톤이 보장되지 않는다. @Configuration이 있어야 스프링이 CGLIB으로 해당 클래스를 프록시로 만들어 @Bean 메서드가 여러 번 호출되어도 동일한 인스턴스를 반환하도록 한다. @Configuration 없이는 매 호출마다 new 객체가 생성된다.

**Q: CGLIB이란 무엇인가?**
A: 바이트코드를 조작하여 클래스를 동적으로 상속·확장하는 라이브러리다. 스프링은 @Configuration 클래스를 CGLIB으로 프록시 객체로 만들어 @Bean 메서드 호출을 가로채고, 이미 생성된 빈이 있으면 새로 생성하지 않고 기존 빈을 반환하도록 한다.
