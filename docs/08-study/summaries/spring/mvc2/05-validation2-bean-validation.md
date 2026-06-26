# 검증2 - Bean Validation

> 스프링 MVC 2편 | MVC2

---

## 전체 흐름 한눈에 보기

```
Bean Validation 기본
  @NotBlank / @NotNull / @Range / @Max 애노테이션으로 검증 로직 선언
  spring-boot-starter-validation 의존성 추가 → LocalValidatorFactoryBean 자동 등록
  @Validated or @Valid → 자동으로 검증 실행 + BindingResult에 에러 담김

오류 코드 (MessageCodesResolver와 동일 패턴)
  @NotBlank → NotBlank.item.itemName / NotBlank.itemName / NotBlank.java.lang.String / NotBlank
  errors.properties에서 커스텀 메시지 정의 가능
  @NotBlank(message = "...") 직접 메시지도 가능

한계 → 등록/수정 요구사항 다름
  groups 기능: @Validated(SaveCheck.class)
  Form 전송 객체 분리: ItemSaveForm / ItemUpdateForm (실무 권장)

API 검증 (@RequestBody)
  @RequestBody 바인딩 실패 → 컨트롤러 진입 불가 (400 예외)
  @ModelAttribute는 실패해도 일부 필드 바인딩 계속
```

---

## 의존성 추가

```groovy
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

- `jakarta.validation-api`: Bean Validation 표준 인터페이스
- `hibernate-validator`: 구현체 (가장 보편적으로 사용)

> Bean Validation 2.0 (JSR-380) 표준. JPA가 표준/하이버네이트 구현체인 것과 동일 구조.

---

## 애노테이션 적용

```java
@Data
public class Item {
    private Long id;

    @NotBlank
    private String itemName;

    @NotNull
    @Range(min = 1000, max = 1000000)
    private Integer price;

    @NotNull
    @Max(9999)
    private Integer quantity;
}
```

| 애노테이션 | 의미 |
|-----------|------|
| `@NotBlank` | null + 빈 문자열 + 공백만 있는 경우 거부 |
| `@NotNull` | null 거부 |
| `@Range(min, max)` | 범위 검사 (하이버네이트 Validator 전용) |
| `@Max(value)` | 최대값 이하 |

> `javax.validation` 패키지: 표준 인터페이스 / `org.hibernate.validator` 패키지: 하이버네이트 전용

---

## 스프링 MVC + Bean Validation 자동 연동

```java
// 컨트롤러 — @Validated 하나만 추가하면 됨
@PostMapping("/add")
public String addItem(@Validated @ModelAttribute Item item, BindingResult bindingResult, ...) {
    if (bindingResult.hasErrors()) {
        return "validation/v3/addForm";
    }
    // 성공 로직
}
```

- `spring-boot-starter-validation` 추가 시 스프링 부트가 `LocalValidatorFactoryBean`을 글로벌 Validator로 자동 등록
- `@Validated` / `@Valid` → `supports()` 확인 → `validate()` 자동 호출
- 검증 실패 시 `FieldError` / `ObjectError`를 `BindingResult`에 담아줌

**주의**: 직접 글로벌 Validator를 `WebMvcConfigurer.getValidator()`로 등록하면 스프링 부트의 Bean Validator 자동 등록 취소됨.

### 바인딩 먼저, 검증은 성공한 필드만

```
@ModelAttribute → 각 필드 타입 변환 시도
  성공 → 해당 필드 BeanValidation 적용
  실패 → typeMismatch FieldError 추가, 해당 필드 BeanValidation 생략
