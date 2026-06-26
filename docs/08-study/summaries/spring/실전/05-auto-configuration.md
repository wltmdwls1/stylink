# 자동 구성(Auto Configuration)

## 전체 흐름 한눈에 보기

```
문제: DB 쓸 때마다 DataSource/TransactionManager/JdbcTemplate 빈 직접 등록 반복

발견: @Configuration 주석 처리해도 빈이 존재 → 스프링 부트가 자동 등록

원리: @SpringBootApplication → @EnableAutoConfiguration
     → AutoConfigurationImportSelector (ImportSelector)
     → 모든 jar의 META-INF/spring/AutoConfiguration.imports 파일 읽기
     → @Conditional 조건 체크 → 빈 등록
```

---

## 스프링 부트 자동 구성이란

일반적으로 자주 사용하는 빈들을 자동으로 등록해주는 기능. `spring-boot-autoconfigure` 라이브러리에 집결.

| 자동 구성 클래스 | 등록하는 빈 |
|---|---|
| `JdbcTemplateAutoConfiguration` | `JdbcTemplate` |
| `DataSourceAutoConfiguration` | `DataSource` |
| `DataSourceTransactionManagerAutoConfiguration` | `TransactionManager` |

### 핵심 규칙: 개발자 빈 우선

`@ConditionalOnMissingBean`으로 개발자가 직접 등록한 빈이 있으면 자동 구성은 동작하지 않음.

---

## @Conditional

소스코드 변경 없이 조건에 따라 빈을 등록/미등록하는 메커니즘.

```java
public interface Condition {
    boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}
```

- `true` → 빈 등록, `false` → 등록 안 함
- `ConditionContext`로 환경 변수, 스프링 컨테이너 정보 접근 가능

### @ConditionalOnXxx (스프링 부트 제공)

직접 `Condition`을 구현할 필요 없이 바로 사용 가능한 편의 애노테이션:

| 애노테이션 | 조건 |
|---|---|
| `@ConditionalOnClass` | 지정 클래스 존재 시 |
| `@ConditionalOnMissingBean` | 지정 빈 없을 때 |
| `@ConditionalOnProperty` | 환경 값이 일치할 때 |
| `@ConditionalOnBean` | 지정 빈 존재 시 |

```java
// property 조건 예시
@ConditionalOnProperty(name = "memory", havingValue = "on")
public class MemoryConfig { ... }
```

환경 값 전달 방법:
- VM 옵션: `-Dmemory=on`
- 프로그램 인수: `--memory=on`
- `application.properties`: `memory=on`

---

## 자동 구성 라이브러리 만들기

라이브러리를 jar에 추가만 하면 빈이 자동 등록되도록 하는 방법.

### 1단계: @AutoConfiguration + @ConditionalOnXxx 클래스 작성

```java
@AutoConfiguration
@ConditionalOnProperty(name = "memory", havingValue = "on")
public class MemoryAutoConfig {
    @Bean public MemoryController memoryController() { ... }
    @Bean public MemoryFinder memoryFinder() { ... }
}
```

### 2단계: AutoConfiguration.imports 파일 등록 (필수!)

```
src/main/resources/META-INF/spring/
org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
내용:
```
memory.MemoryAutoConfig
```

이 파일이 없으면 자동 구성이 동작하지 않음.

---

## 자동 구성 동작 원리

### @SpringBootApplication 내부 구조

```java
@SpringBootConfiguration
@EnableAutoConfiguration          // 핵심
@ComponentScan(excludeFilters = {
    @Filter(AutoConfigurationExcludeFilter.class) })  // @AutoConfiguration 제외
public @interface SpringBootApplication {}
```

### @EnableAutoConfiguration 내부 구조

```java
@Import(AutoConfigurationImportSelector.class)  // 동적 ImportSelector
public @interface EnableAutoConfiguration {}
```

### AutoConfigurationImportSelector 동작

`ImportSelector`를 구현해 모든 jar의 `AutoConfiguration.imports` 파일을 동적으로 읽어 설정 정보 목록 반환.

### 전체 실행 순서

```
java -jar app.jar
→ @SpringBootApplication
→ @EnableAutoConfiguration
→ AutoConfigurationImportSelector.selectImports()
→ 모든 jar의 META-INF/spring/AutoConfiguration.imports 읽기
→ 나열된 AutoConfig 클래스들 로드
→ @Conditional 조건 체크
→ 조건 만족한 것만 빈 등록
```

### ImportSelector: 정적 vs 동적

```java
// 정적: 코드에 대상이 고정
@Import({AConfig.class, BConfig.class})

// 동적: ImportSelector로 런타임에 결정
public class MySelector implements ImportSelector {
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[]{"hello.selector.HelloConfig"};
    }
}
```

---

## 강의 권장 방식 vs stylink 적용 포인트

**강의 권장 방식:**
- DataSource, TransactionManager, JdbcTemplate → 자동 구성이 등록
- 개발자는 `application.properties`에 DB URL/사용자 등 설정만 하면 됨
- 커스터마이징 필요 시 `@Bean`으로 직접 등록 → 자동 구성보다 우선 적용

**stylink 적용 포인트: 강의 방식 그대로 적용**
- `spring-boot-starter-data-jpa` → DataSource, JPA, TransactionManager 자동 구성
- `application.yml`의 `spring.datasource.*`로 설정 주입 (다음 챕터에서 다룸)
- 직접 자동 구성을 만들 일은 없음; 읽을 수 있으면 충분

**면접 포인트:**
- "스프링 부트 자동 구성이란?" → `@EnableAutoConfiguration` → `AutoConfigurationImportSelector` → `AutoConfiguration.imports` 파일 기반으로 조건에 맞는 빈 자동 등록
- "`@ConditionalOnMissingBean`이 왜 중요한가?" → 개발자가 직접 등록한 빈이 있으면 자동 구성을 건너뜀 → 개발자 빈이 항상 우선
- "DataSource 빈은 누가 등록하나?" → `DataSourceAutoConfiguration`이 자동 등록. 개발자가 `@Bean DataSource`를 등록하면 이게 우선
- "자동 구성 클래스가 컴포넌트 스캔 대상이 되면 안 되는 이유?" → `AutoConfiguration.imports` 파일을 통해서만 로드되어야 함. 스캔되면 의도치 않은 순서/중복 발생
