# 서블릿

> 스프링 MVC 1편 | MVC1

---

## 전체 흐름 한눈에 보기

```
서블릿 컨테이너 동작 방식
  스프링 부트 시작 → 내장 톰캣 서버 생성 → 서블릿 컨테이너 생성 → 서블릿 등록

HTTP 요청 흐름
  웹 브라우저 → HTTP 요청 → WAS → request/response 객체 생성 → 서블릿 실행
  → response 객체 정보로 HTTP 응답 생성 → 웹 브라우저 반환

HTTP 요청 데이터 전송 3가지 방법
  1. GET 쿼리 파라미터     → URL?key=value&key2=value2 (메시지 바디 없음)
  2. POST HTML Form      → Content-Type: application/x-www-form-urlencoded
  3. HTTP API 메시지 바디  → JSON/XML/TEXT (POST/PUT/PATCH)
```

---

## Hello 서블릿

```java
@ServletComponentScan  // 서블릿 자동 등록 (메인 클래스에 추가)
@SpringBootApplication
public class ServletApplication { ... }
```

```java
@WebServlet(name = "helloServlet", urlPatterns = "/hello")
public class HelloServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String username = request.getParameter("username");
        response.setContentType("text/plain");
        response.setCharacterEncoding("utf-8");
        response.getWriter().write("hello " + username);
    }
}
```

- `@WebServlet(name, urlPatterns)` — 서블릿 이름과 URL 매핑 지정
- URL 호출 시 서블릿 컨테이너가 `service()` 메서드 실행

---

## HttpServletRequest 개요

서블릿이 HTTP 요청 메시지를 파싱하여 `HttpServletRequest` 객체에 담아 제공한다.

| 구분 | 조회 가능한 것 |
|------|--------------|
| START LINE | HTTP 메소드, URL, 쿼리 스트링, 스키마/프로토콜 |
| 헤더 | 모든 헤더 정보 |
| 바디 | form 파라미터, message body 데이터 |

**부가 기능**
- 임시 저장소: `request.setAttribute(name, value)` / `request.getAttribute(name)` — 요청 시작~끝 유지
- 세션 관리: `request.getSession(create: true)`

---

## HttpServletRequest 기본 사용법

```java
// START LINE
request.getMethod()       // GET
request.getProtocol()     // HTTP/1.1
request.getScheme()       // http
request.getRequestURL()   // http://localhost:8080/request-header
request.getRequestURI()   // /request-header
request.getQueryString()  // username=hello
request.isSecure()        // false (HTTPS 여부)

// 헤더 전체 조회
request.getHeaderNames().asIterator()
    .forEachRemaining(name -> System.out.println(name + ": " + request.getHeader(name)));

// 편의 메서드
request.getServerName()      // localhost (Host 헤더)
request.getServerPort()      // 8080
request.getLocale()          // ko (Accept-Language 첫 번째)
request.getCookies()         // Cookie[]
request.getContentType()     // Content-Type
request.getContentLength()   // -1 (없으면)
request.getCharacterEncoding() // UTF-8
```

---

## HTTP 요청 데이터 — GET 쿼리 파라미터

```
http://localhost:8080/request-param?username=hello&age=20
```

```java
// 단일 파라미터
String username = request.getParameter("username");

// 전체 파라미터 이름 조회
request.getParameterNames().asIterator()
    .forEachRemaining(name -> System.out.println(name + "=" + request.getParameter(name)));

// 파라미터 Map으로 조회
Map<String, String[]> parameterMap = request.getParameterMap();

// 같은 이름 복수 파라미터 (?username=hello&username=kim)
String[] usernames = request.getParameterValues("username");
```

> `getParameter()`는 같은 이름 복수 값이면 첫 번째를 반환 — 복수일 때는 `getParameterValues()` 사용

---

## HTTP 요청 데이터 — POST HTML Form

```
Content-Type: application/x-www-form-urlencoded
message body: username=hello&age=20
```

