# 스프링 MVC - 구조 이해

> 스프링 MVC 1편 | MVC1

---

## 전체 흐름 한눈에 보기

```
직접 만든 프레임워크 → 스프링 MVC 대응관계
  FrontController     → DispatcherServlet
  handlerMappingMap   → HandlerMapping
  MyHandlerAdapter    → HandlerAdapter
  ModelView           → ModelAndView
  viewResolver        → ViewResolver
  MyView              → View

DispatcherServlet.doDispatch() 핵심 흐름
  1. getHandler(request)          → HandlerMapping으로 핸들러 조회
  2. getHandlerAdapter(handler)   → HandlerAdapter 조회
  3. ha.handle(...)               → 어댑터가 핸들러 실행 → ModelAndView 반환
  4. resolveViewName(viewName)    → ViewResolver로 View 객체 획득
  5. view.render(model, ...)      → 뷰 렌더링

실무 컨트롤러 방식 (v3 = 실용적인 방식)
  @Controller + @RequestMapping + Model 파라미터 + String 반환 + @RequestParam
  → @GetMapping / @PostMapping 으로 HTTP 메서드 구분
```

---

## 스프링 MVC 전체 구조

### 직접 만든 프레임워크 → 스프링 MVC 대응

| 직접 만든 것 | 스프링 MVC |
|------------|-----------|
| FrontController | **DispatcherServlet** |
| handlerMappingMap | **HandlerMapping** |
| MyHandlerAdapter | **HandlerAdapter** |
| ModelView | **ModelAndView** |
| viewResolver 메서드 | **ViewResolver** |
| MyView | **View** |

### DispatcherServlet 등록

- `HttpServlet` 상속 구조: `DispatcherServlet → FrameworkServlet → HttpServletBean → HttpServlet`
- 스프링 부트가 `DispatcherServlet`을 서블릿으로 **자동 등록** (urlPatterns=`"/"`)
- 더 구체적인 경로가 우선순위가 높으므로 기존 서블릿도 함께 동작

### doDispatch() 핵심 코드

```java
protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
    // 1. 핸들러 조회
    HandlerExecutionChain mappedHandler = getHandler(processedRequest);
    if (mappedHandler == null) { noHandlerFound(...); return; }

    // 2. 핸들러 어댑터 조회
    HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

    // 3. 핸들러 어댑터 실행 → 4. 핸들러 실행 → 5. ModelAndView 반환
    ModelAndView mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

    // 6. viewResolver 호출 → 7. View 반환 → 8. 렌더링
    processDispatchResult(...);
}

protected void render(ModelAndView mv, ...) {
    String viewName = mv.getViewName();
    View view = resolveViewName(viewName, mv.getModelInternal(), locale, request);  // 6~7
    view.render(mv.getModelInternal(), request, response);                           // 8
}
```

---

## HandlerMapping과 HandlerAdapter

### 스프링 부트 자동 등록 목록

**HandlerMapping** (우선순위 순)

| 순서 | 구현체 | 역할 |
|------|--------|------|
| 0 | `RequestMappingHandlerMapping` | `@RequestMapping` 애노테이션 기반 |
| 1 | `BeanNameUrlHandlerMapping` | 스프링 빈 이름으로 핸들러 찾기 |

**HandlerAdapter** (우선순위 순)

| 순서 | 구현체 | 역할 |
|------|--------|------|
| 0 | `RequestMappingHandlerAdapter` | `@RequestMapping` 애노테이션 기반 |
| 1 | `HttpRequestHandlerAdapter` | `HttpRequestHandler` 인터페이스 |
| 2 | `SimpleControllerHandlerAdapter` | `Controller` 인터페이스 (구식) |

### OldController 호출 흐름 예시

```java
@Component("/springmvc/old-controller")   // 빈 이름 = URL
public class OldController implements Controller {
    public ModelAndView handleRequest(...) { return new ModelAndView("new-form"); }
}
```

1. `BeanNameUrlHandlerMapping` → 빈 이름으로 `OldController` 반환
2. `SimpleControllerHandlerAdapter` → `Controller` 인터페이스 지원 → `handleRequest()` 호출

> 실무에서는 99.9% `RequestMappingHandlerMapping` + `RequestMappingHandlerAdapter` 사용

---

## ViewResolver

### 스프링 부트 자동 등록 뷰 리졸버

| 순서 | 구현체 | 역할 |
|------|--------|------|
| 1 | `BeanNameViewResolver` | 빈 이름으로 뷰 찾기 (엑셀 파일 등 특수 뷰) |
| 2 | `InternalResourceViewResolver` | JSP 처리 |

### InternalResourceViewResolver 설정

```properties
# application.properties
spring.mvc.view.prefix=/WEB-INF/views/
spring.mvc.view.suffix=.jsp
```

→ 스프링 부트가 이 설정을 읽어 `InternalResourceViewResolver` 자동 등록

