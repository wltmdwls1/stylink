# 컴포넌트 스캔

> 스프링 기본편 | BASIC

---

## 전체 흐름 한눈에 보기

```
문제: 수십~수백 개의 @Bean을 직접 등록하면 누락 위험, 설정 비대화
해결: @ComponentScan + @Autowired

@ComponentScan
  → @Component가 붙은 클래스를 자동으로 스캔 → 스프링 빈 등록
  → 빈 이름 기본: 클래스명 첫 글자 소문자 (MemberServiceImpl → memberServiceImpl)

@Autowired
  → 생성자에 붙이면 타입으로 일치하는 빈을 자동 주입
  → getBean(MemberRepository.class) 와 동일

탐색 위치
  → basePackages 미지정 시 @ComponentScan 붙은 클래스의 패키지부터 시작
  → 권장: 설정 클래스를 프로젝트 최상단에 두기 (@SpringBootApplication이 이것!)

충돌
  → 자동 vs 자동: ConflictingBeanDefinitionException
  → 수동 vs 자동: 스프링 부트에서는 오류(기본값 변경됨)
```

---

## AutoAppConfig 구성

```java
@Configuration
@ComponentScan(
    excludeFilters = @Filter(type = FilterType.ANNOTATION, classes = Configuration.class)
)
public class AutoAppConfig {
    // @Bean 없음 — 자동 스캔
}
```

각 클래스에 `@Component` + 생성자에 `@Autowired` 추가:
```java
@Component
public class OrderServiceImpl implements OrderService {
    private final MemberRepository memberRepository;
    private final DiscountPolicy discountPolicy;

    @Autowired
    public OrderServiceImpl(MemberRepository memberRepository, DiscountPolicy discountPolicy) {
        this.memberRepository = memberRepository;
        this.discountPolicy = discountPolicy;
    }
}
```

---

## 컴포넌트 스캔 기본 대상

| 애노테이션 | 역할 |
|-----------|------|
| `@Component` | 컴포넌트 스캔 대상 |
| `@Controller` | 스프링 MVC 컨트롤러로 인식 |
| `@Service` | 특별한 처리 없음, 비즈니스 계층 인식 목적 |
| `@Repository` | 데이터 접근 계층, DB 예외를 스프링 예외로 변환 |
| `@Configuration` | 설정 정보, 싱글톤 추가 처리 |

모두 내부에 `@Component`를 포함하고 있다 — 자바 언어 기능이 아닌 스프링이 지원하는 방식.

---

## 탐색 위치

```java
@ComponentScan(basePackages = "hello.core")          // 하위 패키지 모두 탐색
@ComponentScan(basePackageClasses = AppConfig.class) // 해당 클래스의 패키지 기준
// 지정 없으면 → @ComponentScan 붙은 클래스의 패키지부터
```

**권장**: 설정 클래스를 프로젝트 최상단에 두고 `basePackages` 생략.
스프링 부트의 `@SpringBootApplication` 안에 `@ComponentScan`이 들어있고, 최상단에 위치하는 것이 관례.

---

## 필터

```java
@ComponentScan(
    includeFilters = @Filter(type = FilterType.ANNOTATION, classes = MyIncludeComponent.class),
    excludeFilters  = @Filter(type = FilterType.ANNOTATION, classes = MyExcludeComponent.class)
)
```

- `includeFilters`: 사용할 일 거의 없음
- `excludeFilters`: 가끔 사용
- 스프링의 기본 설정에 최대한 맞추어 사용 권장

---

## 중복 등록과 충돌

| 충돌 유형 | 결과 |
|----------|------|
| 자동 vs 자동 (같은 이름) | `ConflictingBeanDefinitionException` |
| 수동 vs 자동 (같은 이름) | 기존 스프링: 수동 빈이 자동 빈을 오버라이딩. **스프링 부트: 오류** |

스프링 부트 에러:
```
Consider renaming one of the beans or enabling overriding by setting
spring.main.allow-bean-definition-overriding=true
```

> 의도하지 않은 충돌이 대부분 → 애매한 버그가 됨 → 부트에서 기본값을 오류로 변경

---

## 면접 Q&A

**Q: @ComponentScan과 @Bean의 차이는?**
A: `@Bean`은 개발자가 설정 클래스에 직접 빈을 등록하는 방식이다. `@ComponentScan`은 `@Component`가 붙은 클래스를 자동으로 찾아 스프링 빈으로 등록한다. 스프링 부트는 `@ComponentScan`을 기본으로 사용하고, 수동 등록(`@Bean`)은 기술 지원 빈이나 다형성이 필요한 비즈니스 로직 빈에서 주로 사용한다.

**Q: @Repository가 스프링 예외를 변환해주는 이유는?**
A: 데이터 접근 기술(JPA, JDBC 등)은 각자 다른 예외를 던진다. `@Repository`를 붙이면 스프링이 AOP를 통해 기술별 예외를 `DataAccessException` 계층의 스프링 예외로 변환해준다. 덕분에 서비스 레이어가 특정 데이터 접근 기술에 종속되지 않는다.

**Q: 스프링 부트에서 수동/자동 빈이 충돌나면 어떻게 되나?**
A: 스프링 부트 기본 설정에서는 오류가 발생한다. 과거에는 수동 빈이 자동 빈을 오버라이딩했지만, 이런 경우 대부분 의도치 않은 설정 충돌로 디버깅이 어려운 버그가 발생해서 스프링 부트에서 기본값을 오류로 바꿨다.
