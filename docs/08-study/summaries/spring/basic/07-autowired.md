# 의존관계 자동 주입

> 스프링 기본편 | DEEP

---

## 전체 흐름 한눈에 보기

```
DI 방법 4가지
  → 생성자 주입 (권장) / 수정자 주입 / 필드 주입 (비권장) / 일반 메서드 주입

왜 생성자 주입?
  → 불변: 객체 생성 시 1번만 호출, 이후 변경 불가
  → final 필드: 컴파일 시점에 누락 감지
  → 테스트 용이: 생성자에 직접 목 주입 가능

조회 빈 2개 이상 충돌 해결
  → @Autowired 필드명 매칭 (타입 → 이름 순)
  → @Qualifier (추가 구분자)
  → @Primary (우선순위 지정)
  → 우선순위: @Qualifier > @Primary

실무 최신 트렌드
  → @Component + @RequiredArgsConstructor (Lombok)
  → final 필드 + 자동 생성자 → 코드 최소화
```

---

## DI 방법 비교

| 방법 | 코드 | 언제 사용 |
|------|------|----------|
| 생성자 주입 | `@Autowired` on 생성자 | **항상 (기본)** |
| 수정자 주입 | `@Autowired` on setter | 선택적·변경 가능한 의존관계 |
| 필드 주입 | `@Autowired` on 필드 | **사용 금지** (테스트 불가) |
| 일반 메서드 주입 | `@Autowired` on 메서드 | 거의 사용 안 함 |

---

## 생성자 주입의 장점

```java
@Component
public class OrderServiceImpl implements OrderService {
    private final MemberRepository memberRepository;
    private final DiscountPolicy discountPolicy;

    @Autowired  // 생성자 1개면 생략 가능
    public OrderServiceImpl(MemberRepository memberRepository, DiscountPolicy discountPolicy) {
        this.memberRepository = memberRepository;
        this.discountPolicy = discountPolicy;
    }
}
```

1. **불변**: 생성 시 딱 1번 호출, 이후 setter 없어도 됨
2. **final 가능**: 초기화 누락 시 컴파일 오류로 즉시 발견
3. **테스트 용이**: 순수 자바 코드로 `new OrderServiceImpl(mock, mock)` 가능
4. **누락 방지**: setter 주입은 런타임 NPE, 생성자 주입은 컴파일 오류

> "컴파일 오류는 세상에서 가장 빠르고 좋은 오류다!"

---

## 롬복으로 최적화

```java
// 최종 권장 패턴
@Component
@RequiredArgsConstructor  // final 필드를 모아 생성자 자동 생성
public class OrderServiceImpl implements OrderService {
    private final MemberRepository memberRepository;
    private final DiscountPolicy discountPolicy;
    // 생성자 코드 직접 작성 불필요
}
```

`@RequiredArgsConstructor` = Lombok의 애노테이션 프로세서가 컴파일 시점에 생성자 코드를 자동 생성.

---

## 조회 빈 2개 이상 충돌 해결

`@Autowired`는 타입으로 조회 → 같은 타입이 2개 이상이면 `NoUniqueBeanDefinitionException`

**방법 1: @Autowired 필드명 매칭**
```java
// 타입 매칭 → 2개 이상이면 → 필드명/파라미터명으로 빈 이름 추가 매칭
@Autowired
private DiscountPolicy rateDiscountPolicy;  // "rateDiscountPolicy" 빈 주입
```

**방법 2: @Qualifier (추가 구분자)**
```java
@Component
@Qualifier("mainDiscountPolicy")
public class RateDiscountPolicy implements DiscountPolicy {}

@Autowired
public OrderServiceImpl(@Qualifier("mainDiscountPolicy") DiscountPolicy discountPolicy) { ... }
```
매칭 순서: `@Qualifier`끼리 → 빈 이름 → `NoSuchBeanDefinitionException`

**방법 3: @Primary (우선순위)**
```java
@Component
@Primary  // 같은 타입 빈 중 우선 선택됨
public class RateDiscountPolicy implements DiscountPolicy {}
```

**우선순위**: `@Qualifier` > `@Primary` (좁은 범위가 항상 이김)

**실무 활용**: 메인 DataSource는 `@Primary`, 서브 DataSource는 `@Qualifier` 지정

---

## 커스텀 애노테이션으로 타입 안전성 확보

```java
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier("mainDiscountPolicy")
public @interface MainDiscountPolicy {}

// 사용 — 문자열 오타 없이 컴파일 타임에 체크
@Autowired
public OrderServiceImpl(@MainDiscountPolicy DiscountPolicy discountPolicy) { ... }
```

---

## 조회한 빈이 모두 필요할 때

```java
// 전략 패턴 구현: 할인 종류를 클라이언트가 선택
static class DiscountService {
    private final Map<String, DiscountPolicy> policyMap; // 빈 이름 → 구현체

    public int discount(Member member, int price, String discountCode) {
        DiscountPolicy policy = policyMap.get(discountCode);
        return policy.discount(member, price);
    }
}
```

- `Map<String, DiscountPolicy>`: 키=빈 이름, 값=해당 타입의 모든 빈
- `List<DiscountPolicy>`: 해당 타입 빈 전체 목록

---

## 자동 vs 수동 운영 기준

| 구분 | 추천 |
|------|------|
| 업무 로직 (Controller/Service/Repository) | **자동** (@Component 계열) |
| 기술 지원 (DB 연결, 공통 로그, AOP) | **수동** (명확하게 드러내기) |
| 다형성 적극 활용 (Map<String, Policy> 등) | 수동 또는 특정 패키지에 모아두기 |

---

## 면접 Q&A

**Q: @Autowired의 주입 우선순위 규칙은?**
A: 타입 매칭을 먼저 시도하고, 같은 타입 빈이 2개 이상이면 필드명/파라미터명으로 빈 이름을 추가 매칭한다. 여기서도 구분이 안 되면 `NoUniqueBeanDefinitionException`이 발생한다. `@Qualifier`는 `@Primary`보다 우선순위가 높다.

**Q: 왜 필드 주입을 사용하면 안 되나?**
A: DI 프레임워크 없이는 아무것도 할 수 없다. 순수 자바 코드로 단위 테스트를 작성할 때 의존관계를 외부에서 주입할 방법이 없어 테스트가 매우 어렵다. 생성자 주입은 생성자에 목 객체를 넘기면 되어 테스트하기 쉽다.

**Q: @Primary와 @Qualifier 중 어떤 것을 언제 쓰나?**
A: 대부분의 경우 기본으로 사용할 빈에 `@Primary`를 붙인다. 예외적으로 다른 빈을 주입받아야 할 때 `@Qualifier`로 명시적으로 지정한다. 예를 들어 메인 DataSource는 `@Primary`, 서브 DataSource는 `@Qualifier`로 구분한다. `@Qualifier`가 좁은 범위로 항상 우선순위가 높다.
