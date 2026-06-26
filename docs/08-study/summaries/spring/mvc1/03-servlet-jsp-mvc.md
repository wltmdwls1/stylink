# 서블릿, JSP, MVC 패턴

> 스프링 MVC 1편 | MVC1

---

## 전체 흐름 한눈에 보기

```
서블릿만 사용
  → 자바 코드로 HTML 직접 생성 → 비효율적, 복잡

JSP 도입
  → HTML 중심 + 자바 코드 삽입 (<% %>, <%= %>)
  → 여전히 비즈니스 로직과 뷰 로직이 하나의 파일에 공존 → 유지보수 어려움

MVC 패턴 적용
  서블릿(Controller) → 비즈니스 로직 처리 + 모델에 데이터 담기
  request(Model)    → setAttribute / getAttribute
  JSP(View)         → 모델 데이터를 꺼내서 HTML 렌더링

MVC 패턴 한계
  → 포워드 중복, viewPath 중복, 공통 처리 불가 → FrontController 필요
```

---

## 서블릿으로 회원 관리 (문제점 확인)

```java
// HTML을 자바 코드로 직접 생성
PrintWriter w = response.getWriter();
w.write("<!DOCTYPE html>\n<html>\n...");
w.write("<li>id=" + member.getId() + "</li>\n");
```

- 동적 HTML은 만들 수 있지만, 자바 코드 안에 HTML이 섞여 매우 복잡
- 템플릿 엔진(JSP, Thymeleaf 등)이 등장한 이유

---

## JSP로 회원 관리

**JSP 문법 핵심**

| 문법 | 역할 |
|------|------|
| `<%@ page contentType="text/html;charset=UTF-8" language="java" %>` | JSP 파일 선언 (첫 줄 필수) |
| `<%@ page import="hello.servlet.domain.member.Member" %>` | 자바 import |
| `<% 자바 코드 %>` | 자바 코드 실행 |
| `<%= 자바 표현식 %>` | 값 출력 |

```jsp
<%
    MemberRepository memberRepository = MemberRepository.getInstance();
    String username = request.getParameter("username");
    int age = Integer.parseInt(request.getParameter("age"));
    Member member = new Member(username, age);
    memberRepository.save(member);
%>
<html>
<body>
성공
<ul>
    <li>id=<%=member.getId()%></li>
    <li>username=<%=member.getUsername()%></li>
</ul>
</body>
</html>
```

**JSP 한계**: 비즈니스 로직(상위 절반) + 뷰 로직(하위 절반)이 하나의 파일에 공존 → 유지보수 지옥

---

## MVC 패턴 개요

**MVC 도입 이유**

| 이유 | 설명 |
|------|------|
| 너무 많은 역할 | 서블릿/JSP 하나에 비즈니스 로직 + 뷰 렌더링 모두 담당 |
| 변경의 라이프사이클 | UI 변경과 비즈니스 로직 변경은 독립적으로 발생 — 같이 두면 유지보수 어려움 |
| 기능 특화 | JSP는 화면 렌더링에 최적화 — 그 역할에만 집중시켜야 함 |

**역할 정의**

```
Controller → HTTP 요청 파라미터 검증, 비즈니스 로직 실행, 모델에 데이터 담기
Model      → 뷰에 출력할 데이터 보관 (HttpServletRequest 객체 활용)
View       → 모델 데이터를 꺼내 HTML 렌더링에만 집중
```

> 컨트롤러에 비즈니스 로직을 직접 두기도 하지만, 실무에서는 Service 계층을 별도로 분리

---

## MVC 패턴 적용

### 핵심 코드 패턴

**컨트롤러 (서블릿)**
```java
// 1. 파라미터 조회 + 비즈니스 로직
String username = request.getParameter("username");
int age = Integer.parseInt(request.getParameter("age"));
Member member = new Member(username, age);
memberRepository.save(member);

// 2. 모델에 데이터 담기
request.setAttribute("member", member);

// 3. 뷰로 포워드
String viewPath = "/WEB-INF/views/save-result.jsp";
RequestDispatcher dispatcher = request.getRequestDispatcher(viewPath);
dispatcher.forward(request, response);
```

**뷰 (JSP) — EL 문법으로 모델 조회**
```jsp
<li>id=${member.id}</li>
<li>username=${member.username}</li>
<li>age=${member.age}</li>
```
> `${member.id}` = `request.getAttribute("member").getId()` 를 편리하게 표현

**JSTL로 리스트 반복**
```jsp
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<c:forEach var="item" items="${members}">
    <tr>
        <td>${item.id}</td>
        <td>${item.username}</td>
        <td>${item.age}</td>
    </tr>
</c:forEach>
```

### redirect vs forward

| 구분 | 동작 | URL 변경 | 클라이언트 인지 |
|------|------|---------|--------------|
| redirect | 클라이언트에 응답 → 클라이언트가 새 URL로 재요청 | O | O |
| forward | 서버 내부에서 다른 서블릿/JSP 호출 | X | X (모름) |

### /WEB-INF 경로

JSP를 `/WEB-INF/views/` 하위에 두면 외부에서 직접 URL로 접근 불가 — 반드시 컨트롤러를 통해서만 접근 가능.

---

## MVC 패턴 한계

| 문제 | 내용 |
|------|------|
| 포워드 중복 | 모든 컨트롤러에서 `dispatcher.forward(request, response)` 반복 |
| viewPath 중복 | prefix(`/WEB-INF/views/`), suffix(`.jsp`) 반복 — 뷰 기술 변경 시 전체 수정 |
| 사용하지 않는 코드 | `HttpServletResponse`를 안 써도 시그니처에 항상 포함 |
| 공통 처리 불가 | 공통 로직을 메서드로 뽑아도 매번 직접 호출해야 하고, 실수로 누락 가능 |

**해결책**: 컨트롤러 호출 전에 공통 기능을 처리하는 **프론트 컨트롤러(Front Controller) 패턴** 도입 → 스프링 MVC의 핵심

---

## 면접 Q&A

**Q: MVC 패턴이 왜 필요한가?**
A: 비즈니스 로직과 뷰 로직의 변경 라이프사이클이 다르기 때문이다. 서블릿이나 JSP 하나에 두 로직을 모두 담으면, UI를 수정할 때도 비즈니스 로직이 있는 파일을 건드려야 해서 유지보수가 어려워진다. MVC 패턴으로 Controller(요청 처리 + 비즈니스 로직 호출), Model(데이터 전달), View(화면 렌더링)로 역할을 분리하면 각 영역이 자신의 책임에만 집중할 수 있다.

**Q: redirect와 forward의 차이는?**
A: redirect는 서버가 클라이언트에게 응답을 보내고, 클라이언트가 새 URL로 재요청하기 때문에 URL이 변경되고 클라이언트가 인지한다. forward는 서버 내부에서 다른 서블릿이나 JSP로 제어를 넘기므로 클라이언트는 전혀 인지하지 못하고 URL도 변경되지 않는다. MVC 패턴에서 컨트롤러가 뷰로 이동할 때는 forward를 사용한다.

**Q: 서블릿 MVC 패턴의 한계는?**
A: 모든 컨트롤러에서 포워드 코드와 viewPath의 prefix/suffix가 중복되고, 공통 기능(인증, 로깅 등)을 적용하려면 각 컨트롤러에서 매번 직접 호출해야 해서 누락 위험이 있다. 이를 해결하기 위해 모든 요청의 진입점을 하나로 만드는 **프론트 컨트롤러 패턴**이 필요하며, 스프링 MVC의 `DispatcherServlet`이 바로 이 역할을 한다.
