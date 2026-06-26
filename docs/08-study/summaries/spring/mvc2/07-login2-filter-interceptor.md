# 로그인 처리2 - 필터, 인터셉터

> 스프링 MVC 2편 | MVC2

---

## 전체 흐름 한눈에 보기

```
공통 관심사 (cross-cutting concern)
  로그인 체크, 로그 기록 등 여러 컨트롤러에서 공통으로 필요한 로직
  각 컨트롤러에 중복 작성 → 필터/인터셉터로 한 곳에서 처리

서블릿 필터
  HTTP 요청 → WAS → 필터 → 서블릿 → 컨트롤러
  Filter 인터페이스: init() / doFilter() / destroy()
  FilterRegistrationBean으로 등록 (order, urlPatterns)

스프링 인터셉터 (권장)
  HTTP 요청 → WAS → 필터 → 서블릿 → 인터셉터 → 컨트롤러
  HandlerInterceptor: preHandle / postHandle / afterCompletion
  WebMvcConfigurer.addInterceptors()로 등록 (addPathPatterns / excludePathPatterns)
  필터보다 세밀하고 편리한 URL 설정, 예외 시에도 afterCompletion 보장

ArgumentResolver 활용
  @Login 커스텀 애노테이션 → LoginMemberArgumentResolver
  supportsParameter() / resolveArgument() 구현
  컨트롤러에서 @Login Member loginMember로 간편하게 세션 회원 주입
```

---

## 서블릿 필터

### Filter 인터페이스

```java
public interface Filter {
    default void init(FilterConfig filterConfig) throws ServletException {}
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException;
    default void destroy() {}
}
```

| 메서드 | 시점 |
|--------|------|
| `init()` | 서블릿 컨테이너 생성 시 |
| `doFilter()` | 매 요청 시 |
| `destroy()` | 서블릿 컨테이너 종료 시 |

> `chain.doFilter(request, response)` 를 호출하지 않으면 다음 필터/서블릿으로 진행되지 않음.

### LogFilter (요청 로그)

```java
@Slf4j
public class LogFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();
        String uuid = UUID.randomUUID().toString();

        try {
            log.info("REQUEST  [{}][{}]", uuid, requestURI);
            chain.doFilter(request, response);  // 다음 필터 또는 서블릿 호출
        } catch (Exception e) {
            throw e;
        } finally {
            log.info("RESPONSE [{}][{}]", uuid, requestURI);
        }
    }
}
```

### LoginCheckFilter (인증 체크)

```java
@Slf4j
public class LoginCheckFilter implements Filter {
    private static final String[] whitelist = {"/", "/members/add", "/login", "/logout", "/css/*"};

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            if (isLoginCheckPath(requestURI)) {
                HttpSession session = httpRequest.getSession(false);
                if (session == null || session.getAttribute(SessionConst.LOGIN_MEMBER) == null) {
                    // 미인증 사용자 → 로그인으로 redirect (원래 URL 파라미터로 전달)
                    httpResponse.sendRedirect("/login?redirectURL=" + requestURI);
                    return;  // 중요: 이후 필터/컨트롤러 호출 차단
                }
            }
            chain.doFilter(request, response);
        } catch (Exception e) {
            throw e;
        } finally {
            log.info("인증 체크 필터 종료 {}", requestURI);
        }
    }

    private boolean isLoginCheckPath(String requestURI) {
        return !PatternMatchUtils.simpleMatch(whitelist, requestURI);
    }
}
```

### FilterRegistrationBean 등록

```java
@Configuration
public class WebConfig {
    @Bean
    public FilterRegistrationBean logFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new LogFilter());
        bean.setOrder(1);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public FilterRegistrationBean loginCheckFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new LoginCheckFilter());
        bean.setOrder(2);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
```

> `@WebFilter(urlPatterns = "/*")`로도 등록 가능하지만 필터 순서를 지정하려면 `FilterRegistrationBean` 사용.

### 로그인 후 redirectURL 처리

```java
// LoginController
@PostMapping("/login")
public String loginV4(@Valid @ModelAttribute LoginForm form, BindingResult bindingResult,
                      @RequestParam(defaultValue = "/") String redirectURL,
                      HttpServletRequest request) {
    // ...검증...
    HttpSession session = request.getSession();
    session.setAttribute(SessionConst.LOGIN_MEMBER, loginMember);
    return "redirect:" + redirectURL;  // 로그인 전 접근 URL로 복귀
}
```

---

## 스프링 인터셉터

### HandlerInterceptor 인터페이스

```java
public interface HandlerInterceptor {
    default boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) throws Exception { return true; }
    default void postHandle(HttpServletRequest request, HttpServletResponse response,
                            Object handler, @Nullable ModelAndView modelAndView) throws Exception {}
    default void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, @Nullable Exception ex) throws Exception {}
}
```

| 메서드 | 시점 | 특이사항 |
|--------|------|---------|
| `preHandle` | 컨트롤러 호출 전 | `false` 반환 시 이후 진행 중단 |
| `postHandle` | 컨트롤러 호출 후 | 예외 발생 시 호출 안 됨 |
| `afterCompletion` | 뷰 렌더링 후 | 예외 발생 시에도 항상 호출, `ex` 파라미터로 예외 접근 |

