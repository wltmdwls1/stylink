# 스프링 MVC - 웹 페이지 만들기

> 스프링 MVC 1편 | MVC1

---

## 전체 흐름 한눈에 보기

```
상품 CRUD 예제로 스프링 MVC + Thymeleaf 실전 적용

도메인 계층
  Item (id, itemName, price, quantity) → ItemRepository (store: static Map)

컨트롤러 + 뷰 (BasicItemController)
  GET  /basic/items         → 목록
  GET  /basic/items/{id}    → 상세
  GET  /basic/items/add     → 등록 폼
  POST /basic/items/add     → 등록 처리 (v1~v4: @RequestParam → @ModelAttribute)
  GET  /basic/items/{id}/edit  → 수정 폼
  POST /basic/items/{id}/edit  → 수정 처리 → redirect

핵심 패턴
  PRG (Post/Redirect/Get)    → 새로고침 중복 등록 방지
  RedirectAttributes         → redirect 후 URL 인코딩 + 쿼리파라미터 + 일회성 메시지
```

---

## 도메인 모델

```java
@Data
public class Item {
    private Long id;
    private String itemName;
    private Integer price;
    private Integer quantity;
}
```

```java
@Repository
public class ItemRepository {
    private static final Map<Long, Item> store = new HashMap<>();
    private static long sequence = 0L;

    public Item save(Item item) {
        item.setId(++sequence);
        store.put(item.getId(), item);
        return item;
    }
    public Item findById(Long id) { return store.get(id); }
    public List<Item> findAll() { return new ArrayList<>(store.values()); }
    public void update(Long itemId, Item updateParam) {
        Item findItem = findById(itemId);
        findItem.setItemName(updateParam.getItemName());
        findItem.setPrice(updateParam.getPrice());
        findItem.setQuantity(updateParam.getQuantity());
    }
    public void clearStore() { store.clear(); }
}
```

---

## BasicItemController

```java
@Controller
@RequestMapping("/basic/items")
@RequiredArgsConstructor  // final 필드 생성자 자동 생성 → @Autowired 자동 적용
public class BasicItemController {

    private final ItemRepository itemRepository;

    @GetMapping
    public String items(Model model) {
        model.addAttribute("items", itemRepository.findAll());
        return "basic/items";
    }

    @GetMapping("/{itemId}")
    public String item(@PathVariable Long itemId, Model model) {
        model.addAttribute("item", itemRepository.findById(itemId));
        return "basic/item";
    }

    @GetMapping("/add")
    public String addForm() { return "basic/addForm"; }

    // 등록 처리 — v4 (실용적 최종형)
    @PostMapping("/add")
    public String addItemV4(Item item) {
        itemRepository.save(item);
        return "basic/item";  // 이 방식은 PRG 문제 있음 → addItemV5로 개선
    }

    @GetMapping("/{itemId}/edit")
    public String editForm(@PathVariable Long itemId, Model model) {
        model.addAttribute("item", itemRepository.findById(itemId));
        return "basic/editForm";
    }

    @PostMapping("/{itemId}/edit")
    public String edit(@PathVariable Long itemId, @ModelAttribute Item item) {
        itemRepository.update(itemId, item);
        return "redirect:/basic/items/{itemId}";  // @PathVariable 값 redirect에서 재사용
    }

    @PostConstruct  // 빈 초기화 후 테스트 데이터 삽입
    public void init() {
        itemRepository.save(new Item("testA", 10000, 10));
        itemRepository.save(new Item("testB", 20000, 20));
    }
}
```

---

## @ModelAttribute 등록 처리 진화

```java
// v1 — @RequestParam 개별 바인딩
@PostMapping("/add")
public String addItemV1(@RequestParam String itemName,
                        @RequestParam int price,
                        @RequestParam Integer quantity, Model model) {
    Item item = new Item();
    item.setItemName(itemName); item.setPrice(price); item.setQuantity(quantity);
    itemRepository.save(item);
    model.addAttribute("item", item);
    return "basic/item";
}

// v2 — @ModelAttribute: 객체 생성 + setter 자동 + model.addAttribute 자동
@PostMapping("/add")
public String addItemV2(@ModelAttribute("item") Item item, Model model) {
    itemRepository.save(item);
    // model.addAttribute("item", item); 생략 가능
    return "basic/item";
}

// v3 — name 생략: 클래스명 첫 글자 소문자 → model 키 "item"
@PostMapping("/add")
public String addItemV3(@ModelAttribute Item item) {
    itemRepository.save(item);
    return "basic/item";
}

// v4 — @ModelAttribute 자체 생략 (단순 타입이 아닌 객체 → 자동 적용)
@PostMapping("/add")
public String addItemV4(Item item) {
    itemRepository.save(item);
    return "basic/item";
}
```

| 버전 | 차이점 |
|------|--------|
| v1 | `@RequestParam` 1개씩 → 객체 수동 세팅 |
| v2 | `@ModelAttribute("item")` → 자동 바인딩 + 모델 자동 등록 |
| v3 | name 생략 → `Item` → `"item"` 키 자동 추론 |
| v4 | `@ModelAttribute` 자체 생략 |

---

## PRG 패턴 (Post/Redirect/Get)

