# 검증1 - Validation

> 스프링 MVC 2편 | MVC2

---

## 전체 흐름 한눈에 보기

```
V1 — Map<String, String> errors 직접 구현 (원시적)
  → 타입 오류 처리 불가, 입력값 유지 안 됨

V2 — BindingResult + FieldError / ObjectError
  → @ModelAttribute 바로 다음 파라미터로 선언
  → FieldError(rejectedValue) → 타입 오류 시에도 입력값 유지

rejectValue() / reject()
  → MessageCodesResolver가 에러 코드 → 메시지 코드 배열 자동 생성
  → errors.properties에서 우선순위(세밀→범용) 순으로 조회

Validator 분리
  → ItemValidator implements Validator
  → @InitBinder + WebDataBinder.addValidators()
  → @Validated 애노테이션으로 자동 호출
```

---

## 검증 요구사항

| 구분 | 조건 |
|------|------|
| 타입 오류 | 가격, 수량에 문자 입력 시 오류 처리 |
| 상품명 | 필수, 공백 불가 |
| 가격 | 1,000원 이상 ~ 1,000,000원 이하 |
| 수량 | 최대 9,999 |
| 복합 | 가격 × 수량 합계 10,000원 이상 |

> 클라이언트 검증은 조작 가능 → 서버 검증 필수. API 방식에도 API 응답으로 검증 오류 반환해야 함.

---

## V1 — Map 직접 구현 (한계 확인)

```java
@PostMapping("/add")
public String addItem(@ModelAttribute Item item, Model model) {
    Map<String, String> errors = new HashMap<>();

    if (!StringUtils.hasText(item.getItemName())) {
        errors.put("itemName", "상품 이름은 필수입니다.");
    }
    if (item.getPrice() == null || item.getPrice() < 1000 || item.getPrice() > 1000000) {
        errors.put("price", "가격은 1,000 ~ 1,000,000 까지 허용합니다.");
    }
    if (item.getQuantity() == null || item.getQuantity() > 9999) {
        errors.put("quantity", "수량은 최대 9,999 까지 허용합니다.");
    }
    if (item.getPrice() != null && item.getQuantity() != null) {
        int resultPrice = item.getPrice() * item.getQuantity();
        if (resultPrice < 10000) {
            errors.put("globalError", "가격 * 수량의 합은 10,000원 이상이어야 합니다. 현재 값 = " + resultPrice);
        }
    }
    if (!errors.isEmpty()) {
        model.addAttribute("errors", errors);
        return "validation/v1/addForm";
    }
    // 성공 로직
    ...
}
```

**뷰 (v1)**

```html
<!-- errors?.containsKey() — Safe Navigation Operator: errors가 null이면 NPE 대신 null 반환 -->
<div th:if="${errors?.containsKey('globalError')}">
    <p class="field-error" th:text="${errors['globalError']}">전체 오류 메시지</p>
</div>
<input th:classappend="${errors?.containsKey('itemName')} ? 'field-error' : _" ...>
<div class="field-error" th:if="${errors?.containsKey('itemName')}" th:text="${errors['itemName']}">
```

**V1 한계**: 타입 오류(문자→Integer) 시 스프링 MVC가 400 에러 반환 → 컨트롤러 진입 자체가 불가

---

## V2 — BindingResult

```java
// BindingResult는 반드시 @ModelAttribute 바로 다음에 선언
@PostMapping("/add")
public String addItemV1(@ModelAttribute Item item, BindingResult bindingResult, ...) {

    // 필드 오류
    if (!StringUtils.hasText(item.getItemName())) {
        bindingResult.addError(new FieldError("item", "itemName", "상품 이름은 필수입니다."));
    }
    // 글로벌 오류 (ObjectError)
    if (resultPrice < 10000) {
        bindingResult.addError(new ObjectError("item", "가격 * 수량의 합은 10,000원 이상이어야 합니다."));
    }

    if (bindingResult.hasErrors()) {
        return "validation/v2/addForm";
    }
    // 성공 로직
}
```

> `BindingResult`는 Model에 자동으로 담긴다.
> 
> `BindingResult`가 있으면 `@ModelAttribute` 바인딩 타입 오류 시 400이 아닌 컨트롤러 호출 후 FieldError에 담김.

**뷰 (v2) — Thymeleaf + BindingResult**

```html
<!-- 글로벌 오류 -->
<div th:if="${#fields.hasGlobalErrors()}">
    <p th:each="err : ${#fields.globalErrors()}" th:text="${err}">전체 오류</p>
</div>

<!-- 필드 오류: th:errorclass, th:errors -->
<input th:field="*{itemName}" th:errorclass="field-error" class="form-control">
<div class="field-error" th:errors="*{itemName}">상품명 오류</div>
```

---

## FieldError — 입력값 보존 (rejectedValue)

```java
// 생성자 (긴 버전) — rejectedValue로 타입 오류 시에도 입력 데이터 유지
public FieldError(String objectName, String field,
                  @Nullable Object rejectedValue, boolean bindingFailure,
                  @Nullable String[] codes, @Nullable Object[] arguments,
                  @Nullable String defaultMessage)

// 예: 가격 필드 오류
bindingResult.addError(new FieldError("item", "price",
    item.getPrice(), false, null, null, "가격은 1,000 ~ 1,000,000 까지 허용합니다."));
```

