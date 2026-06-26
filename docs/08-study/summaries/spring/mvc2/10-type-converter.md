# 스프링 타입 컨버터

> 스프링 MVC 2편 | MVC2

---

## 전체 흐름 한눈에 보기

```
스프링 타입 변환이 적용되는 곳
  @RequestParam / @ModelAttribute / @PathVariable / @Value / 뷰 템플릿

Converter 인터페이스
  T convert(S source) — S타입 → T타입 변환
  양방향 필요 시 A→B, B→A 각각 구현

ConversionService
  컨버터 등록(ConverterRegistry)과 사용(ConversionService) 분리 — ISP 원칙
  WebConfig.addFormatters()로 스프링 내부 ConversionService에 등록

Formatter (문자에 특화된 Converter)
  String print(T object, Locale locale) — 객체 → 문자
  T parse(String text, Locale locale)   — 문자 → 객체
  FormattingConversionService: Converter + Formatter 모두 등록 가능 (어댑터 패턴)

어노테이션 기반 포맷터 (실무에서 가장 자주 사용)
  @NumberFormat(pattern = "###,###")       — 필드별 숫자 포맷
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") — 필드별 날짜 포맷

중요: HttpMessageConverter에는 컨버전 서비스 미적용
  JSON 변환 → Jackson 내부 사용 → Jackson 설정으로 포맷 변경
```

---

## Converter 인터페이스

```java
package org.springframework.core.convert.converter;
public interface Converter<S, T> {
    T convert(S source);
}
```

### 구현 예 — 문자 ↔ 숫자

```java
// String → Integer
@Slf4j
public class StringToIntegerConverter implements Converter<String, Integer> {
    @Override
    public Integer convert(String source) {
        log.info("convert source={}", source);
        return Integer.valueOf(source);
    }
}

// Integer → String
@Slf4j
public class IntegerToStringConverter implements Converter<Integer, String> {
    @Override
    public String convert(Integer source) {
        log.info("convert source={}", source);
        return String.valueOf(source);
    }
}
```

### 구현 예 — 문자 ↔ 사용자 정의 타입 (IpPort)

```java
// IpPort 타입 (127.0.0.1:8080 형식)
@Getter
@EqualsAndHashCode  // 모든 필드로 equals/hashCode 생성
public class IpPort {
    private String ip;
    private int port;
    public IpPort(String ip, int port) { this.ip = ip; this.port = port; }
}

// String → IpPort
public class StringToIpPortConverter implements Converter<String, IpPort> {
    @Override
    public IpPort convert(String source) {
        String[] split = source.split(":");
        return new IpPort(split[0], Integer.parseInt(split[1]));
    }
}

// IpPort → String
public class IpPortToStringConverter implements Converter<IpPort, String> {
    @Override
    public String convert(IpPort source) {
        return source.getIp() + ":" + source.getPort();
    }
}
```

### Converter 종류

| 인터페이스 | 용도 |
|-----------|------|
| `Converter` | 기본 타입 컨버터 |
| `ConverterFactory` | 전체 클래스 계층 구조가 필요할 때 |
| `GenericConverter` | 정교한 구현, 대상 필드 애노테이션 정보 사용 가능 |
| `ConditionalGenericConverter` | 특정 조건이 참인 경우에만 실행 |

---

## ConversionService

```java
public interface ConversionService {
    boolean canConvert(Class<?> sourceType, Class<?> targetType);
    <T> T convert(Object source, Class<T> targetType);
}
```

### DefaultConversionService — 테스트 코드로 동작 확인

```java
@Test
void conversionService() {
    DefaultConversionService conversionService = new DefaultConversionService();
    // 등록
    conversionService.addConverter(new StringToIntegerConverter());
    conversionService.addConverter(new IntegerToStringConverter());
    conversionService.addConverter(new StringToIpPortConverter());
    conversionService.addConverter(new IpPortToStringConverter());

    // 사용 — 타입 컨버터를 전혀 몰라도 됨
    assertThat(conversionService.convert("10", Integer.class)).isEqualTo(10);
    assertThat(conversionService.convert(10, String.class)).isEqualTo("10");
    IpPort ipPort = conversionService.convert("127.0.0.1:8080", IpPort.class);
    assertThat(ipPort).isEqualTo(new IpPort("127.0.0.1", 8080));
}
```

> **ISP 원칙**: `ConversionService`(사용)와 `ConverterRegistry`(등록)로 인터페이스 분리.  
> 컨버터 사용 클라이언트는 `ConversionService`만 의존 — 어떻게 등록되는지 알 필요 없음.

---

## 스프링에 Converter 적용하기

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToIntegerConverter());
        registry.addConverter(new IntegerToStringConverter());
        registry.addConverter(new StringToIpPortConverter());
        registry.addConverter(new IpPortToStringConverter());
    }
}
```

```java
// 등록 후 @RequestParam에서 자동 적용
@GetMapping("/ip-port")
public String ipPort(@RequestParam IpPort ipPort) {
    System.out.println("ipPort IP = " + ipPort.getIp());   // 127.0.0.1
    System.out.println("ipPort PORT = " + ipPort.getPort()); // 8080
    return "ok";
}
// http://localhost:8080/ip-port?ipPort=127.0.0.1:8080
```

> 추가한 컨버터가 스프링 기본 컨버터보다 **높은 우선순위**를 가진다.

---

## 뷰 템플릿에 컨버터 적용 (타임리프)

```html
<!-- ${{...}}: 타임리프가 자동으로 ConversionService.convert() 호출 -->
<li>${number}: <span th:text="${number}"></span></li>        <!-- 10000 그대로 -->
<li>${{number}}: <span th:text="${{number}}"></span></li>     <!-- 10,000 (MyNumberFormatter) -->
<li>${ipPort}: <span th:text="${ipPort}"></span></li>         <!-- IpPort@xxx (toString) -->
<li>${{ipPort}}: <span th:text="${{ipPort}}"></span></li>     <!-- 127.0.0.1:8080 (Converter) -->
```

> **`th:field`도 자동으로 컨버전 서비스 적용** — 폼 입력값 렌더링/바인딩 시 자동 변환.

---

## Formatter — 문자 특화 Converter

```java
public interface Formatter<T> extends Printer<T>, Parser<T> {}

