# API 예외 처리

> 스프링 MVC 2편 | MVC2

---

## 전체 흐름 한눈에 보기

```
BasicErrorController
  HTML 오류 페이지 → 적합
  API 오류 → 부적합 (Accept별 세밀 제어 어려움)

HandlerExceptionResolver (원리 이해)
  컨트롤러 밖으로 예외가 던져지면 ExceptionResolver가 처리
  예외를 해결 → WAS까지 전파 없이 정상 응답으로 처리 가능
  반환: 빈 ModelAndView(뷰 없이 리턴) / ModelAndView(뷰 렌더링) / null(다음 resolver)

스프링이 제공하는 ExceptionResolver (우선순위 순)
  1. ExceptionHandlerExceptionResolver → @ExceptionHandler 처리 (실무 핵심)
  2. ResponseStatusExceptionResolver  → @ResponseStatus / ResponseStatusException
  3. DefaultHandlerExceptionResolver  → 스프링 내부 예외 처리

@ExceptionHandler + @ControllerAdvice (실무 표준)
  @ExceptionHandler: 컨트롤러 내 예외 처리 메서드
  @RestControllerAdvice: 전역 예외 처리 분리 → 정상 코드와 예외 처리 코드 완전 분리
```

---

## HandlerExceptionResolver 인터페이스

```java
public interface HandlerExceptionResolver {
    ModelAndView resolveException(
        HttpServletRequest request, HttpServletResponse response,
        Object handler, Exception ex);
}
```

### 반환 값에 따른 동작

| 반환 값 | 동작 |
|---------|------|
| `new ModelAndView()` (빈) | 뷰 렌더링 없이 정상 흐름으로 서블릿 리턴 |
| `ModelAndView` (뷰 지정) | 뷰 렌더링 |
| `null` | 다음 ExceptionResolver 탐색, 없으면 WAS까지 예외 전파 |

> ExceptionResolver가 예외를 처리하면 WAS까지 예외가 전파되지 않음 → WAS의 오류 페이지 내부 재요청 없음 → 깔끔

### 직접 구현 예 (MyHandlerExceptionResolver)

```java
@Slf4j
public class MyHandlerExceptionResolver implements HandlerExceptionResolver {
    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                         Object handler, Exception ex) {
        try {
            if (ex instanceof IllegalArgumentException) {
                log.info("IllegalArgumentException resolver to 400");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
                return new ModelAndView();  // 빈 ModelAndView: 정상 흐름으로 처리
            }
        } catch (IOException e) {
            log.error("resolver ex", e);
        }
        return null;  // 다음 ExceptionResolver로
    }
}
```

```java
// WebConfig 등록
@Override
public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
    resolvers.add(new MyHandlerExceptionResolver());
}
// configureHandlerExceptionResolvers()는 스프링 기본 ExceptionResolver가 제거되므로 사용 금지
```

---

## 스프링이 제공하는 ExceptionResolver

### 1. ResponseStatusExceptionResolver

```java
// 방법 1: 예외 클래스에 @ResponseStatus 애노테이션
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "잘못된 요청 오류")
public class BadRequestException extends RuntimeException {}

// 방법 2: reason을 MessageSource에서 조회
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "error.bad")
public class BadRequestException extends RuntimeException {}
// → messages.properties: error.bad=잘못된 요청 오류입니다.

// 방법 3: ResponseStatusException (동적 상태 코드, 라이브러리 예외 처리 시)
throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.bad", new IllegalArgumentException());
```

> 내부적으로 `response.sendError(statusCode, resolvedReason)` 호출 → WAS가 `/error` 재요청

### 2. DefaultHandlerExceptionResolver

스프링 내부 예외를 적절한 HTTP 상태 코드로 변환.

| 내부 예외 | 변환 결과 |
|----------|---------|
| `TypeMismatchException` | 500 → **400** |
| `HttpRequestMethodNotSupportedException` | 500 → **405** |
| `HttpMediaTypeNotSupportedException` | 500 → **415** |

---

## @ExceptionHandler — 실무 표준

### 컨트롤러 내 사용

```java
@Data
@AllArgsConstructor
public class ErrorResult {
    private String code;
    private String message;
}

@Slf4j
@RestController
public class ApiExceptionV2Controller {

    // IllegalArgumentException → 400
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResult illegalExHandle(IllegalArgumentException e) {
        log.error("[exceptionHandle] ex", e);
        return new ErrorResult("BAD", e.getMessage());
    }

    // UserException → 400 (동적 상태 코드: ResponseEntity 사용)
    @ExceptionHandler  // 파라미터 타입 UserException 자동 인식
    public ResponseEntity<ErrorResult> userExHandle(UserException e) {
        ErrorResult errorResult = new ErrorResult("USER-EX", e.getMessage());
        return new ResponseEntity<>(errorResult, HttpStatus.BAD_REQUEST);
    }

    // Exception (상위 타입) → 500
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler
    public ErrorResult exHandle(Exception e) {
        log.error("[exceptionHandle] ex", e);
        return new ErrorResult("EX", "내부 오류");
    }

    @GetMapping("/api2/members/{id}")
    public MemberDto getMember(@PathVariable("id") String id) { ... }
}
```