| 파라미터 | 설명 |
|---------|------|
| `objectName` | `@ModelAttribute` 이름 |
| `field` | 오류 필드 |
| `rejectedValue` | 사용자가 입력한 값(거절된 값) |
| `bindingFailure` | 타입 오류 여부 (바인딩 실패 true, 검증 실패 false) |
| `codes` | 메시지 코드 배열 |
| `arguments` | 메시지 파라미터 ({0}, {1}) |
| `defaultMessage` | 기본 오류 메시지 |

> `th:field="*{price}"` — 오류 시 `FieldError`의 `rejectedValue`를 꺼내서 표시, 정상 시 모델 값 표시

---

## rejectValue() / reject() + MessageCodesResolver

```java
// addItemV4 — 훨씬 간결한 방식
bindingResult.rejectValue("itemName", "required");
bindingResult.rejectValue("price", "range", new Object[]{1000, 1000000}, null);
bindingResult.reject("totalPriceMin", new Object[]{10000, resultPrice}, null);
```

### DefaultMessageCodesResolver 규칙

**객체 오류** (ObjectError) — 2개 생성
```
1. code.objectName    → required.item
2. code               → required
```

**필드 오류** (FieldError) — 4개 생성
```
1. code.objectName.field     → required.item.itemName
2. code.field                → required.itemName
3. code.fieldType            → required.java.lang.String
4. code                      → required
```

→ 생성된 코드 배열을 순서대로 `MessageSource`에서 조회. 처음 매칭되는 메시지 사용.

### errors.properties 계층 구조

```properties
# application.properties
spring.messages.basename=messages,errors
```

```properties
# errors.properties — 구체 → 범용 순으로 작성

# ObjectError
totalPriceMin.item=상품의 가격 * 수량의 합은 {0}원 이상이어야 합니다. 현재 값 = {1}
totalPriceMin=전체 가격은 {0}원 이상이어야 합니다. 현재 값 = {1}

# FieldError Level1 (가장 구체적)
required.item.itemName=상품 이름은 필수입니다.
range.item.price=가격은 {0} ~ {1} 까지 허용합니다.
max.item.quantity=수량은 최대 {0} 까지 허용합니다.

# Level3 (타입별)
required.java.lang.String=필수 문자입니다.
required.java.lang.Integer=필수 숫자입니다.
range.java.lang.Integer={0} ~ {1} 까지의 숫자를 입력해주세요.

# Level4 (가장 범용)
required=필수 값 입니다.
range={0} ~ {1} 까지 허용합니다.
max={0} 까지 허용합니다.

# 타입 오류 시 스프링이 자동 생성하는 코드
typeMismatch.java.lang.Integer=숫자를 입력해주세요.
typeMismatch=타입 오류입니다.
```

> `typeMismatch`: 타입 오류 발생 시 스프링이 자동으로 생성하는 에러 코드. `errors.properties`에 추가하면 사용자 친화적 메시지 출력 가능.

---

## Validator 분리

### ItemValidator

```java
@Component
public class ItemValidator implements Validator {
    @Override
    public boolean supports(Class<?> clazz) {
        return Item.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Item item = (Item) target;
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "itemName", "required");
        if (item.getPrice() == null || item.getPrice() < 1000 || item.getPrice() > 1000000) {
            errors.rejectValue("price", "range", new Object[]{1000, 1000000}, null);
        }
        if (item.getQuantity() == null || item.getQuantity() > 10000) {
            errors.rejectValue("quantity", "max", new Object[]{9999}, null);
        }
        if (item.getPrice() != null && item.getQuantity() != null) {
            int resultPrice = item.getPrice() * item.getQuantity();
            if (resultPrice < 10000) {
                errors.reject("totalPriceMin", new Object[]{10000, resultPrice}, null);
            }
        }
    }
}
```

### @InitBinder + @Validated

```java
// 컨트롤러에 추가
@InitBinder
public void init(WebDataBinder dataBinder) {
    dataBinder.addValidators(itemValidator);
}

// 사용 — @Validated 파라미터 앞에 추가
@PostMapping("/add")
public String addItemV6(@Validated @ModelAttribute Item item, BindingResult bindingResult, ...) {
    if (bindingResult.hasErrors()) { return "validation/v2/addForm"; }
    // 성공 로직
}
```

- `@InitBinder`: 해당 컨트롤러에만 적용 (전역은 `WebMvcConfigurer.getValidator()`)
- `@Validated`: `supports()` 확인 → `validate()` 호출
- `@Validated` vs `@Valid`: `@Validated`는 스프링 전용, `@Valid`는 자바 표준 (Bean Validation 의존성 필요)

---

## 면접 Q&A

**Q: BindingResult와 Errors의 차이는?**
A: `Errors`는 기본 인터페이스이고 `BindingResult`는 `Errors`를 상속한 확장 인터페이스다. 실제 구현체는 `BeanPropertyBindingResult`다. `Errors`는 단순 오류 저장/조회 기능만 있고, `BindingResult`는 거기에 더해 `addError()` 등 추가 기능을 제공한다. 주로 `BindingResult`를 사용한다.

**Q: MessageCodesResolver가 하는 역할은?**
A: `rejectValue("price", "range")`처럼 짧은 에러 코드만 입력하면 `MessageCodesResolver`가 `range.item.price`, `range.price`, `range.java.lang.Integer`, `range` 같은 메시지 코드 배열을 자동으로 생성한다. Thymeleaf가 `th:errors`를 렌더링할 때 이 배열을 순서대로 `MessageSource`에서 조회해 처음 매칭된 메시지를 사용한다. 덕분에 구체적인 메시지는 구체적으로 정의하고, 나머지는 범용 메시지로 처리할 수 있다.
