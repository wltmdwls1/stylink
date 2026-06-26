# 메시지, 국제화

> 스프링 MVC 2편 | MVC2

---

## 전체 흐름 한눈에 보기

```
메시지
  HTML에 하드코딩된 "상품명", "가격" 등 문구를 messages.properties에 모아 관리
  Thymeleaf: #{label.item.itemName} → messages.properties에서 조회

국제화
  messages_en.properties / messages_ko.properties → Accept-Language 헤더로 자동 선택
  LocaleResolver: Locale 선택 방식 변경 가능 (기본: AcceptHeaderLocaleResolver)

스프링 부트 기본 설정
  spring.messages.basename=messages (자동 등록)
  messages.properties, messages_en.properties → 자동 인식
```

---

## MessageSource 설정

### 직접 빈 등록 (스프링 MVC)

```java
@Bean
public MessageSource messageSource() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasenames("messages", "errors"); // 여러 파일 등록 가능
    messageSource.setDefaultEncoding("utf-8");
    return messageSource;
}
```

### 스프링 부트 자동 설정

```properties
# application.properties
spring.messages.basename=messages  # 기본값 — 명시 생략 가능
```

→ `messages.properties`, `messages_en.properties`, `messages_ko.properties` 자동 인식

---

## messages.properties 작성 예시

```properties
# /resources/messages.properties (기본 — 한국어)
label.item=상품
label.item.id=상품 ID
label.item.itemName=상품명
label.item.price=가격
label.item.quantity=수량
page.items=상품 목록
page.item=상품 상세
page.addItem=상품 등록
page.updateItem=상품 수정
button.save=저장
button.cancel=취소

hello=안녕
hello.name=안녕 {0}
```

```properties
# /resources/messages_en.properties (영어)
label.item=Item
label.item.itemName=Item Name
label.item.price=price
page.items=Item List
button.save=Save
hello=hello
hello.name=hello {0}
```

---

## MessageSource 사용법

```java
public interface MessageSource {
    String getMessage(String code, Object[] args, String defaultMessage, Locale locale);
    String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException;
}
```

```java
@Autowired MessageSource ms;

// 기본 조회 (locale=null → messages.properties)
ms.getMessage("hello", null, null)             // → "안녕"

// 없는 코드 → NoSuchMessageException
ms.getMessage("no_code", null, null)

// 없는 코드 + defaultMessage → defaultMessage 반환
ms.getMessage("no_code", null, "기본 메시지", null)  // → "기본 메시지"

// 파라미터 치환 ({0} 자리에 "Spring" 삽입)
ms.getMessage("hello.name", new Object[]{"Spring"}, null)  // → "안녕 Spring"

// 영어 locale
ms.getMessage("hello", null, Locale.ENGLISH)   // → "hello"
```

### Locale 조회 우선순위

```
Locale = en_US → messages_en_US → messages_en → messages (기본)
```

---

## Thymeleaf에서 메시지 사용

```html
<!-- #{...} 표현식으로 messages.properties 조회 -->
<h2 th:text="#{page.addItem}">상품 등록</h2>
<label th:text="#{label.item.itemName}">상품명</label>
<button th:text="#{button.save}">저장</button>

<!-- 파라미터가 있는 메시지: hello.name=안녕 {0} -->
<p th:text="#{hello.name(${item.itemName})}"></p>
```

> HTML 파일로 직접 열면 태그 내 텍스트("상품 등록")가 표시되고, 서버 렌더링 시 `th:text`로 교체됨

---

## 국제화 — LocaleResolver

```
HTTP 요청 → LocaleResolver.resolveLocale() → Locale 결정 → MessageSource 조회
```

| 구현체 | 동작 |
|--------|------|
| `AcceptHeaderLocaleResolver` (기본) | `Accept-Language` 헤더 사용 |
| `SessionLocaleResolver` | 세션에 저장된 Locale 사용 |
| `CookieLocaleResolver` | 쿠키에 저장된 Locale 사용 |
| `FixedLocaleResolver` | 고정된 Locale 사용 |

```java
// Locale 선택 방식 변경 시 (예: 쿠키 기반)
@Bean
public LocaleResolver localeResolver() {
    return new CookieLocaleResolver();
}
```

---

## 면접 Q&A

**Q: 메시지 기능이 필요한 이유는?**
A: HTML 파일에 "상품명", "가격" 같은 문구가 하드코딩되어 있으면 기획자가 단어 변경을 요청할 때 모든 화면 파일을 수정해야 한다. `messages.properties`에 한 곳에서 관리하면 파일 하나만 수정하면 전체에 반영된다.

**Q: 스프링 MVC에서 국제화가 동작하는 방식은?**
A: HTTP 요청의 `Accept-Language` 헤더를 `AcceptHeaderLocaleResolver`가 읽어 Locale을 결정하고, `MessageSource`가 해당 Locale에 맞는 `.properties` 파일(`messages_en.properties` 등)에서 메시지를 조회한다. Thymeleaf에서는 `#{...}` 표현식으로 메시지를 출력한다. Locale 선택 방식을 바꾸려면 `LocaleResolver` 구현체를 빈으로 등록하면 된다.