### @ExceptionHandler 특징

```java
// 복수 예외 처리
@ExceptionHandler({AException.class, BException.class})
public String ex(Exception e) { ... }

// 예외 생략 → 파라미터 타입으로 자동 지정
@ExceptionHandler
public ResponseEntity<ErrorResult> userExHandle(UserException e) { ... }

// 우선순위: 자식 > 부모 (더 구체적인 것이 우선)
@ExceptionHandler(부모예외.class) public String 부모처리() {}
@ExceptionHandler(자식예외.class) public String 자식처리() {}
// → 자식예외 발생 시 자식처리() 호출
```

### 실행 흐름

```
1. 컨트롤러에서 IllegalArgumentException 발생
2. ExceptionHandlerExceptionResolver 실행 (우선순위 1위)
3. 해당 컨트롤러에 @ExceptionHandler(IllegalArgumentException.class) 있는지 확인
4. illegalExHandle() 실행
5. @RestController → @ResponseBody 적용 → HttpMessageConverter → JSON 응답
6. @ResponseStatus(BAD_REQUEST) → HTTP 400
```

---

## @RestControllerAdvice — 전역 예외 처리 분리

### 사용 이유

`@ExceptionHandler`를 각 컨트롤러에 작성하면 정상 코드와 예외 처리 코드가 섞임.  
`@RestControllerAdvice`로 분리하면 컨트롤러는 비즈니스 로직에만 집중.

```java
@Slf4j
@RestControllerAdvice  // = @ControllerAdvice + @ResponseBody
public class ExControllerAdvice {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResult illegalExHandle(IllegalArgumentException e) {
        log.error("[exceptionHandle] ex", e);
        return new ErrorResult("BAD", e.getMessage());
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResult> userExHandle(UserException e) {
        ErrorResult errorResult = new ErrorResult("USER-EX", e.getMessage());
        return new ResponseEntity<>(errorResult, HttpStatus.BAD_REQUEST);
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler
    public ErrorResult exHandle(Exception e) {
        log.error("[exceptionHandle] ex", e);
        return new ErrorResult("EX", "내부 오류");
    }
}
```

### 대상 컨트롤러 지정

```java
// 특정 애노테이션 있는 컨트롤러만
@ControllerAdvice(annotations = RestController.class)
public class ExampleAdvice1 {}

// 특정 패키지와 하위
@ControllerAdvice("org.example.controllers")
public class ExampleAdvice2 {}

// 특정 클래스 타입
@ControllerAdvice(assignableTypes = {ControllerInterface.class, AbstractController.class})
public class ExampleAdvice3 {}

// 대상 지정 생략 → 모든 컨트롤러에 적용 (글로벌)
```

---

## 면접 Q&A

**Q: @ExceptionHandler와 @ControllerAdvice를 사용하는 이유는?**
A: `HandlerExceptionResolver`를 직접 구현하면 `ModelAndView`를 반환해야 하고, JSON 응답을 위해 `response`에 직접 데이터를 써야 해서 매우 불편하다. `@ExceptionHandler`는 컨트롤러처럼 `@ResponseBody`, `ResponseEntity` 등을 그대로 쓸 수 있어 훨씬 편리하다. 다만 각 컨트롤러에 작성하면 정상 코드와 예외 처리 코드가 섞이므로, `@RestControllerAdvice`를 별도 클래스로 만들어 전역으로 분리하면 깔끔한 구조가 된다.

**Q: ResponseStatusExceptionResolver와 DefaultHandlerExceptionResolver의 차이는?**
A: `ResponseStatusExceptionResolver`는 개발자가 정의한 예외에 `@ResponseStatus(code = HttpStatus.BAD_REQUEST)`를 붙이거나, `ResponseStatusException`을 던져서 HTTP 상태 코드를 지정하는 방식이다. `DefaultHandlerExceptionResolver`는 스프링 내부에서 발생하는 예외(예: 타입 바인딩 실패 시 `TypeMismatchException`)를 적절한 상태 코드로 변환해준다. 파라미터에 잘못된 타입이 들어와 생기는 `TypeMismatchException`은 클라이언트 문제이므로 400으로 변환한다.
