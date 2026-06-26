# MVC 프레임워크 만들기

> 스프링 MVC 1편 | MVC1

---

## 전체 흐름 한눈에 보기

```
v1: FrontController 도입
  → urlPatterns = "/v1/*" 로 모든 요청을 하나의 서블릿이 받음
  → controllerMap(URL → Controller) 에서 조회 후 process() 호출

v2: View 분리 (MyView)
  → 각 컨트롤러가 MyView를 반환 → FrontController가 view.render() 호출
  → 포워드 중복 제거

v3: Model 추가 (ModelView + viewResolver)
  → 컨트롤러가 HttpServletRequest 사용 안 함 → Map<String,String> paramMap 수신
  → ModelView(논리 뷰 이름 + model Map) 반환
  → viewResolver: 논리 이름 → 물리 경로 변환

v4: 단순하고 실용적인 컨트롤러
  → model이 파라미터로 전달됨 → 컨트롤러는 String(뷰 이름)만 반환

v5: 유연한 컨트롤러 (어댑터 패턴)
  → MyHandlerAdapter 인터페이스: supports(handler) + handle(...)
  → 다양한 컨트롤러 인터페이스(V3, V4)를 하나의 FrontController에서 처리
  → 스프링 MVC의 DispatcherServlet + HandlerAdapter 구조와 동일
```

---

## 프론트 컨트롤러 패턴 소개

| 구분 | 도입 전 | 도입 후 |
|------|---------|---------|
| 공통 처리 | 각 컨트롤러마다 중복 | FrontController 한 곳 |
| 서블릿 수 | 요청 수만큼 | FrontController 1개 |
| 나머지 컨트롤러 | 서블릿 필요 | 서블릿 불필요 |

> 스프링 MVC의 `DispatcherServlet`이 바로 FrontController 패턴으로 구현되어 있음

---

## v1 — FrontController 도입

```java
// ControllerV1 인터페이스
public interface ControllerV1 {
    void process(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException;
}
```

```java
// FrontControllerServletV1
@WebServlet(name = "frontControllerServletV1", urlPatterns = "/front-controller/v1/*")
public class FrontControllerServletV1 extends HttpServlet {

    private Map<String, ControllerV1> controllerMap = new HashMap<>();

    public FrontControllerServletV1() {
        controllerMap.put("/front-controller/v1/members/new-form", new MemberFormControllerV1());
        controllerMap.put("/front-controller/v1/members/save",     new MemberSaveControllerV1());
        controllerMap.put("/front-controller/v1/members",          new MemberListControllerV1());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        ControllerV1 controller = controllerMap.get(requestURI);
        if (controller == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        controller.process(request, response);
    }
}
```

- `urlPatterns = "/v1/*"` → 하위 모든 URL을 이 서블릿 하나가 받음
- 각 컨트롤러 내부 로직은 기존 서블릿과 동일 (forward 직접 호출)

---

## v2 — View 분리 (MyView)

**문제**: 모든 컨트롤러에서 `dispatcher.forward()` 3줄이 반복됨

**해결**: 컨트롤러가 `MyView` 객체를 반환 → FrontController가 `view.render()` 호출

```java
// MyView
public class MyView {
    private String viewPath;

    public MyView(String viewPath) { this.viewPath = viewPath; }

    public void render(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        RequestDispatcher dispatcher = request.getRequestDispatcher(viewPath);
        dispatcher.forward(request, response);
    }
}
```

```java
// ControllerV2 — MyView 반환
public interface ControllerV2 {
    MyView process(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException;
}

// 컨트롤러 구현체 — 포워드 코드 없이 한 줄
public MyView process(...) {
    return new MyView("/WEB-INF/views/new-form.jsp");
}
```

```java
// FrontController — render 한 곳에서 처리
MyView view = controller.process(request, response);
view.render(request, response);
```

---

## v3 — Model 추가 + 서블릿 종속성 제거

**문제 2가지**
1. 컨트롤러가 `HttpServletRequest`에 종속 → 테스트 어려움
2. 뷰 경로에 prefix(`/WEB-INF/views/`), suffix(`.jsp`) 중복

```java
// ModelView — 뷰 이름 + 모델 데이터를 함께 반환
public class ModelView {
    private String viewName;
    private Map<String, Object> model = new HashMap<>();
    // getter/setter
}
```

```java
// ControllerV3 — 서블릿 없음
public interface ControllerV3 {
    ModelView process(Map<String, String> paramMap);
}

// 컨트롤러 구현체
public ModelView process(Map<String, String> paramMap) {
    String username = paramMap.get("username");
    int age = Integer.parseInt(paramMap.get("age"));
    Member member = new Member(username, age);
    memberRepository.save(member);

    ModelView mv = new ModelView("save-result");  // 논리 뷰 이름
    mv.getModel().put("member", member);
    return mv;
}
```

```java
// FrontControllerV3 — createParamMap + viewResolver
Map<String, String> paramMap = createParamMap(request);   // request → Map
ModelView mv = controller.process(paramMap);

String viewName = mv.getViewName();
MyView view = viewResolver(viewName);   // "save-result" → "/WEB-INF/views/save-result.jsp"
view.render(mv.getModel(), request, response);  // model을 request.setAttribute로 옮김
```

```java
// viewResolver
private MyView viewResolver(String viewName) {
    return new MyView("/WEB-INF/views/" + viewName + ".jsp");
}
```

> JSP는 `request.getAttribute()`로 데이터를 조회하기 때문에 `model.forEach → request.setAttribute`로 옮기는 과정이 필요

---

## v4 — 단순하고 실용적인 컨트롤러

**문제**: 컨트롤러마다 `ModelView` 생성이 번거로움