### 뷰 리졸버 동작 흐름 (논리 뷰 이름 `"new-form"` 처리 시)

```
1. 핸들러 어댑터 → 논리 뷰 이름 "new-form" 획득
2. BeanNameViewResolver → "new-form" 이름의 빈 없음 → 다음으로
3. InternalResourceViewResolver → InternalResourceView 반환
4. view.render() → forward() 호출 → JSP 실행
```

> JSP만 `forward()`가 필요하고, Thymeleaf 등 다른 템플릿은 바로 렌더링됨

---

## 스프링 MVC 시작하기 — @RequestMapping

### @Controller 핵심 역할

```java
@Controller  // ① 스프링 빈 자동 등록 (@Component 포함), ② 애노테이션 기반 컨트롤러로 인식
public class SpringMemberFormControllerV1 {
    @RequestMapping("/springmvc/v1/members/new-form")
    public ModelAndView process() {
        return new ModelAndView("new-form");  // 논리 뷰 이름
    }
}
```

- `ModelAndView.addObject("key", value)` → 모델 데이터 추가
- `RequestMappingHandlerMapping`은 클래스에 `@Controller` 또는 `@RequestMapping`이 있으면 매핑 정보로 인식
- **스프링 부트 3.0(스프링 6.0) 이상**: 클래스 레벨의 `@RequestMapping`만으로는 인식 안 됨 → `@Controller` 필수

### 컨트롤러 통합 — 클래스 레벨 @RequestMapping

```java
@Controller
@RequestMapping("/springmvc/v2/members")  // 클래스 레벨 공통 경로
public class SpringMemberControllerV2 {
    @RequestMapping("/new-form")  // → /springmvc/v2/members/new-form
    public ModelAndView newForm() { return new ModelAndView("new-form"); }

    @RequestMapping("/save")      // → /springmvc/v2/members/save
    public ModelAndView save(...) { ... }

    @RequestMapping             // → /springmvc/v2/members
    public ModelAndView members() { ... }
}
```

---

## 실용적인 방식 (실무 표준)

```java
@Controller
@RequestMapping("/springmvc/v3/members")
public class SpringMemberControllerV3 {

    @GetMapping("/new-form")
    public String newForm() {
        return "new-form";  // String으로 뷰 이름 직접 반환
    }

    @PostMapping("/save")
    public String save(
            @RequestParam("username") String username,  // request.getParameter() 대체
            @RequestParam("age") int age,
            Model model) {                              // Model을 파라미터로 수신
        Member member = new Member(username, age);
        memberRepository.save(member);
        model.addAttribute("member", member);
        return "save-result";
    }

    @GetMapping
    public String members(Model model) {
        model.addAttribute("members", memberRepository.findAll());
        return "members";
    }
}
```

### 핵심 변경 포인트 요약

| 항목 | 기존 방식 | 실용적 방식 |
|------|---------|-----------|
| 반환 타입 | `ModelAndView` | `String` (뷰 이름) |
| 모델 사용 | `mv.addObject()` | `Model` 파라미터 + `addAttribute()` |
| 파라미터 수신 | `request.getParameter()` | `@RequestParam` |
| HTTP 메서드 구분 | `@RequestMapping(method=GET)` | `@GetMapping` / `@PostMapping` |

---

## 면접 Q&A

**Q: DispatcherServlet의 역할과 동작 흐름은?**
A: `DispatcherServlet`은 스프링 MVC의 프론트 컨트롤러다. `doDispatch()` 메서드에서 ① `HandlerMapping`으로 핸들러 조회 → ② `HandlerAdapter` 조회 → ③ 어댑터를 통해 핸들러 실행 → ④ `ModelAndView` 반환 → ⑤ `ViewResolver`로 뷰 객체 획득 → ⑥ `view.render()`로 HTML 생성의 순서로 동작한다.

**Q: @Controller와 @Component의 차이는?**
A: `@Controller`는 내부에 `@Component`를 포함하므로 컴포넌트 스캔으로 빈 등록된다. 추가로 `RequestMappingHandlerMapping`이 클래스 레벨에서 `@Controller`를 감지해 해당 클래스를 애노테이션 기반 컨트롤러로 인식한다. 스프링 부트 3.0 이상에서는 `@Controller` 없이 `@RequestMapping`만 있으면 컨트롤러로 인식하지 않는다.

**Q: InternalResourceViewResolver가 동작하는 방식은?**
A: 컨트롤러가 반환한 논리 뷰 이름(`"save-result"`)을 `application.properties`의 `spring.mvc.view.prefix`와 `spring.mvc.view.suffix` 설정과 결합해 물리 경로(`/WEB-INF/views/save-result.jsp`)를 만들고, `InternalResourceView`를 반환한다. 최종적으로 `view.render()`에서 `forward()`를 호출해 JSP를 실행한다. JSP 외의 Thymeleaf 등은 `forward()` 없이 바로 렌더링된다.