### 문제: 새로고침 중복 등록

```
POST /add (등록) → 200 OK → item.html 렌더링
  ↓ 새로고침
POST /add (재등록!) → 중복 저장 발생
```

### 해결: 등록 후 redirect

```java
// v5 — PRG 패턴 적용
@PostMapping("/add")
public String addItemV5(Item item) {
    itemRepository.save(item);
    return "redirect:/basic/items/" + item.getId();
    // 주의: URL 인코딩 안 됨 → RedirectAttributes 사용 권장
}
```

```
POST /add (등록) → 302 redirect → GET /items/{id} → 200 OK
  ↓ 새로고침
GET /items/{id} (재조회) → 중복 없음
```

---

## RedirectAttributes

```java
// v6 — RedirectAttributes: URL 인코딩 + pathVariable + 쿼리파라미터 처리
@PostMapping("/add")
public String addItemV6(Item item, RedirectAttributes redirectAttributes) {
    Item savedItem = itemRepository.save(item);
    redirectAttributes.addAttribute("itemId", savedItem.getId());
    redirectAttributes.addAttribute("status", true);
    return "redirect:/basic/items/{itemId}";
    // 결과: redirect:/basic/items/3?status=true
    // {itemId} → pathVariable 치환, status → 쿼리 파라미터 자동 추가
}
```

### 뷰에서 status 파라미터 활용

```html
<!-- templates/basic/item.html -->
<h2 th:if="${param.status}" th:text="'저장 완료!'"></h2>
```

- `${param.status}`: Thymeleaf에서 쿼리 파라미터 직접 접근
- `th:if`: 조건부 렌더링 (false/null 이면 태그 미출력)
- 상품 목록 → 상세로 이동 시에는 파라미터 없으므로 메시지 미노출

---

## Thymeleaf 핵심 문법 정리

| 문법 | 예시 | 설명 |
|------|------|------|
| URL 링크 | `th:href="@{/basic/items}"` | 컨텍스트 자동 추가 |
| URL + 경로변수 | `th:href="@{/items/{id}(id=${item.id})}"` | PathVariable 치환 |
| URL + 쿼리파라미터 | `th:href="@{/items/{id}(id=${item.id}, q='test')}"` | 나머지는 쿼리파라미터 |
| 리터럴 대체 | `th:onclick="\|location.href='@{/items/add}'\|"` | `\|...\|` 내부에서 변수/URL 혼합 |
| 텍스트 출력 | `th:text="${item.price}"` | 태그 내 텍스트 교체 |
| 속성 변경 | `th:value="${item.id}"` | `value` 속성 교체 |
| 반복 | `th:each="item : ${items}"` | 컬렉션 반복 |
| 조건 | `th:if="${param.status}"` | 조건부 출력 |
| form action | `th:action` (값 없음) | 현재 URL로 submit |
| 쿼리파라미터 접근 | `${param.status}` | 뷰에서 직접 접근 |

### 타임리프 자연스러운 렌더링 (Natural Templates)

```html
<!-- HTML 파일로 직접 열면 value="1"로 표시, 서버 렌더링 시 th:value="${item.id}"로 교체 -->
<input value="1" th:value="${item.id}">
```

- `th:xxx`가 없으면 HTML 기본 속성 그대로
- 서버 렌더링 시 `th:xxx`가 HTML `xxx` 속성을 덮어씀
- 순수 HTML 파일로도 브라우저에서 열어 확인 가능

---

## 면접 Q&A

**Q: PRG 패턴이란?**
A: Post/Redirect/Get 패턴은 POST 요청 처리 후 뷰를 직접 렌더링하지 않고 GET 요청으로 redirect하는 패턴이다. POST 처리 후 뷰를 반환하면 브라우저 새로고침 시 마지막 요청인 POST가 재전송되어 데이터가 중복 저장되는 문제가 생긴다. Redirect 후에는 새로고침 시 GET 요청만 재전송되므로 중복 등록 문제가 해결된다.

**Q: RedirectAttributes를 사용하는 이유는?**
A: `"redirect:/items/" + item.getId()`처럼 문자열 연결로 redirect URL을 만들면 URL 인코딩이 적용되지 않아 한글 등 특수문자가 포함된 경우 깨질 수 있다. `RedirectAttributes`를 사용하면 `addAttribute()`로 추가한 값이 URL 인코딩 처리되고, `redirect:/items/{itemId}` 패턴의 `{itemId}`는 pathVariable로 치환되며, 나머지는 자동으로 쿼리 파라미터로 추가된다. 뷰에서는 `${param.xxx}`로 해당 파라미터를 접근할 수 있다.

**Q: @ModelAttribute가 @RequestParam과 다른 편리한 점은?**
A: `@ModelAttribute`는 요청 파라미터를 객체에 자동으로 바인딩하고(파라미터 이름 → setter 호출), 그 객체를 Model에 자동으로 추가한다. 덕분에 파라미터별 `@RequestParam`, 객체 생성 코드, `model.addAttribute()` 호출 세 가지를 모두 생략할 수 있다. name을 생략하면 클래스명 첫 글자를 소문자로 바꾼 이름이 model 키로 사용되며, `@ModelAttribute` 자체도 생략 가능하다.
