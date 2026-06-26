# 스프링 MVC - 기본 기능

> 스프링 MVC 1편 | MVC1

---

## 전체 흐름 한눈에 보기

```
로그
  System.out.println() 대신 Slf4j 로그 사용
  log.debug("data={}", data)  ← 로그 레벨 미도달 시 연산 자체가 발생하지 않음

요청 매핑
  @RequestMapping → @GetMapping / @PostMapping (HTTP 메서드 구분)
  @PathVariable   → /mapping/{userId} 경로 변수 조회
  params, headers, consumes, produces → 조건 추가 매핑

HTTP 요청 데이터 수신 3단계
  1. request.getParameter()           → GET 쿼리/POST Form 파라미터
  2. @RequestParam, @ModelAttribute   → 스프링이 바인딩
  3. @RequestBody, HttpEntity         → HTTP 메시지 바디 (JSON 등)

HTTP 응답 3방식
  1. 정적 리소스 (src/main/resources/static)
  2. 뷰 템플릿 (src/main/resources/templates, Thymeleaf)
  3. @ResponseBody / ResponseEntity   → HTTP 메시지 바디 직접 입력

HTTP 메시지 컨버터
  canRead()/canWrite() → 클래스 타입 + 미디어 타입 확인 후 read()/write() 호출
  0: ByteArrayHttpMessageConverter (byte[])
  1: StringHttpMessageConverter    (String)
  2: MappingJackson2HttpMessageConverter (객체/Map + application/json)
```

---

## 로그 사용법

```java
@Slf4j  // lombok — private static final Logger log = LoggerFactory.getLogger(...)
@RestController
public class LogTestController {

    @RequestMapping("/log-test")
    public String logTest() {
        String name = "Spring";
        log.trace("trace log={}", name);
        log.debug("debug log={}", name);
        log.info(" info log={}", name);
        log.warn(" warn log={}", name);
        log.error("error log={}", name);
        return "ok";
    }
}
```

### 로그 레벨 / 올바른 사용법

| 항목 | 내용 |
|------|------|
| 레벨 순서 | `TRACE > DEBUG > INFO > WARN > ERROR` |
| 개발 서버 | DEBUG 레벨 |
| 운영 서버 | INFO 레벨 |
| 잘못된 방법 | `log.debug("data=" + data)` — 레벨 미도달 시에도 문자열 연산 발생 |
| 올바른 방법 | `log.debug("data={}", data)` — 미도달 시 연산 없음 |

```properties
# application.properties
logging.level.root=info
logging.level.hello.springmvc=debug
```

> `@RestController` = `@Controller` + 모든 메서드에 `@ResponseBody`

---

## 요청 매핑

```java
@RestController
public class MappingController {

    // 기본 — HTTP 메서드 모두 허용
    @RequestMapping("/hello-basic")
    public String helloBasic() { return "ok"; }

    // HTTP 메서드 지정
    @RequestMapping(value = "/mapping-get-v1", method = RequestMethod.GET)
    public String mappingGetV1() { return "ok"; }

    // 단축 애노테이션
    @GetMapping("/mapping-get-v2")
    public String mappingGetV2() { return "ok"; }

    // PathVariable — 경로 변수
    @GetMapping("/mapping/{userId}")
    public String mappingPath(@PathVariable("userId") String data) {
        return "ok";
    }

    // PathVariable 다중
    @GetMapping("/mapping/users/{userId}/orders/{orderId}")
    public String mappingPath(@PathVariable String userId, @PathVariable Long orderId) {
        return "ok";
    }

    // 특정 파라미터 조건 추가 (params)
    @GetMapping(value = "/mapping-param", params = "mode=debug")
    public String mappingParam() { return "ok"; }

    // 특정 헤더 조건 (headers)
    @GetMapping(value = "/mapping-header", headers = "mode=debug")
    public String mappingHeader() { return "ok"; }

    // Content-Type 조건 (consumes) — 불일치 시 415
    @PostMapping(value = "/mapping-consume", consumes = "application/json")
    public String mappingConsumes() { return "ok"; }

    // Accept 조건 (produces) — 불일치 시 406
    @PostMapping(value = "/mapping-produce", produces = "text/html")
    public String mappingProduces() { return "ok"; }
}
```

### 회원 관리 API — 클래스 레벨 @RequestMapping

```java
@RestController
@RequestMapping("/mapping/users")
public class MappingClassController {
    @GetMapping              public String users()           { return "get users"; }
    @PostMapping             public String addUser()         { return "post user"; }
    @GetMapping("/{userId}") public String findUser(...)     { ... }
    @PatchMapping("/{userId}") public String updateUser(...) { ... }
    @DeleteMapping("/{userId}") public String deleteUser(...) { ... }
}
```

---

## HTTP 요청 — 헤더 조회