> **필터 vs 인터셉터**: 필터는 서블릿 레벨, 인터셉터는 스프링 MVC 레벨. 인터셉터가 더 편리하고 기능이 많아 일반적으로 인터셉터 사용 권장.

### LogInterceptor (요청 로그)

```java
@Slf4j
public class LogInterceptor implements HandlerInterceptor {
    public static final String LOG_ID = "logId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        String uuid = UUID.randomUUID().toString();
        request.setAttribute(LOG_ID, uuid);  // postHandle/afterCompletion에서 꺼내 씀

        if (handler instanceof HandlerMethod) {
            HandlerMethod hm = (HandlerMethod) handler; // @RequestMapping 컨트롤러 메서드
        }
        log.info("REQUEST  [{}][{}][{}]", uuid, requestURI, handler);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) throws Exception {
        log.info("postHandle [{}]", modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        String logId = (String) request.getAttribute(LOG_ID);
        log.info("RESPONSE [{}][{}]", logId, request.getRequestURI());
        if (ex != null) {
            log.error("afterCompletion error!!", ex);
        }
    }
}
```

> `preHandle`과 `afterCompletion`은 별도 메서드 호출이라 인스턴스 변수를 쓰면 싱글톤 문제. `request.setAttribute()`에 저장해서 전달.

### LoginCheckInterceptor (인증 체크)

```java
@Slf4j
public class LoginCheckInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionConst.LOGIN_MEMBER) == null) {
            response.sendRedirect("/login?redirectURL=" + requestURI);
            return false;
        }
        return true;
    }
}
```

### WebMvcConfigurer로 등록

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LogInterceptor())
                .order(1)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/*.ico", "/error");

        registry.addInterceptor(new LoginCheckInterceptor())
                .order(2)
                .addPathPatterns("/**")
                .excludePathPatterns("/", "/members/add", "/login", "/logout",
                                     "/css/**", "/*.ico", "/error");
    }
}
```

> 필터와 달리 `excludePathPatterns`로 화이트리스트를 설정에서 처리 가능 → 인터셉터 코드가 단순해짐.

---

## ArgumentResolver 활용 — @Login

### @Login 애노테이션

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Login {}
```

### LoginMemberArgumentResolver

```java
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean hasLoginAnnotation = parameter.hasParameterAnnotation(Login.class);
        boolean hasMemberType = Member.class.isAssignableFrom(parameter.getParameterType());
        return hasLoginAnnotation && hasMemberType;  // @Login + Member 타입
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        HttpSession session = request.getSession(false);
        if (session == null) { return null; }
        return session.getAttribute(SessionConst.LOGIN_MEMBER);
    }
}
```

### 등록 및 사용

```java
// WebConfig
@Override
public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new LoginMemberArgumentResolver());
}

// HomeController
@GetMapping("/")
public String home(@Login Member loginMember, Model model) {
    if (loginMember == null) { return "home"; }
    model.addAttribute("member", loginMember);
    return "loginHome";
}
```

---

## 필터 vs 인터셉터 비교

| 항목 | 서블릿 필터 | 스프링 인터셉터 |
|------|-----------|--------------|
| 레벨 | 서블릿 컨테이너 | 스프링 MVC |
| 인터페이스 | `Filter` (doFilter 하나) | `HandlerInterceptor` (3개 메서드) |
| 호출 시점 구분 | 불가 (하나의 메서드 내에서 처리) | preHandle / postHandle / afterCompletion |
| handler 정보 | 없음 | 있음 (HandlerMethod 등) |
| URL 패턴 | `/*` 수준 | `/**`, PathPattern 수준 (세밀하게 설정) |
| 예외 발생 시 | finally로 처리 | afterCompletion에서 처리 |
| 권장 | 서블릿 레벨 변환 필요 시 | **일반적으로 인터셉터 사용 권장** |

---

## 면접 Q&A

**Q: 서블릿 필터와 스프링 인터셉터의 차이는?**
A: 필터는 서블릿 컨테이너 레벨에서 동작하고 `doFilter()` 하나로 모든 처리를 해야 한다. 인터셉터는 스프링 MVC 레벨에서 동작하며 `preHandle`(컨트롤러 호출 전), `postHandle`(호출 후), `afterCompletion`(뷰 렌더링 후) 세 시점으로 나뉜다. 인터셉터는 어떤 핸들러가 호출되는지, 어떤 ModelAndView가 반환되는지 알 수 있고, `excludePathPatterns`로 URL 제외 설정도 편리하다. 특별한 이유가 없으면 인터셉터 사용을 권장한다.

**Q: afterCompletion이 있는 이유는?**
A: `postHandle`은 컨트롤러에서 예외가 발생하면 호출되지 않는다. 예외 발생 여부와 관계없이 공통 후처리(로그 마무리, 리소스 정리 등)가 필요할 때 `afterCompletion`을 사용한다. `afterCompletion`은 예외 발생 시에도 항상 호출되고 `ex` 파라미터로 어떤 예외가 발생했는지 확인할 수 있다.
