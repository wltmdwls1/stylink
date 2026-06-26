# 예외 처리와 오류 페이지

> 스프링 MVC 2편 | MVC2

---

## 전체 흐름 한눈에 보기

```
서블릿 예외 처리 (원리 이해)
  Exception 발생 → WAS까지 전파 → 500
  response.sendError(404) → WAS에 오류 기록 → 기본 오류 화면

오류 페이지 요청 흐름 (핵심!)
  1. WAS까지 예외/sendError 전파
  2. WAS → /error-page/500 내부 재요청 (클라이언트는 모름)
  3. 필터/인터셉터/컨트롤러 다시 호출 → 오류 페이지 렌더링

DispatcherType (필터 중복 호출 제거)
  REQUEST: 클라이언트 요청
  ERROR: 오류 페이지 내부 요청
  필터: setDispatcherTypes(REQUEST) → 기본값, ERROR 호출 시 필터 미적용
  인터셉터: excludePathPatterns("/error-page/**")로 제거

스프링 부트 자동화
  WebServerCustomizer + ErrorPage + ErrorPageController → 불필요
  BasicErrorController 자동 등록 → /error 경로 처리
  오류 뷰 파일만 resources/templates/error/ 에 넣으면 됨
  우선순위: 500.html > 5xx.html, 뷰 템플릿 > 정적 리소스
```

---

## 서블릿 예외 처리 — 원리

### 예외 발생 2가지 방식

```java
// 1. Exception 발생 → WAS까지 전파 → 500
@GetMapping("/error-ex")
public void errorEx() {
    throw new RuntimeException("예외 발생!");
}

// 2. response.sendError() → WAS에 오류 상태 기록
@GetMapping("/error-404")
public void error404(HttpServletResponse response) throws IOException {
    response.sendError(404, "404 오류!");
}
```

```
WAS(예외 전파) ← 필터 ← 서블릿 ← 인터셉터 ← 컨트롤러(예외발생)
WAS(sendError 확인) ← 필터 ← 서블릿 ← 인터셉터 ← 컨트롤러(response.sendError())
```

### 오류 페이지 작동 원리 — 내부 재호출

```
1. WAS까지 예외/sendError 전파
2. WAS → 오류 페이지 경로 확인 (/error-page/500)
3. WAS → /error-page/500 내부 재요청
   → 필터 → 서블릿 → 인터셉터 → 컨트롤러(/error-page/500) → View
```

> 클라이언트(브라우저)는 이 내부 재호출 과정을 전혀 모름. 최종 응답만 받음.

### request.attribute 오류 정보

WAS가 오류 페이지 재요청 시 `request`에 오류 정보를 담아줌.

```java
// RequestDispatcher 상수
"javax.servlet.error.exception"      // 예외 객체
"javax.servlet.error.exception_type" // 예외 타입
"javax.servlet.error.message"        // 오류 메시지
"javax.servlet.error.request_uri"    // 원래 요청 URI
"javax.servlet.error.status_code"    // HTTP 상태 코드
```

---

## 서블릿 예외 처리 — 직접 구현

```java
@Component
public class WebServerCustomizer implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        ErrorPage errorPage404 = new ErrorPage(HttpStatus.NOT_FOUND, "/error-page/404");
        ErrorPage errorPage500 = new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/error-page/500");
        ErrorPage errorPageEx = new ErrorPage(RuntimeException.class, "/error-page/500");
        factory.addErrorPages(errorPage404, errorPage500, errorPageEx);
    }
}

@Controller
public class ErrorPageController {
    @RequestMapping("/error-page/404")
    public String errorPage404(HttpServletRequest request) { return "error-page/404"; }

    @RequestMapping("/error-page/500")
    public String errorPage500(HttpServletRequest request) { return "error-page/500"; }
}
```

---

## DispatcherType — 필터/인터셉터 중복 호출 제거

```java
public enum DispatcherType {
    REQUEST,  // 클라이언트 요청
    ERROR,    // 오류 페이지 내부 재요청
    FORWARD,  // RequestDispatcher.forward()
    INCLUDE,  // RequestDispatcher.include()
    ASYNC     // 비동기 호출
}
```

### 필터 — setDispatcherTypes

```java
@Bean
public FilterRegistrationBean logFilter() {
    FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
    bean.setFilter(new LogFilter());
    bean.setOrder(1);
    bean.addUrlPatterns("/*");
    // REQUEST만 적용 (기본값) → 오류 페이지 내부 재요청 시 필터 미적용
    bean.setDispatcherTypes(DispatcherType.REQUEST);
    return bean;
}
```

