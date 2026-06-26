# 외부설정과 프로필2

## 전체 흐름 한눈에 보기

```
외부 설정을 어떻게 읽어서 쓸 것인가?

방법1: Environment.getProperty(key, Type) → 타입 변환 지원, 반복 코드 많음
방법2: @Value("${key}") → 간편하지만 키를 하나씩 지정
방법3: @ConfigurationProperties("prefix") → 묶음 객체, 타입 안전 (권장)

@ConfigurationProperties 발전 과정:
V1: Setter 방식 (변경 가능 → 위험)
V2: 생성자 방식 (불변 → 권장)
V3: + @Validated Bean Validation (로딩 시점 검증 → 가장 안전)

YAML: 계층형 설정 파일, --- 구분자로 프로필 분리
@Profile: @Conditional 기반, 프로필에 따라 빈 자체를 교체
```

---

## 예제 도메인: MyDataSource

```java
// 외부 설정으로 주입받을 설정값 묶음
// url, username, password, maxConnection(int), timeout(Duration), options(List<String>)

// application.properties
my.datasource.url=local.db.com
my.datasource.username=local_user
my.datasource.password=local_pw
my.datasource.etc.max-connection=1       // 캐밥 표기법 권장
my.datasource.etc.timeout=3500ms
my.datasource.etc.options=CACHE,ADMIN
```

캐밥 표기법(max-connection) ↔ 낙타 표기법(maxConnection): 스프링이 자동 변환(Relaxed Binding).

---

## 방법1: Environment 직접 사용

```java
@Bean
public MyDataSource myDataSource() {
    String url = env.getProperty("my.datasource.url");
    int maxConnection = env.getProperty("my.datasource.etc.max-connection", Integer.class);
    Duration timeout   = env.getProperty("my.datasource.etc.timeout", Duration.class);
    List<String> options = env.getProperty("my.datasource.etc.options", List.class);
    return new MyDataSource(url, maxConnection, timeout, options);
}
```

스프링 내부 변환기: 문자열 → Integer, Duration(`3500ms` → `PT3.5S`), List(`A,B` → `[A, B]`) 자동 변환.

단점: `getProperty()` 호출이 설정값 개수만큼 반복됨.

---

## 방법2: @Value

```java
@Value("${my.datasource.url}")
private String url;

@Value("${my.datasource.etc.max-connection}")
private int maxConnection;

// 기본값: 키가 없으면 1 사용
@Value("${my.datasource.etc.max-connection:1}")
private int maxConnectionWithDefault;
```

- 필드 주입 / 파라미터 주입 모두 사용 가능
- 내부적으로 `Environment`를 통해 조회
- 단점: 키를 하나씩 지정, 설정이 많아지면 복잡

---

## 방법3: @ConfigurationProperties (권장)

외부 설정 묶음을 타입 안전한 객체로 변환. 오타나 타입 불일치는 로딩 시점에 발견.

### V1: Setter 방식

```java
@Data
@ConfigurationProperties("my.datasource")
public class MyDataSourcePropertiesV1 {
    private String url;
    private Etc etc = new Etc();

    @Data
    public static class Etc {
        private int maxConnection;       // max-connection 자동 변환
        private Duration timeout;
        private List<String> options = new ArrayList<>();
    }
}
```

```java
@EnableConfigurationProperties(MyDataSourcePropertiesV1.class)
public class MyDataSourceConfigV1 {
    private final MyDataSourcePropertiesV1 properties;

    @Bean
    public MyDataSource myDataSource() {
        return new MyDataSource(
            properties.getUrl(),
            properties.getEtc().getMaxConnection(), ...);
    }
}
```

- `@EnableConfigurationProperties(Xxx.class)`: 해당 클래스를 빈으로 등록 + 외부 설정 바인딩
- `@ConfigurationPropertiesScan`: 컴포넌트 스캔 방식 자동 등록

문제: Setter가 있어 빈 등록 후에도 값 변경 가능 → 불변성 보장 안 됨

### V2: 생성자 방식 (불변)

```java
@Getter
@ConfigurationProperties("my.datasource")
public class MyDataSourcePropertiesV2 {
    private final String url;
    private final Etc etc;

    public MyDataSourcePropertiesV2(String url, @DefaultValue Etc etc) {
        this.url = url;
        this.etc = etc;
    }

    @Getter
    public static class Etc {
        private final int maxConnection;
        private final Duration timeout;
        private final List<String> options;

        public Etc(int maxConnection, Duration timeout,
                   @DefaultValue("DEFAULT") List<String> options) {
            this.maxConnection = maxConnection;
            this.timeout = timeout;
            this.options = options;
        }
    }
}
```

- 스프링 부트 3.0+: 생성자 1개면 `@ConstructorBinding` 생략 가능
- `@DefaultValue`: 키가 없을 때 기본값
  - `@DefaultValue Etc etc` → etc 관련 키 없으면 Etc 객체 생성 (필드는 null/0)
  - `@DefaultValue("DEFAULT") List<String> options` → options 없으면 `["DEFAULT"]`
- final 필드 + Setter 없음 → 초기화 후 변경 불가 (불변)