**해결**: model을 파라미터로 넘겨주고, 컨트롤러는 **String(뷰 이름)만** 반환

```java
// ControllerV4
public interface ControllerV4 {
    String process(Map<String, String> paramMap, Map<String, Object> model);
}

// 컨트롤러 구현체 — 매우 단순
public String process(Map<String, String> paramMap, Map<String, Object> model) {
    model.put("member", member);
    return "save-result";  // 뷰 이름만 반환
}
```

```java
// FrontControllerV4
Map<String, Object> model = new HashMap<>();         // 프론트 컨트롤러가 생성
String viewName = controller.process(paramMap, model);
MyView view = viewResolver(viewName);
view.render(model, request, response);
```

> 아키텍처(v3)는 유지하면서 개발자 편의성만 향상 — "프레임워크가 수고로워야 개발자가 편해진다"

---

## v5 — 유연한 컨트롤러 (어댑터 패턴)

**문제**: ControllerV3와 ControllerV4는 인터페이스가 달라 하나의 FrontController에서 혼용 불가

**해결**: `MyHandlerAdapter` 인터페이스로 다양한 컨트롤러를 통일된 방식으로 호출

```java
// MyHandlerAdapter 인터페이스
public interface MyHandlerAdapter {
    boolean supports(Object handler);                    // 이 컨트롤러를 처리할 수 있는가?
    ModelView handle(HttpServletRequest request,
                     HttpServletResponse response,
                     Object handler)
            throws ServletException, IOException;       // 항상 ModelView 반환
}
```

```java
// ControllerV3HandlerAdapter
public boolean supports(Object handler) {
    return (handler instanceof ControllerV3);
}

public ModelView handle(..., Object handler) {
    ControllerV3 controller = (ControllerV3) handler;
    Map<String, String> paramMap = createParamMap(request);
    return controller.process(paramMap);  // 이미 ModelView 반환하므로 그대로
}
```

```java
// ControllerV4HandlerAdapter — 핵심: String → ModelView 변환
public ModelView handle(..., Object handler) {
    ControllerV4 controller = (ControllerV4) handler;
    Map<String, String> paramMap = createParamMap(request);
    Map<String, Object> model = new HashMap<>();

    String viewName = controller.process(paramMap, model);

    ModelView mv = new ModelView(viewName);  // String을 ModelView로 포장
    mv.setModel(model);
    return mv;
}
```

```java
// FrontControllerServletV5
private final Map<String, Object> handlerMappingMap = new HashMap<>();   // Object — 어떤 타입도 OK
private final List<MyHandlerAdapter> handlerAdapters = new ArrayList<>();

@Override
protected void service(...) {
    Object handler = getHandler(request);           // 1. 핸들러 조회
    MyHandlerAdapter adapter = getHandlerAdapter(handler);  // 2. 어댑터 조회
    ModelView mv = adapter.handle(request, response, handler);  // 3. 어댑터 호출
    MyView view = viewResolver(mv.getViewName());   // 4. 뷰 리졸버
    view.render(mv.getModel(), request, response);  // 5. 렌더링
}

private MyHandlerAdapter getHandlerAdapter(Object handler) {
    for (MyHandlerAdapter adapter : handlerAdapters) {
        if (adapter.supports(handler)) return adapter;
    }
    throw new IllegalArgumentException("handler adapter를 찾을 수 없습니다.");
}
```

---

## 버전별 진화 정리

| 버전 | 핵심 변경 | 해결한 문제 |
|------|---------|------------|
| v1 | FrontController 도입 | 공통 처리 |
| v2 | MyView 분리 | 포워드 중복 제거 |
| v3 | ModelView + viewResolver | 서블릿 종속성 제거, viewPath 중복 제거 |
| v4 | String 반환 + model 파라미터 전달 | ModelView 생성 번거로움 제거 |
| v5 | 어댑터 패턴 | 다양한 컨트롤러 인터페이스 혼용 가능 |

> 스프링 MVC: DispatcherServlet = FrontController, HandlerAdapter = MyHandlerAdapter, Handler = Controller

---

## 면접 Q&A

**Q: 프론트 컨트롤러 패턴이란?**
A: 모든 요청의 진입점을 하나의 서블릿(FrontController)으로 통일하는 패턴이다. FrontController가 URL에 맞는 컨트롤러를 찾아 호출하고, 공통 처리(인증, 로깅, 뷰 렌더링)를 일괄 처리한다. 나머지 컨트롤러는 서블릿에 의존하지 않아도 된다. 스프링 MVC의 `DispatcherServlet`이 이 패턴으로 구현되어 있다.

**Q: 뷰 리졸버(ViewResolver)가 왜 필요한가?**
A: 컨트롤러가 논리 뷰 이름(`"save-result"`)만 반환하도록 하면, 실제 물리 경로(`/WEB-INF/views/save-result.jsp`)는 뷰 리졸버가 변환한다. 덕분에 뷰 기술(JSP → Thymeleaf)을 바꾸거나 경로를 변경할 때 컨트롤러 코드를 건드리지 않고 뷰 리졸버 설정만 수정하면 된다.

**Q: 어댑터 패턴을 MVC에서 어떻게 활용하는가?**
A: `MyHandlerAdapter` 인터페이스의 `supports(handler)`로 어떤 컨트롤러를 처리할 수 있는지 판별하고, `handle()`에서 실제 컨트롤러를 호출한다. 어댑터가 항상 `ModelView`를 반환하기 때문에 FrontController는 컨트롤러 타입과 관계없이 동일한 방식으로 처리할 수 있다. ControllerV4는 `String`을 반환하지만 어댑터가 이를 `ModelView`로 변환해준다. 스프링 MVC에서도 `HandlerAdapter`가 동일한 역할을 한다.
