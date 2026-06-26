# 로그인 처리1 - 쿠키, 세션

> 스프링 MVC 2편 | MVC2

---

## 전체 흐름 한눈에 보기

```
쿠키 방식 로그인
  로그인 성공 → response.addCookie(new Cookie("memberId", id))
  요청마다 @CookieValue로 쿠키 조회
  문제: 클라이언트가 쿠키값 조작 가능, 탈취 시 위험

세션 직접 구현
  로그인 성공 → UUID로 sessionId 생성 → 서버 Map에 저장
  클라이언트에는 sessionId만 쿠키로 전달 (중요 정보 서버에만)
  SessionManager (createSession / getSession / expire)

HttpSession (서블릿 표준 세션)
  request.getSession()     → 없으면 생성 (true 옵션)
  request.getSession(false)→ 없으면 null 반환
  session.setAttribute("loginMember", member)
  session.invalidate() → 세션 삭제
  @SessionAttribute → 세션 조회 간편화
  JSESSIONID 쿠키 자동 관리

세션 타임아웃
  기본 30분, 마지막 요청 시간(lastAccessedTime) 기준으로 갱신
  server.servlet.session.timeout=1800 (글로벌 설정)
```

---

## 패키지 구조 — 도메인과 웹 분리

```
hello.login
├── domain
│   ├── item       — Item, ItemRepository
│   ├── member     — Member, MemberRepository
│   └── login      — LoginService
└── web
    ├── item
    ├── member     — MemberController
    └── login      — LoginController
```

> **도메인이 웹을 의존하면 안 된다.** web 패키지를 통째로 교체해도 domain은 그대로여야 한다.

---

## 핵심 도메인

```java
@Data
public class Member {
    private Long id;
    @NotEmpty private String loginId;
    @NotEmpty private String name;
    @NotEmpty private String password;
}
```

```java
@Service
@RequiredArgsConstructor
public class LoginService {
    private final MemberRepository memberRepository;

    public Member login(String loginId, String password) {
        return memberRepository.findByLoginId(loginId)
                .filter(m -> m.getPassword().equals(password))
                .orElse(null);  // null이면 로그인 실패
    }
}
```

---

## 쿠키 방식 로그인

### 로그인 처리

```java
// LoginController
@PostMapping("/login")
public String login(@Valid @ModelAttribute LoginForm form, BindingResult bindingResult,
                    HttpServletResponse response) {
    if (bindingResult.hasErrors()) { return "login/loginForm"; }

    Member loginMember = loginService.login(form.getLoginId(), form.getPassword());
    if (loginMember == null) {
        bindingResult.reject("loginFail", "아이디 또는 비밀번호가 맞지 않습니다.");
        return "login/loginForm";
    }

    // 로그인 성공 → 쿠키 생성
    Cookie idCookie = new Cookie("memberId", String.valueOf(loginMember.getId()));
    response.addCookie(idCookie);
    return "redirect:/";
}
```

### 홈에서 쿠키 조회

```java
@GetMapping("/")
public String homeLogin(
        @CookieValue(name = "memberId", required = false) Long memberId, Model model) {
    if (memberId == null) { return "home"; }
    Member loginMember = memberRepository.findById(memberId);
    if (loginMember == null) { return "home"; }
    model.addAttribute("member", loginMember);
    return "loginHome";
}
```

### 로그아웃 — 쿠키 만료

```java
@PostMapping("/logout")
public String logout(HttpServletResponse response) {
    Cookie cookie = new Cookie("memberId", null);
    cookie.setMaxAge(0);  // 즉시 만료
    response.addCookie(cookie);
    return "redirect:/";
}
```

### 쿠키 방식의 보안 문제

| 문제 | 설명 |
|------|------|
| 쿠키 값 조작 | 브라우저에서 `memberId=2`로 변경하면 다른 사용자로 접근 가능 |
| 쿠키 탈취 | 네트워크/XSS로 탈취 시 무한정 사용 가능 |

**해결**: 서버에 중요 정보를 저장하고, 클라이언트에는 추측 불가능한 랜덤 토큰(세션ID)만 전달.

---

## 세션 직접 구현

```java
@Component
public class SessionManager {
    public static final String SESSION_COOKIE_NAME = "mySessionId";
    private Map<String, Object> sessionStore = new ConcurrentHashMap<>();

    // 세션 생성
    public void createSession(Object value, HttpServletResponse response) {
        String sessionId = UUID.randomUUID().toString();
        sessionStore.put(sessionId, value);
        Cookie mySessionCookie = new Cookie(SESSION_COOKIE_NAME, sessionId);
        response.addCookie(mySessionCookie);
    }

    // 세션 조회
    public Object getSession(HttpServletRequest request) {
        Cookie sessionCookie = findCookie(request, SESSION_COOKIE_NAME);
        if (sessionCookie == null) { return null; }
        return sessionStore.get(sessionCookie.getValue());
    }

    // 세션 삭제
    public void expire(HttpServletRequest request) {
        Cookie sessionCookie = findCookie(request, SESSION_COOKIE_NAME);
        if (sessionCookie != null) { sessionStore.remove(sessionCookie.getValue()); }
    }

    private Cookie findCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) { return null; }
        return Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals(cookieName))
                .findAny().orElse(null);
    }
}
```

**핵심**: 쿠키에는 UUID sessionId만 저장. 서버 Map에 sessionId → 회원 정보 매핑.