```java
@RequestMapping("/headers")
public String headers(
        HttpServletRequest request,
        HttpServletResponse response,
        HttpMethod httpMethod,          // HTTP 메서드
        Locale locale,                  // Accept-Language 첫 번째
        @RequestHeader MultiValueMap<String, String> headerMap,  // 전체 헤더
        @RequestHeader("host") String host,                      // 특정 헤더
        @CookieValue(value = "myCookie", required = false) String cookie
) { return "ok"; }
```

> `MultiValueMap`: 하나의 키에 여러 값 가능 — `map.get("keyA")` → `List<String>`

---

## HTTP 요청 파라미터

### @RequestParam

```java
// v2 — 파라미터 이름 명시
@RequestMapping("/request-param-v2")
public String v2(@RequestParam("username") String memberName,
                 @RequestParam("age") int memberAge) { return "ok"; }

// v3 — 파라미터 이름 = 변수명 동일 시 생략 가능
public String v3(@RequestParam String username, @RequestParam int age) { ... }

// v4 — String/int/Integer 단순 타입은 @RequestParam 자체 생략 가능
public String v4(String username, int age) { ... }

// required + defaultValue
public String required(
        @RequestParam(required = true) String username,
        @RequestParam(required = false) Integer age) { ... }

public String defaultVal(
        @RequestParam(required = true, defaultValue = "guest") String username,
        @RequestParam(required = false, defaultValue = "-1") int age) { ... }

// Map으로 수신
public String paramMap(@RequestParam Map<String, Object> paramMap) { ... }
```

> 주의: `required = false`에 `int` 사용 시 null → 500. `Integer` 또는 `defaultValue` 사용

> 스프링 부트 3.2 이상: `@RequestParam`/`@PathVariable`에서 이름 생략 시 `-parameters` 컴파일 옵션 필요.
> **권장: 항상 이름을 명시** (`@RequestParam("username")`)

### @ModelAttribute

```java
@Data  // @Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor
public class HelloData {
    private String username;
    private int age;
}

// v1 — 자동으로 객체 생성 + 파라미터 이름으로 setter 호출 + model.addAttribute 자동 적용
@RequestMapping("/model-attribute-v1")
public String v1(@ModelAttribute HelloData helloData) { return "ok"; }

// v2 — @ModelAttribute 생략 가능 (단순 타입이 아닌 경우)
public String v2(HelloData helloData) { return "ok"; }
```

| 애노테이션 생략 규칙 | 처리 방식 |
|------------------|---------|
| `String`, `int`, `Integer` 등 단순 타입 | `@RequestParam` |
| 나머지 객체 타입 | `@ModelAttribute` |

---

## HTTP 요청 메시지 바디

### 단순 텍스트

```java
// v3 — HttpEntity (헤더 + 바디 함께 조회, @RequestParam/@ModelAttribute 불가)
@PostMapping("/request-body-string-v3")
public HttpEntity<String> v3(HttpEntity<String> httpEntity) {
    String messageBody = httpEntity.getBody();
    return new HttpEntity<>("ok");
}

// v4 — @RequestBody (가장 편리)
@ResponseBody
@PostMapping("/request-body-string-v4")
public String v4(@RequestBody String messageBody) { return "ok"; }
```

### JSON

```java
// v3 — @RequestBody로 객체 직접 수신 (MappingJackson2HttpMessageConverter 사용)
@ResponseBody
@PostMapping("/request-body-json-v3")
public String v3(@RequestBody HelloData data) {
    log.info("username={}, age={}", data.getUsername(), data.getAge());
    return "ok";
}

// v5 — @RequestBody로 받고 @ResponseBody로 JSON 반환
@ResponseBody
@PostMapping("/request-body-json-v5")
public HelloData v5(@RequestBody HelloData data) {
    return data;  // 객체 → JSON 직렬화 후 응답
}
```

> `@RequestBody`는 생략 불가 — 생략 시 `@ModelAttribute`로 처리됨 (요청 파라미터로 읽음)

---

## HTTP 응답

### 정적 리소스 / 뷰 템플릿

```java
@Controller
public class ResponseViewController {

    @RequestMapping("/response-view-v1")
    public ModelAndView v1() {
        return new ModelAndView("response/hello").addObject("data", "hello!");
    }

    @RequestMapping("/response-view-v2")
    public String v2(Model model) {
        model.addAttribute("data", "hello!!");
        return "response/hello";  // → templates/response/hello.html
    }
}
```

- 정적 리소스: `src/main/resources/static/` — URL 경로 그대로 파일 반환
- 뷰 템플릿: `src/main/resources/templates/` — Thymeleaf 렌더링

### HTTP API 응답 (메시지 바디 직접 입력)

