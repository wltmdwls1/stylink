# 스프링 핵심 원리 이해1 - 예제 만들기

> 스프링 기본편 | BASIC

---

## 전체 흐름 한눈에 보기

```
비즈니스 요구사항 분석
  → 회원: BASIC/VIP 등급, 자체 DB (변경 가능)
  → 주문/할인: VIP에게 1000원 고정 할인 (변경 가능)

회원 도메인 설계
  → MemberRepository (인터페이스) ← MemoryMemberRepository (구현)
  → MemberService (인터페이스) ← MemberServiceImpl (구현)

주문 도메인 설계
  → DiscountPolicy (인터페이스) ← FixDiscountPolicy (구현)
  → OrderService (인터페이스) ← OrderServiceImpl (구현)

문제점 발견
  → MemberServiceImpl이 MemoryMemberRepository 구체 클래스에 직접 의존 → DIP 위반
  → OrderServiceImpl이 FixDiscountPolicy 구체 클래스에 직접 의존 → DIP 위반
  → 할인 정책 변경 시 OrderServiceImpl 코드 수정 필요 → OCP 위반
  → 다음 강에서 AppConfig로 해결
```

---

## 프로젝트 생성

- start.spring.io → Spring Boot 3.x, Java 17, Gradle
- 의존성: Spring Web (예제에선 사용 안 함, 나중에 추가)
- groupId: `hello`, artifactId: `core`

---

## 회원 도메인

```
MemberService (인터페이스)
  └─ MemberServiceImpl (구현체)
       └─ MemberRepository (인터페이스)
            └─ MemoryMemberRepository (구현체, HashMap 기반)
```

핵심 설계: 인터페이스와 구현체를 분리하여 **나중에 DB 구현체로 교체** 가능하게 설계.

문제: `MemberServiceImpl` 내부에서 `new MemoryMemberRepository()`로 구체 클래스에 직접 의존 → DIP 위반.

---

## 주문과 할인 도메인

```
주문 흐름
  클라이언트
    → OrderService.createOrder(memberId, itemName, price)
      → MemberRepository.findById() → 회원 등급 확인
      → DiscountPolicy.discount() → 할인 금액 계산
      → Order 반환
```

```java
// VIP에게 1000원 고정 할인
public class FixDiscountPolicy implements DiscountPolicy {
    private int discountFixAmount = 1000;

    @Override
    public int discount(Member member, int price) {
        if (member.getGrade() == Grade.VIP) {
            return discountFixAmount;
        }
        return 0;
    }
}
```

설계 의도: `OrderServiceImpl`은 할인 로직을 직접 갖지 않고 `DiscountPolicy`에 위임 → **단일 책임 원칙(SRP)** 실현.

문제: `new FixDiscountPolicy()`로 구체 클래스에 직접 의존 → 할인 정책 변경 시 `OrderServiceImpl` 코드 수정 필요.

---

## 이 예제에서 배운 것

| 원칙 | 위반 여부 | 이유 |
|------|-----------|------|
| SRP | 준수 | OrderService는 주문만, 할인은 DiscountPolicy에 위임 |
| DIP | **위반** | 구현체(`MemoryMemberRepository`, `FixDiscountPolicy`)에 직접 의존 |
| OCP | **위반** | 할인 정책 변경 시 `OrderServiceImpl` 코드 수정 필요 |

---

## 면접 Q&A

**Q: 인터페이스를 두고 구현체를 분리하는 이유는?**
A: 구현 기술이 변경될 가능성이 있는 영역(DB, 할인 정책 등)을 인터페이스로 추상화하면, 실제 구현체가 바뀌어도 사용하는 쪽(클라이언트)의 코드를 수정하지 않아도 된다. 이것이 OCP(개방-폐쇄 원칙)의 목표다.

**Q: DIP란 무엇이고, 이 예제에서 어떻게 위반되었나?**
A: DIP(의존관계 역전 원칙)는 구체 클래스가 아닌 추상화(인터페이스)에 의존해야 한다는 원칙이다. 이 예제에서 `OrderServiceImpl`이 `DiscountPolicy` 인터페이스와 `FixDiscountPolicy` 구현체 모두에 의존하고 있어 DIP를 위반했다. 결론적으로 인터페이스만 의존해야 하는데, 누군가가 구현 객체를 대신 생성해서 주입해줘야 한다.