public interface Printer<T> {
    String print(T object, Locale locale);  // 객체 → 문자
}
public interface Parser<T> {
    T parse(String text, Locale locale) throws ParseException;  // 문자 → 객체
}
```

### 구현 예 — 숫자 1000 ↔ "1,000"

```java
@Slf4j
public class MyNumberFormatter implements Formatter<Number> {
    @Override
    public Number parse(String text, Locale locale) throws ParseException {
        log.info("text={}, locale={}", text, locale);
        return NumberFormat.getInstance(locale).parse(text);  // "1,000" → 1000
    }

    @Override
    public String print(Number object, Locale locale) {
        log.info("object={}, locale={}", object, locale);
        return NumberFormat.getInstance(locale).format(object);  // 1000 → "1,000"
    }
}
```

> `NumberFormat.getInstance(locale)` — Locale별로 다른 숫자 포맷 자동 처리.

### FormattingConversionService — Converter + Formatter 함께 등록

```java
@Test
void formattingConversionService() {
    DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
    conversionService.addConverter(new StringToIpPortConverter());    // Converter 등록
    conversionService.addConverter(new IpPortToStringConverter());
    conversionService.addFormatter(new MyNumberFormatter());          // Formatter 등록

    // 사용 — convert() 하나로 통일
    assertThat(conversionService.convert(1000, String.class)).isEqualTo("1,000");
    assertThat(conversionService.convert("1,000", Long.class)).isEqualTo(1000L);
}
```

> `DefaultFormattingConversionService`는 기본 통화/숫자 포맷터 내장.  
> 스프링 부트는 내부적으로 `WebConversionService`(`DefaultFormattingConversionService` 상속)를 사용.

### WebConfig에 Formatter 등록

```java
@Override
public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new StringToIpPortConverter());
    registry.addConverter(new IpPortToStringConverter());
    registry.addFormatter(new MyNumberFormatter());  // 추가
    // StringToIntegerConverter는 주석 처리 필수 — MyNumberFormatter와 기능 충돌
    // 컨버터 > 포맷터 우선순위이므로 컨버터가 있으면 포맷터 미적용
}
```

---

## 어노테이션 기반 포맷터 — 실무에서 자주 사용

```java
@Data
static class Form {
    @NumberFormat(pattern = "###,###")
    private Integer number;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime localDateTime;
}
```

```
입력: "10,000" → Integer 10000
출력: Integer 10000 → "10,000"

입력: "2021-01-01 10:30:00" → LocalDateTime
출력: LocalDateTime → "2021-01-01 10:30:00"
```

> 필드마다 다른 포맷 지정 가능 → 범용 Formatter로는 불가능했던 유연성 제공.

---

## 주의: HttpMessageConverter에는 컨버전 서비스 미적용

```
컨버전 서비스 적용 범위:
  ✅ @RequestParam / @ModelAttribute / @PathVariable
  ✅ 뷰 템플릿 (타임리프 ${{...}}, th:field)

컨버전 서비스 미적용:
  ❌ HttpMessageConverter (@RequestBody, @ResponseBody, ResponseEntity)
     → Jackson 라이브러리 내부 동작 (Jackson ObjectMapper 설정으로 포맷 변경)
```

> JSON 숫자/날짜 포맷을 바꾸려면 `@JsonFormat`, `ObjectMapper` 설정 등 Jackson 쪽을 건드려야 한다.

---

## 면접 Q&A

**Q: 스프링에서 @RequestParam Integer data가 String "10"을 자동 변환하는 원리는?**
A: 스프링은 내부적으로 `ConversionService`를 사용한다. `@RequestParam`을 처리하는 `RequestParamMethodArgumentResolver`가 바인딩 시 `ConversionService.convert()`를 호출해 문자 "10"을 Integer 10으로 변환한다. 개발자가 커스텀 타입을 만들면 `Converter<String, MyType>`을 구현하고 `WebConfig.addFormatters()`에 등록하면 같은 방식으로 자동 변환된다.

**Q: Converter와 Formatter의 차이는?**
A: `Converter`는 범용 타입 변환 인터페이스로 어떤 타입 간의 변환이든 처리할 수 있다. `Formatter`는 **문자(String)와 객체 간 변환에 특화**된 인터페이스로 `print(객체→문자)`와 `parse(문자→객체)` 두 메서드를 가지며 `Locale` 정보도 지원한다. 웹 애플리케이션에서는 요청/응답이 문자 기반이므로 대부분 Formatter 또는 `@NumberFormat`/`@DateTimeFormat` 어노테이션으로 충분하다.

**Q: @NumberFormat과 @DateTimeFormat의 동작 방식은?**
A: 두 어노테이션은 `AnnotationFormatterFactory`를 통해 동작하는 어노테이션 기반 포맷터다. 필드에 붙이면 해당 필드의 바인딩/렌더링 시 지정된 pattern대로 변환된다. `@RequestParam`/`@ModelAttribute`/뷰 템플릿에서 자동 적용되지만, `@RequestBody`(JSON)는 Jackson이 처리하므로 적용되지 않는다.