```

---

## Bean Validation 오류 코드

Bean Validation이 `bindingResult`에 에러를 등록할 때 애노테이션 이름을 코드로 사용해 `MessageCodesResolver`가 4단계 코드를 생성한다.

**@NotBlank 예시**
```
NotBlank.item.itemName
NotBlank.itemName
NotBlank.java.lang.String
NotBlank
```

### errors.properties에서 메시지 커스터마이징

```properties
# Bean Validation 메시지 추가 (errors.properties)
NotBlank={0} 공백X
Range={0}, {2} ~ {1} 허용
Max={0}, 최대 {1}
```

> `{0}`: 필드명, `{1}`, `{2}` 등: 각 애노테이션 속성값

### 애노테이션 message 속성 직접 사용

```java
@NotBlank(message = "상품 이름을 입력해주세요.")
private String itemName;
```

**BeanValidation 메시지 탐색 우선순위**
1. `MessageSource`에서 코드(`NotBlank.item.itemName` 등) 순서로 탐색
2. 애노테이션의 `message` 속성
3. 라이브러리 기본 값 (예: "공백일 수 없습니다")

---

## 글로벌 오류 (ObjectError)

### @ScriptAssert — 권장하지 않음

```java
@ScriptAssert(lang = "javascript", script = "_this.price * _this.quantity >= 10000")
public class Item { ... }
```

복잡한 의존관계 검증은 `@ScriptAssert`가 제한적 → **직접 자바 코드로 처리 권장**

```java
// 컨트롤러에서 직접 처리
if (item.getPrice() != null && item.getQuantity() != null) {
    int resultPrice = item.getPrice() * item.getQuantity();
    if (resultPrice < 10000) {
        bindingResult.reject("totalPriceMin", new Object[]{10000, resultPrice}, null);
    }
}
```

---

## 한계 — 등록/수정 요구사항 충돌

| 상황 | id 필수 | quantity 최대값 |
|------|---------|----------------|
| 등록 | 불필요 | 9999 |
| 수정 | 필수 | 제한 없음 |

한 `Item` 클래스에 두 요구사항을 동시에 만족시킬 수 없음.

### 해결 방법 1 — groups

```java
public interface SaveCheck {}
public interface UpdateCheck {}

@Data
public class Item {
    @NotNull(groups = UpdateCheck.class)         // 수정 시만 필수
    private Long id;

    @NotBlank(groups = {SaveCheck.class, UpdateCheck.class})
    private String itemName;

    @NotNull(groups = {SaveCheck.class, UpdateCheck.class})
    @Max(value = 9999, groups = SaveCheck.class)  // 등록 시만 최대 9999
    private Integer quantity;
}

// 컨트롤러
@PostMapping("/add")
public String addItem(@Validated(SaveCheck.class) @ModelAttribute Item item, ...) { ... }

@PostMapping("/{itemId}/edit")
public String edit(@PathVariable Long itemId, @Validated(UpdateCheck.class) @ModelAttribute Item item, ...) { ... }
```

> `@Valid`는 groups 기능 없음. groups 사용 시 반드시 `@Validated` 사용.

### 해결 방법 2 — Form 전송 객체 분리 (실무 권장)

```java
// 등록 전용 폼
@Data
public class ItemSaveForm {
    @NotBlank
    private String itemName;
    @NotNull @Range(min = 1000, max = 1000000)
    private Integer price;
    @NotNull @Max(value = 9999)
    private Integer quantity;
}

// 수정 전용 폼
@Data
public class ItemUpdateForm {
    @NotNull
    private Long id;
    @NotBlank
    private String itemName;
    @NotNull @Range(min = 1000, max = 1000000)
    private Integer price;
    private Integer quantity;   // 수정 시 제한 없음
}
```

```java
// 등록: ItemSaveForm 수신 → Item으로 변환
@PostMapping("/add")
public String addItem(@Validated @ModelAttribute("item") ItemSaveForm form,
                      BindingResult bindingResult, RedirectAttributes redirectAttributes) {
    // ...검증 후
    Item item = new Item();
    item.setItemName(form.getItemName());
    item.setPrice(form.getPrice());
    item.setQuantity(form.getQuantity());
    itemRepository.save(item);
    // ...
}

