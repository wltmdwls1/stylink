# 스프링 핵심 원리 이해2 - 객체 지향 원리 적용

> 스프링 기본편 | DEEP

---

## 전체 흐름 한눈에 보기

```
문제: 할인 정책 변경 요청 (고정 1000원 → 정률 10%)
  → OrderServiceImpl 코드를 직접 변경해야 함 → OCP 위반!
  → 이유: 구체 클래스에도 의존하고 있어서 → DIP 위반!

해결: AppConfig (관심사 분리)
  → 생성자 주입으로 구체 클래스를 외부에서 주입
  → OrderServiceImpl은 인터페이스(추상화)만 의존
  → 할인 정책 변경 = AppConfig의 한 줄만 수정

개념 정리
  → IoC: 제어의 역전 (프로그램 제어 흐름이 역전됨)
  → DI: 의존관계 주입 (외부에서 구현체를 주입)
  → IoC 컨테이너 = DI 컨테이너 → 스프링이 이 역할!

스프링 전환
  → AppConfig에 @Configuration, @Bean 추가
  → ApplicationContext로 빈 꺼내서 사용
```

---

## DIP/OCP 위반 재확인

```java
// 변경 전
private final DiscountPolicy discountPolicy = new FixDiscountPolicy();
// 변경 후
private final DiscountPolicy discountPolicy = new RateDiscountPolicy();
```

할인 정책을 바꾸려면 `OrderServiceImpl`을 직접 수정해야 함 → **OCP 위반**.
`OrderServiceImpl`이 `DiscountPolicy` 인터페이스 + `FixDiscountPolicy` 구현체 모두에 의존 → **DIP 위반**.

---

## AppConfig로 관심사 분리

"배우는 배역에만 집중하고, 공연 기획자(AppConfig)가 배우를 섭외한다."

```java
@Configuration
public class AppConfig {
    @Bean
    public MemberService memberService() {
        return new MemberServiceImpl(memberRepository());
    }

    @Bean
    public OrderService orderService() {
        return new OrderServiceImpl(memberRepository(), discountPolicy());
    }

    @Bean
    public MemberRepository memberRepository() {
        return new MemoryMemberRepository();
    }

    @Bean
    public DiscountPolicy discountPolicy() {
        return new RateDiscountPolicy(); // 여기만 수정하면 됨!
    }
}
```

`OrderServiceImpl`은 **생성자 주입**만 받음:
```java
public OrderServiceImpl(MemberRepository memberRepository, DiscountPolicy discountPolicy) {
    this.memberRepository = memberRepository;
    this.discountPolicy = discountPolicy;
    // 구체 클래스를 모름 → 순수하게 인터페이스만 의존!
}
```

---

## IoC / DI / 컨테이너

| 개념 | 설명 |
|------|------|
| **IoC** (제어의 역전) | 기존엔 구현체가 스스로 의존 객체를 생성·제어했지만, AppConfig가 대신 제어. 제어 흐름이 뒤집힘 |
| **DI** (의존관계 주입) | 런타임에 외부에서 실제 구현 객체를 생성하여 클라이언트에 전달·연결. 동적 의존관계 |
| **DI 컨테이너** | AppConfig처럼 객체를 생성하고 의존관계를 연결해주는 것. 스프링 컨테이너가 이 역할 |

정적 vs 동적 의존관계:
- **정적**: 코드(import문)만 봐도 알 수 있는 의존관계
- **동적**: 런타임에 실제로 어떤 구현체가 주입될지 결정 → DI가 동적 의존관계를 다룸

---

## 할인 정책 변경 결과

```java
// AppConfig의 discountPolicy()만 변경
public DiscountPolicy discountPolicy() {
    // return new FixDiscountPolicy();
    return new RateDiscountPolicy(); // 끝!
}
```

`OrderServiceImpl`, `MemberServiceImpl` 등 **클라이언트 코드는 전혀 수정 없음** → OCP 충족.

---

## 스프링 전환

```java
// 이전: AppConfig 직접 생성
AppConfig appConfig = new AppConfig();
MemberService memberService = appConfig.memberService();

// 이후: 스프링 컨테이너 사용
ApplicationContext ac = new AnnotationConfigApplicationContext(AppConfig.class);
MemberService memberService = ac.getBean("memberService", MemberService.class);
```

스프링 컨테이너의 이점: 싱글톤, AOP, 트랜잭션, 환경변수, 이벤트 등 방대한 기능.

---

## 면접 Q&A

**Q: OCP와 DIP가 동시에 위반된 이유는?**
A: `OrderServiceImpl`이 `DiscountPolicy` 인터페이스뿐 아니라 `FixDiscountPolicy` 구현 클래스에도 직접 의존했기 때문이다(DIP 위반). 그 결과 할인 정책을 변경하면 `OrderServiceImpl` 코드를 직접 수정해야 했다(OCP 위반). 두 원칙은 함께 움직이는 경우가 많다.

**Q: AppConfig가 하는 일은 무엇이고, 왜 필요한가?**
A: AppConfig는 구현 객체를 생성하고, 생성자를 통해 의존관계를 연결(주입)해주는 역할을 한다. 이를 통해 비즈니스 로직을 담은 클래스들은 인터페이스만 바라보면 되고, 구체 구현은 AppConfig가 한 곳에서 결정한다. 이것이 관심사의 분리다.

**Q: IoC와 DI의 차이는?**
A: IoC는 더 넓은 개념으로, 프로그램의 제어 흐름을 개발자가 아닌 외부(프레임워크·컨테이너)가 담당하는 것이다. DI는 IoC를 구현하는 방법 중 하나로, 런타임에 외부에서 의존 객체를 주입하는 구체적인 기술이다.

**Q: 생성자 주입의 장점은?**
A: 객체 생성 시점에 의존관계가 확정되므로, 필수 의존관계를 누락할 수 없다. 또한 final 키워드를 사용할 수 있어 불변성이 보장된다. 테스트 시에도 생성자에 목(mock) 객체를 바로 넣을 수 있어 테스트하기 쉽다.
