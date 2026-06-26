# 스프링 컨테이너와 스프링 빈

> 스프링 기본편 | DEEP

---

## 전체 흐름 한눈에 보기

```
스프링 컨테이너 생성 (AnnotationConfigApplicationContext)
  ↓
구성 정보(AppConfig) 읽기
  ↓
@Bean 메서드 모두 실행 → 스프링 빈 등록
  ↓
의존관계 설정 (생성자 주입)
  ↓
사용: ac.getBean("memberService", MemberService.class)

빈 조회 방법
  → 이름으로 / 타입으로 / 상속관계 고려
  → 동일 타입 둘 이상 → NoUniqueBeanDefinitionException
  → 없는 빈 조회 → NoSuchBeanDefinitionException

BeanFactory vs ApplicationContext
  → BeanFactory: 빈 관리(최상위)
  → ApplicationContext: BeanFactory + i18n + 환경변수 + 이벤트 + 리소스
  → 실무: 항상 ApplicationContext 사용

설정 형식
  → 자바 코드(@Configuration) vs XML 중 자바 코드 권장
  → 내부적으로 BeanDefinition 메타정보로 추상화
```

---

## 스프링 컨테이너 생성 3단계

```
1단계: 컨테이너 생성
new AnnotationConfigApplicationContext(AppConfig.class)
→ 비어있는 스프링 컨테이너 생성, 구성정보 지정

2단계: 빈 등록
@Bean 메서드를 호출 → 반환 객체를 빈으로 등록
  빈 이름: 메서드명 (기본) / @Bean(name="...") 직접 지정 가능
  주의: 빈 이름 중복 금지!

3단계: 의존관계 설정
설정 정보(AppConfig)를 참고하여 DI 수행
싱글톤이므로 같은 빈 요청 시 동일 인스턴스 반환
```

---

## 빈 조회 방법

```java
// 기본: 이름 + 타입
ac.getBean("memberService", MemberService.class)
// 타입으로만 (동일 타입 둘 이상이면 오류!)
ac.getBean(MemberService.class)
// 구체 타입 (권장 X - 유연성↓)
ac.getBean("memberService", MemberServiceImpl.class)
// 없는 빈 → NoSuchBeanDefinitionException
```

동일 타입 빈이 둘 이상일 때:
```java
// NoUniqueBeanDefinitionException 발생
ac.getBean(MemberRepository.class)
// 해결: 빈 이름 지정
ac.getBean("memoryMemberRepository", MemberRepository.class)
// 해결: 해당 타입 모두 조회
Map<String, MemberRepository> all = ac.getBeansOfType(MemberRepository.class);
```

상속 관계:
- **부모 타입으로 조회하면 자식 타입도 모두 함께 조회됨**
- `Object` 타입으로 조회 → 모든 스프링 빈 조회

---

## BeanFactory vs ApplicationContext

```
BeanFactory (인터페이스)
  └─ ApplicationContext (인터페이스)
       ├─ MessageSource          → i18n (국제화)
       ├─ EnvironmentCapable     → 환경변수 (local/dev/prod 구분)
       ├─ ApplicationEventPublisher → 이벤트 발행·구독
       └─ ResourceLoader         → 파일·클래스패스·URL 리소스 조회
```

`BeanFactory`는 빈 조회만 제공. 직접 사용하는 경우는 거의 없음.
`ApplicationContext`는 빈 관리 + 애플리케이션에 필요한 모든 부가기능 제공 → **실무에선 항상 ApplicationContext**.

---

## 설정 형식 비교

| 방식 | 클래스 | 특징 |
|------|--------|------|
| 자바 코드 | `AnnotationConfigApplicationContext` | IDE 자동완성, 타입 안전, **권장** |
| XML | `GenericXmlApplicationContext` | 빈 정보를 컴파일 없이 변경 가능, 레거시에서 주로 사용 |

두 방식 모두 내부적으로 `BeanDefinition`(메타정보)으로 변환되어 스프링이 처리.
→ 스프링 컨테이너는 설정 형식에 종속되지 않음.

---

## BeanDefinition

스프링이 다양한 설정 형식을 지원할 수 있는 이유: `BeanDefinition` 추상화

주요 정보:
- `BeanClassName`: 생성할 빈의 클래스명
- `factoryBeanName` / `factoryMethodName`: @Bean 메서드 정보
- `Scope`: 싱글톤(기본)
- `lazyInit`: 지연 초기화 여부
- `InitMethodName` / `DestroyMethodName`: 생명주기 콜백

> 실무에서 BeanDefinition을 직접 다룰 일은 거의 없음. "스프링이 이런 메타정보를 기반으로 동작한다"는 개념 이해로 충분.

---

## 면접 Q&A

**Q: BeanFactory와 ApplicationContext의 차이는?**
A: BeanFactory는 스프링 컨테이너의 최상위 인터페이스로 빈 관리와 조회 기능만 제공한다. ApplicationContext는 BeanFactory를 상속하면서 국제화(MessageSource), 환경변수(EnvironmentCapable), 이벤트(ApplicationEventPublisher), 리소스 조회(ResourceLoader) 등의 부가기능을 추가로 제공한다. 실무에서는 항상 ApplicationContext를 사용한다.

**Q: 부모 타입으로 빈을 조회하면 어떻게 되나?**
A: 부모 타입으로 조회하면 해당 타입을 포함한 모든 자식 타입의 빈도 함께 조회된다. 예를 들어 `DiscountPolicy` 타입으로 조회하면 `FixDiscountPolicy`와 `RateDiscountPolicy` 빈이 모두 반환된다. 동일 타입이 둘 이상이면 `NoUniqueBeanDefinitionException`이 발생하므로 이름을 지정하거나 `getBeansOfType()`으로 Map으로 받아야 한다.

**Q: 스프링 컨테이너가 다양한 설정 형식(XML, 자바 코드)을 지원할 수 있는 이유는?**
A: 모든 설정 형식은 내부적으로 BeanDefinition이라는 메타정보로 변환되기 때문이다. 스프링 컨테이너는 BeanDefinition만 바라보고 동작하므로, 설정 형식이 무엇이든 동일하게 빈을 생성하고 관리할 수 있다.
