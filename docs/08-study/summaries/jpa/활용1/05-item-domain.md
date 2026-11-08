# 05. 상품 도메인 개발

## 전체 흐름 한눈에 보기

```
Item 엔티티에 비즈니스 로직 추가 (재고 증감)
  → ItemRepository (persist/merge 분기)
  → ItemService (단순 위임)
```

---

## 1. 엔티티에 비즈니스 로직 넣기 — 도메인 모델 패턴

재고 변경 로직을 Service가 아닌 **Item 엔티티 안에** 직접 구현.

```java
public abstract class Item {
    private int stockQuantity;

    // 재고 추가 (주문 취소 시에도 사용)
    public void addStock(int quantity) {
        this.stockQuantity += quantity;
    }

    // 재고 감소 (주문 시 사용)
    public void removeStock(int quantity) {
        int restStock = this.stockQuantity - quantity;
        if (restStock < 0) {
            throw new NotEnoughStockException("need more stock");
        }
        this.stockQuantity = restStock;
    }
}
```

**핵심**: 재고 데이터를 가진 엔티티가 직접 재고 로직도 가짐 → **응집도 향상**, 외부에서 `setStockQuantity()` 직접 호출 금지.

---

## 2. persist vs merge — save() 분기

```java
public void save(Item item) {
    if (item.getId() == null) {
        em.persist(item);   // 신규 등록
    } else {
        em.merge(item);     // 수정 (영속성 컨텍스트 밖에서 들어온 객체)
    }
}
```

- `id == null`: 처음 저장 → `persist`
- `id != null`: 이미 DB에 있는 데이터 → `merge`
- merge 상세 내용은 웹 계층에서 다룸

---

## 3. ItemService — 단순 위임

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ItemService {
    private final ItemRepository itemRepository;

    @Transactional
    public void saveItem(Item item) { itemRepository.save(item); }

    public List<Item> findItems() { return itemRepository.findAll(); }
    public Item findOne(Long itemId) { return itemRepository.findOne(itemId); }
}
```

상품 서비스는 단순 위임 수준 — 복잡한 비즈니스 로직은 엔티티 안에 있음.

---

## stylink 적용 포인트

**강의 권장 방식**: 도메인 모델 패턴 — 비즈니스 로직을 엔티티 안에 넣어 응집도 높임.

**stylink 적용**:
- `Inventory` 엔티티에 `hold()`, `release()`, `sell()` 같은 재고 상태 전환 메서드 직접 구현 — 외부 Service에서 `setStatus()`로 직접 세팅하는 방식은 지양
- `removeStock` 같은 검증 로직도 엔티티 안에 → 재고 부족 시 즉시 예외 던지는 방식 적용 가능
- `NotEnoughStockException`처럼 도메인별 커스텀 예외 분리 (`BusinessException` 상속 구조 이미 적용됨)