### V3: Bean Validation 추가

```java
@Getter
@ConfigurationProperties("my.datasource")
@Validated
public class MyDataSourcePropertiesV3 {
    @NotEmpty
    private String url;

    @Valid
    private Etc etc;

    @Getter
    public static class Etc {
        @Min(1) @Max(999)
        private int maxConnection;

        @DurationMin(seconds = 1) @DurationMax(seconds = 60)
        private Duration timeout;

        private List<String> options;
    }
}
```

의존성:
```groovy
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

검증 실패 시 **애플리케이션 로딩 시점에 예외 발생**:
```
Reason: 1 이상이어야 합니다
```

예외 발생 시점 비교:
- 컴파일 시점 예외 > 로딩 시점 예외 >> 런타임 예외

V3는 설정값 오류를 운영 중이 아닌 시작 시점에 즉시 잡아낸다.

---

## @ConfigurationProperties vs @Value 비교

| 기능 | @Value | @ConfigurationProperties |
|------|--------|--------------------------|
| 사용법 | 키 하나씩 지정 | prefix 기반 묶음 |
| 타입 변환 | O | O |
| 검증 | X | @Validated 가능 |
| 불변 | X | 생성자 방식으로 가능 |
| 권장 상황 | 설정 1~2개 | 설정 묶음 |

---

## YAML

```yaml
my:
  datasource:
    url: local.db.com
    username: local_user
    etc:
      max-connection: 1
      timeout: 60s
      options: LOCAL, CACHE
```

- 공백(보통 2칸)으로 계층 구조 표현
- `key:` 뒤에 공백 필수
- 스프링이 YAML 계층구조를 properties로 평탄화해 읽음

**주의**: `application.properties`와 `application.yml` 동시 존재 시 properties가 우선.
실무에서는 YAML 선호.

### 프로필 분리 (--- 구분자)

```yaml
# 기본값 (프로필 없어도 항상 적용)
my:
  datasource:
    url: local.db.com
---
spring:
  config:
    activate:
      on-profile: dev
my:
  datasource:
    url: dev.db.com
---
spring:
  config:
    activate:
      on-profile: prod
my:
  datasource:
    url: prod.db.com
```

- yml 구분자: `---` (properties는 `#---`)
- 챕터6의 3단계 합체 방식과 동일한 원리

---

## @Profile

설정값이 아닌 **빈 자체**를 프로필에 따라 교체.

```java
@Configuration
public class PayConfig {
    @Bean
    @Profile("default")
    public PayClient localPayClient() {
        return new LocalPayClient();
    }

    @Bean
    @Profile("prod")
    public PayClient prodPayClient() {
        return new ProdPayClient();
    }
}
```

- 프로필 미지정 → `default` → `LocalPayClient`
- `--spring.profiles.active=prod` → `ProdPayClient`
- `OrderService`는 `PayClient` 인터페이스만 의존 → 환경 교체에 투명

### @Profile의 정체

```java
@Conditional(ProfileCondition.class)
public @interface Profile {
    String[] value();
}
```

`@Profile`은 `@Conditional`의 편의 래퍼. 챕터5의 `@Conditional` + 챕터6의 프로필 개념이 합쳐진 결과.

---

## stylink 적용 포인트

현재 `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/stylink
    password: ${DB_PASSWORD}    # OS 환경변수에서 주입
```

- `${DB_PASSWORD}`: OS 환경변수 → 더 높은 우선순위로 덮어씀 (챕터6 원리)
- `spring.datasource.*`: `DataSourceAutoConfiguration`이 읽어 DataSource 자동 구성 (챕터5 원리)
- 로컬/개발/운영 분리 필요 시: `---` 구분자 합체 파일로 관리
- external-mock의 `PayClient` 구현체 교체 시 `@Profile` 활용 가능

---

## 면접 포인트

- **"@Value vs @ConfigurationProperties 차이는?"**
  → @Value는 키를 하나씩 지정하는 간단한 주입. @ConfigurationProperties는 prefix 기반 묶음 객체로 변환, 타입 안전 + @Validated로 로딩 시점 검증 가능 → 설정이 많으면 @ConfigurationProperties 권장

- **"@ConfigurationProperties에서 불변을 어떻게 보장하나?"**
  → Setter 제거 + 생성자 주입 방식(V2). final 필드로 초기화 후 변경 불가

- **"왜 검증을 로딩 시점에 하는 게 좋은가?"**
  → 운영 중 잘못된 설정으로 인한 장애는 원인 파악이 어려움. 로딩 시점에 실패하면 즉시 발견 가능

- **"@Profile이란?"**
  → @Conditional(ProfileCondition.class)의 편의 래퍼. spring.profiles.active 값과 일치하는 프로필이 활성화된 빈만 등록. 설정값이 아닌 빈 자체를 환경별로 교체할 때 사용

- **"Relaxed Binding이란?"**
  → 캐밥(max-connection) / 낙타(maxConnection) / 대문자(MAX_CONNECTION) 등 표기법이 달라도 스프링이 자동으로 매핑. properties에서는 캐밥, 자바에서는 낙타 표기법을 쓰는 관례를 지원