```java
// ResponseEntity — HTTP 상태 코드 함께 설정
@GetMapping("/response-body-string-v2")
public ResponseEntity<String> v2() {
    return new ResponseEntity<>("ok", HttpStatus.OK);
}

// @ResponseBody — 간편하게
@ResponseBody
@GetMapping("/response-body-string-v3")
public String v3() { return "ok"; }

// JSON 반환
@GetMapping("/response-body-json-v1")
public ResponseEntity<HelloData> jsonV1() {
    HelloData data = new HelloData();
    data.setUsername("userA"); data.setAge(20);
    return new ResponseEntity<>(data, HttpStatus.OK);
}

// @ResponseStatus + @ResponseBody
@ResponseStatus(HttpStatus.OK)
@ResponseBody
@GetMapping("/response-body-json-v2")
public HelloData jsonV2() { ... }
```

> `@RestController` = `@Controller` + 전체 메서드에 `@ResponseBody`

---

## HTTP 메시지 컨버터

### 스프링 부트 기본 등록 컨버터 (우선순위 순)

| 순서 | 컨버터 | 클래스 타입 | 미디어 타입 |
|------|--------|-----------|-----------|
| 0 | `ByteArrayHttpMessageConverter` | `byte[]` | `*/*` |
| 1 | `StringHttpMessageConverter` | `String` | `*/*` |
| 2 | `MappingJackson2HttpMessageConverter` | 객체 또는 `HashMap` | `application/json` |

### 동작 방식

```java
public interface HttpMessageConverter<T> {
    boolean canRead(Class<?> clazz, MediaType mediaType);   // 읽기 가능 여부
    boolean canWrite(Class<?> clazz, MediaType mediaType);  // 쓰기 가능 여부
    T read(...);    // 요청 바디 → 객체
    void write(...); // 객체 → 응답 바디
}
```

- **요청 시**: `@RequestBody` 파라미터 타입 + `Content-Type` → `canRead()` → `read()`
- **응답 시**: 반환 타입 + `Accept` 헤더(또는 `produces`) → `canWrite()` → `write()`

---

## 요청 매핑 핸들러 어댑터 구조

```
HTTP 요청
  → DispatcherServlet
  → RequestMappingHandlerAdapter
      → ArgumentResolver (supportsParameter → resolveArgument)
          → HttpMessageConverter (@RequestBody, HttpEntity 처리)
      → 컨트롤러 메서드 호출
      → ReturnValueHandler (지원 여부 확인 → 처리)
          → HttpMessageConverter (@ResponseBody, HttpEntity 처리)
  → HTTP 응답
```

- `ArgumentResolver`: 30개 이상 기본 제공 — `HttpServletRequest`, `@RequestParam`, `@ModelAttribute`, `@RequestBody`, `HttpEntity` 등
- `ReturnValueHandler`: 10개 이상 기본 제공 — `ModelAndView`, `@ResponseBody`, `HttpEntity`, `String` 등
- 확장: `WebMvcConfigurer` 구현 → `addArgumentResolvers()`, `extendMessageConverters()` 오버라이드

---

## 면접 Q&A

**Q: @RequestParam과 @ModelAttribute의 차이는?**
A: `@RequestParam`은 요청 파라미터 하나를 단순 타입(String, int)으로 바인딩한다. `@ModelAttribute`는 요청 파라미터 여러 개를 객체로 한 번에 바인딩하며, 파라미터 이름으로 객체의 setter를 호출한다. 또한 `@ModelAttribute`는 바인딩한 객체를 자동으로 Model에 추가한다. 스프링 MVC는 파라미터에 애노테이션 생략 시 단순 타입이면 `@RequestParam`, 객체 타입이면 `@ModelAttribute`로 처리한다.

**Q: @RequestBody 사용 시 @ModelAttribute와의 차이는?**
A: `@RequestBody`는 HTTP 메시지 바디를 `HttpMessageConverter`를 통해 읽는다. `@ModelAttribute`는 URL 쿼리 파라미터나 Form 데이터(요청 파라미터)를 읽는다. JSON API 요청에서는 데이터가 메시지 바디에 담기므로 반드시 `@RequestBody`를 사용해야 한다. `@RequestBody`는 생략 불가 — 생략 시 `@ModelAttribute`로 처리되어 바디를 읽지 않는다.

**Q: HttpMessageConverter가 선택되는 기준은?**
A: 요청 시에는 `@RequestBody`의 파라미터 클래스 타입과 HTTP 요청의 `Content-Type`을 조합해 `canRead()`를 만족하는 컨버터를 선택한다. 응답 시에는 반환 타입과 HTTP 요청의 `Accept` 헤더를 조합해 `canWrite()`를 만족하는 컨버터를 선택한다. 컨버터 목록을 순서대로 확인하며 처음 매칭되는 컨버터를 사용한다.