> 기본값: `DispatcherType.REQUEST` → 클라이언트 요청에만 필터 적용. ERROR 오류 페이지 요청 시 필터 스킵.

### 인터셉터 — excludePathPatterns

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new LogInterceptor())
            .order(1)
            .addPathPatterns("/**")
            .excludePathPatterns("/css/**", "/*.ico", "/error", "/error-page/**");
            //                                                   ↑ 오류 페이지 경로 제외
}
```

> 인터셉터는 스프링이 제공하므로 `DispatcherType`과 무관하게 항상 호출됨 → `excludePathPatterns`로 직접 제거.

### 전체 흐름 정리

```
정상 요청 /hello:
  WAS(REQUEST) → 필터 → 서블릿 → 인터셉터 → 컨트롤러 → View

오류 요청 /error-ex:
  1. WAS(REQUEST) → 필터 → 서블릿 → 인터셉터 → 컨트롤러 (예외 발생)
  2. WAS까지 예외 전파
  3. WAS(ERROR) → 필터(x, REQUEST만 설정) → 서블릿 → 인터셉터(x, exclude) → ErrorPageController → View
```

---

## 스프링 부트 자동화

스프링 부트는 `WebServerCustomizer`, `ErrorPage`, `ErrorPageController`를 **자동으로** 설정한다.

### BasicErrorController 자동 등록

- `/error` 경로로 기본 오류 페이지 자동 등록 (`ErrorMvcAutoConfiguration`)
- `BasicErrorController`가 `/error`를 처리
- **개발자는 오류 뷰 파일만 만들면 된다**

### 뷰 선택 우선순위

```
1. 뷰 템플릿 (resources/templates/error/)
   500.html > 5xx.html

2. 정적 리소스 (resources/static/error/)
   404.html > 4xx.html

3. resources/templates/error.html (기본)
```

```
resources/
  templates/
    error/
      500.html   → 500 오류 (구체적)
      5xx.html   → 500대 오류 (범용)
      404.html   → 404 오류 (구체적)
      4xx.html   → 400대 오류 (범용)
```

### BasicErrorController가 뷰에 전달하는 정보

```html
<!-- resources/templates/error/500.html -->
<li th:text="|timestamp: ${timestamp}|"></li>
<li th:text="|path: ${path}|"></li>
<li th:text="|status: ${status}|"></li>
<li th:text="|message: ${message}|"></li>
<li th:text="|error: ${error}|"></li>
<li th:text="|exception: ${exception}|"></li>
<li th:text="|trace: ${trace}|"></li>
```

### 오류 정보 노출 설정 (application.properties)

```properties
# 기본값: false/never
server.error.include-exception=false        # exception 포함 여부
server.error.include-message=never          # message 포함 여부
server.error.include-stacktrace=never       # trace 포함 여부
server.error.include-binding-errors=never   # errors 포함 여부

# 옵션: never | always | on_param
# on_param: ?message=&trace=&errors= 파라미터가 있을 때만 노출 (개발용)
```

> **실무**: 오류 정보를 클라이언트에 노출 금지. 사용자에게는 간단한 오류 메시지만, 서버에는 로그로 기록.

---

## 면접 Q&A

**Q: 스프링 부트에서 오류 페이지가 동작하는 원리는?**
A: 예외가 WAS까지 전파되거나 `response.sendError()`가 호출되면 WAS는 등록된 오류 페이지 경로(`/error`)로 내부에서 다시 요청을 보낸다. 이 내부 재요청은 클라이언트가 모르고, `DispatcherType`이 `ERROR`로 설정된다. 스프링 부트는 `BasicErrorController`를 자동 등록해 `/error` 경로를 처리하며, 개발자는 `resources/templates/error/` 폴더에 오류 뷰 파일만 만들면 된다.

**Q: 오류 페이지 재요청 시 필터/인터셉터가 중복 호출되는 문제 해결 방법은?**
A: 필터는 `FilterRegistrationBean.setDispatcherTypes(DispatcherType.REQUEST)`를 설정하면 클라이언트 요청에만 적용되고, 오류 페이지 내부 재요청(`DispatcherType.ERROR`)에는 적용되지 않는다. 인터셉터는 `DispatcherType`을 인식하지 못하므로, `excludePathPatterns("/error-page/**")`로 오류 페이지 경로를 제외해야 한다.