---

## HttpSession — 서블릿 표준

### 세션 상수

```java
public class SessionConst {
    public static final String LOGIN_MEMBER = "loginMember";
}
```

### 로그인 (HttpSession 생성)

```java
@PostMapping("/login")
public String loginV3(@Valid @ModelAttribute LoginForm form, BindingResult bindingResult,
                      HttpServletRequest request) {
    // ...검증...
    // 세션 생성 (없으면 생성 - 기본 create:true)
    HttpSession session = request.getSession();
    // 세션에 로그인 회원 정보 보관
    session.setAttribute(SessionConst.LOGIN_MEMBER, loginMember);
    return "redirect:/";
}
```

### 로그아웃 (세션 무효화)

```java
@PostMapping("/logout")
public String logoutV3(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
        session.invalidate();  // 세션 삭제
    }
    return "redirect:/";
}
```

### 홈 — 세션 조회

```java
@GetMapping("/")
public String homeLoginV3(HttpServletRequest request, Model model) {
    // false: 없으면 null 반환 (없는데 생성하면 낭비)
    HttpSession session = request.getSession(false);
    if (session == null) { return "home"; }
    Member loginMember = (Member) session.getAttribute(SessionConst.LOGIN_MEMBER);
    if (loginMember == null) { return "home"; }
    model.addAttribute("member", loginMember);
    return "loginHome";
}
```

### getSession() 옵션

| 메서드 | 동작 |
|--------|------|
| `request.getSession()` / `getSession(true)` | 기존 세션 있으면 반환, 없으면 새로 생성 |
| `request.getSession(false)` | 기존 세션 있으면 반환, 없으면 null |

> **홈/조회에서는 `getSession(false)` 사용** — 세션 없는 사용자에게 불필요한 세션 생성 방지

---

## @SessionAttribute

```java
// 세션 조회를 더 간결하게
@GetMapping("/")
public String homeLoginV3Spring(
        @SessionAttribute(name = SessionConst.LOGIN_MEMBER, required = false) Member loginMember,
        Model model) {
    if (loginMember == null) { return "home"; }
    model.addAttribute("member", loginMember);
    return "loginHome";
}
```

> `@SessionAttribute`는 세션을 새로 생성하지 않음. 이미 로그인된 사용자 찾는 용도로만 사용.

---

## URL에 jsessionid 제거

세션 첫 요청 시 URL에 자동으로 `;jsessionid=xxx`가 붙는 문제 해결:

```properties
# application.properties
server.servlet.session.tracking-modes=cookie
```

---

## 세션 정보 조회

```java
@GetMapping("/session-info")
public String sessionInfo(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    // ...
    log.info("sessionId={}", session.getId());
    log.info("maxInactiveInterval={}", session.getMaxInactiveInterval()); // 1800초(30분)
    log.info("creationTime={}", new Date(session.getCreationTime()));
    log.info("lastAccessedTime={}", new Date(session.getLastAccessedTime()));
    log.info("isNew={}", session.isNew());
    return "세션 출력";
}
```

---

## 세션 타임아웃

```properties
# 글로벌 설정 (초 단위)
server.servlet.session.timeout=1800  # 기본값 30분
```

```java
// 특정 세션만 설정
session.setMaxInactiveInterval(1800);
```

- **만료 기준**: `lastAccessedTime` (마지막 요청 시간) 기준으로 갱신
- 사용자가 지속적으로 요청하면 세션은 계속 유지됨
- 세션에 최소한의 데이터만 저장 (메모리 절약)

---

## 면접 Q&A

**Q: 쿠키 방식 로그인의 보안 문제와 세션이 해결하는 방법은?**
A: 쿠키에 `memberId=1` 같은 실제 사용자 ID를 담으면 브라우저에서 값을 조작하거나 탈취 시 다른 사용자로 가장할 수 있다. 세션 방식에서는 로그인 성공 시 서버에서 UUID로 예측 불가능한 세션ID를 생성해 서버 메모리(Map)에 사용자 정보와 함께 저장하고, 클라이언트에는 세션ID만 쿠키로 전달한다. 실제 사용자 정보는 서버에만 있으므로 쿠키가 탈취돼도 직접적인 정보 노출이 없고, 서버에서 해당 세션을 삭제하면 즉시 무효화된다.

**Q: request.getSession()과 getSession(false)의 차이와 사용 시점은?**
A: `getSession()`은 세션이 없으면 새로 생성하고, `getSession(false)`는 세션이 없으면 null을 반환한다. 로그인 처리 시에는 세션을 생성해야 하므로 `getSession()`을 쓰고, 홈이나 조회 페이지처럼 단순히 로그인 여부를 확인할 때는 `getSession(false)`를 써야 한다. 비로그인 사용자에게 불필요한 세션이 생성되면 메모리가 낭비된다.

**Q: 세션 타임아웃이 동작하는 방식은?**
A: 기본 30분(1800초)이며, 세션 생성 후 30분이 지나면 삭제되는 게 아니라 마지막 요청 시간(`lastAccessedTime`) 기준으로 30분이 지나면 만료된다. 사용자가 계속 요청하면 `lastAccessedTime`이 갱신되어 세션이 유지된다. `server.servlet.session.timeout=초` 로 전체 설정하거나 `session.setMaxInactiveInterval(초)` 로 개별 세션에 설정할 수 있다.