- 서버 입장에서 GET 쿼리 파라미터와 **형식이 동일** → `request.getParameter()`로 동일하게 조회 가능
- GET은 content-type 없음 (메시지 바디 미사용), POST Form은 content-type 필수

---

## HTTP 요청 데이터 — API 메시지 바디

**단순 텍스트**
```java
ServletInputStream inputStream = request.getInputStream();
String messageBody = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
```

**JSON**
```java
private ObjectMapper objectMapper = new ObjectMapper();

ServletInputStream inputStream = request.getInputStream();
String messageBody = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);

HelloData helloData = objectMapper.readValue(messageBody, HelloData.class);
// helloData.getUsername(), helloData.getAge()
```

> 스프링 부트 + Spring MVC 선택 시 Jackson(`ObjectMapper`) 기본 제공

---

## HttpServletResponse 기본 사용법

```java
// 상태 코드
response.setStatus(HttpServletResponse.SC_OK);  // 200

// 헤더 직접 설정
response.setHeader("Content-Type", "text/plain;charset=utf-8");
response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

// Content 편의 메서드
response.setContentType("text/plain");
response.setCharacterEncoding("utf-8");

// 쿠키 편의 메서드
Cookie cookie = new Cookie("myCookie", "good");
cookie.setMaxAge(600);
response.addCookie(cookie);

// redirect 편의 메서드
response.sendRedirect("/basic/hello-form.html");  // 302 + Location 헤더 자동

// 바디 출력
PrintWriter writer = response.getWriter();
writer.println("ok");
```

---

## HTTP 응답 데이터 — HTML / JSON

**HTML 응답**
```java
response.setContentType("text/html");
response.setCharacterEncoding("utf-8");
PrintWriter writer = response.getWriter();
writer.println("<html><body><div>안녕?</div></body></html>");
```

**JSON 응답**
```java
response.setHeader("content-type", "application/json");
response.setCharacterEncoding("utf-8");

HelloData data = new HelloData();
data.setUsername("kim");
data.setAge(20);

String result = objectMapper.writeValueAsString(data);  // {"username":"kim","age":20}
response.getWriter().write(result);
```

> `application/json`은 스펙상 UTF-8 고정 — `charset=utf-8` 추가 파라미터 불필요.
> `getWriter()` 대신 `getOutputStream()` 사용 시 파라미터 자동 추가 문제 없음.

---

## 면접 Q&A

**Q: 서블릿이란 무엇인가?**
A: 서블릿은 HTTP 요청/응답을 편리하게 다룰 수 있도록 자바가 제공하는 웹 기술이다. WAS(톰캣)가 HTTP 요청 메시지를 파싱하여 `HttpServletRequest` 객체를 생성하고, 개발자는 `service()` 메서드에서 요청을 처리한 뒤 `HttpServletResponse`에 응답을 담으면 WAS가 HTTP 응답 메시지를 생성한다.

**Q: GET 쿼리 파라미터와 POST HTML Form의 차이점은?**
A: 클라이언트 입장에서는 전송 방식이 다르지만, 서버 입장에서 데이터 형식이 동일하다(key=value&key2=value2). 따라서 `request.getParameter()`로 구분 없이 조회할 수 있다. 차이점은 GET은 URL에 데이터가 노출되고 메시지 바디를 사용하지 않으며 content-type이 없고, POST Form은 메시지 바디에 담아 전송하므로 `Content-Type: application/x-www-form-urlencoded`를 명시해야 한다.

**Q: JSON 응답 시 content-type을 `application/json;charset=utf-8`로 설정하면 안 되는 이유는?**
A: `application/json`은 스펙상 UTF-8을 사용하도록 정의되어 있어 charset 파라미터를 지원하지 않는다. `response.getWriter()`를 사용하면 자동으로 `charset=utf-8`이 붙어버리는데, 이 경우 `getOutputStream()`으로 대체하면 해결된다.