// 수정: ItemUpdateForm 수신
@PostMapping("/{itemId}/edit")
public String edit(@PathVariable Long itemId,
                   @Validated @ModelAttribute("item") ItemUpdateForm form,
                   BindingResult bindingResult) { ... }
```

> `@ModelAttribute("item")` — 명시하지 않으면 `ItemSaveForm` → `"itemSaveForm"`으로 Model 등록됨. 뷰의 `th:object`와 일치하도록 명시 필요.

**Form 객체 분리 장점**: 각 폼에 정확히 맞는 검증 설정 가능, `Item` 도메인 객체가 깔끔해짐, 나중에 필드가 달라져도 서로 영향 없음.

---

## API (@RequestBody) + Bean Validation

```java
@RestController
@RequestMapping("/validation/api/items")
public class ValidationItemApiController {

    @PostMapping("/add")
    public Object addItem(@RequestBody @Validated ItemSaveForm form,
                          BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return bindingResult.getAllErrors();
        }
        return form;
    }
}
```

### @ModelAttribute vs @RequestBody 바인딩 실패 차이

| 구분 | @ModelAttribute | @RequestBody |
|------|----------------|-------------|
| 바인딩 대상 | URL 쿼리파라미터 / Form 데이터 | HTTP 메시지 바디 (JSON) |
| 필드 단위 처리 | 각 필드 개별 처리 — 일부 실패해도 나머지 계속 진행 | 전체 JSON → 객체 변환 — 실패 시 즉시 예외 (400) |
| 바인딩 실패 시 | 해당 필드만 `typeMismatch` FieldError → 컨트롤러 호출, Validator 나머지 필드 적용 | 컨트롤러 진입 자체 불가, Validator 실행 안 됨 |

> `@RequestBody` 타입 오류 시 `HttpMessageNotReadableException` 발생 → 예외 처리 핸들러에서 처리 필요.

---

## 면접 Q&A

**Q: Bean Validation이란?**
A: Bean Validation은 자바 표준 스펙(JSR-380)으로, 객체의 필드에 `@NotBlank`, `@NotNull`, `@Range`, `@Max` 같은 애노테이션을 선언해 검증 로직을 정의하는 방식이다. 스프링 부트에서 `spring-boot-starter-validation`을 추가하면 하이버네이트 Validator 구현체가 사용되고, `LocalValidatorFactoryBean`이 글로벌 Validator로 자동 등록된다. `@Validated` 애노테이션으로 컨트롤러 파라미터에 적용할 수 있다.

**Q: 등록과 수정에 검증 요구사항이 다를 때 어떻게 처리하는가?**
A: `groups` 기능을 쓰거나 Form 전송 객체를 분리하는 두 가지 방법이 있다. `groups`는 `SaveCheck.class`, `UpdateCheck.class` 인터페이스를 만들어 애노테이션에 `groups = SaveCheck.class`로 지정하고, 컨트롤러에서 `@Validated(SaveCheck.class)`로 호출한다. 하지만 도메인 객체가 복잡해지는 단점이 있다. 실무에서는 `ItemSaveForm`, `ItemUpdateForm` 같이 별도의 폼 전용 객체를 만들어 각각에 맞는 검증 애노테이션을 붙이는 방식을 더 많이 사용한다.

**Q: @RequestBody에서 타입 오류가 나면 @ModelAttribute와 다르게 동작하는 이유는?**
A: `@ModelAttribute`는 각 필드를 개별적으로 바인딩하기 때문에 특정 필드가 실패해도 나머지 필드를 계속 처리하고 컨트롤러를 호출한다. 반면 `@RequestBody`는 `HttpMessageConverter`가 JSON 전체를 한 번에 객체로 변환하는 방식이라, 변환에 실패하면 객체 자체를 만들지 못해 컨트롤러 진입이 불가능하다. 따라서 `@RequestBody` 방식에서는 바인딩 오류와 검증 오류를 별도로 처리해야 한다.
