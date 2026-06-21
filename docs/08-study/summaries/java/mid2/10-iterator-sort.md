# 컬렉션 프레임워크 - 순회와 정렬

> 자바 중급2편 | DEEP

---

## 전체 흐름 한눈에 보기

```
순회 방법
  → 인덱스 for: List에만 적용 가능
  → for-each: Iterable을 구현한 컬렉션 모두 (내부적으로 iterator)
  → Iterator: 순회 중 삭제 시 필수
  → Stream: 함수형 체인 처리

정렬
  → Comparable: 객체 자체의 "자연 순서" 정의
  → Comparator: 정렬 기준을 외부에서 전달 (람다로 간결하게)
  → Comparator.comparing / thenComparing: 다중 정렬 기준

Collections.sort vs List.sort vs Stream.sorted
  → 셋 다 TimSort(합병정렬 + 삽입정렬) 사용 → O(n log n)
```

---

## 순회 방법 비교

```java
List<String> list = List.of("A", "B", "C");

// 방법1: 인덱스 for (List에만)
for (int i = 0; i < list.size(); i++) {
    System.out.println(list.get(i));
}

// 방법2: for-each (모든 Iterable)
for (String s : list) {
    System.out.println(s);
}

// 방법3: iterator (순회 중 삭제 필요 시)
Iterator<String> it = new ArrayList<>(list).iterator();
while (it.hasNext()) {
    String s = it.next();
    if ("B".equals(s)) it.remove();  // 안전하게 삭제
}

// 방법4: forEach + 람다
list.forEach(System.out::println);

// 방법5: Stream
list.stream().filter(s -> !s.equals("B")).forEach(System.out::println);
```

---

## Comparable — 자연 순서 정의

객체 자체에 "기본 정렬 기준"을 심는다:

```java
public class Product implements Comparable<Product> {
    private String name;
    private int price;

    @Override
    public int compareTo(Product other) {
        return Integer.compare(this.price, other.price);  // 가격 오름차순
    }
}

List<Product> products = new ArrayList<>(/* ... */);
Collections.sort(products);  // Comparable 기준 정렬
```

`compareTo` 반환값:
- 음수: `this < other` (this가 앞)
- 0: 동등
- 양수: `this > other` (other가 앞)

---

## Comparator — 외부 정렬 기준

정렬 기준을 외부에서 주입:

```java
List<Product> products = new ArrayList<>(/* ... */);

// 방법1: 익명 클래스 (레거시)
products.sort(new Comparator<Product>() {
    @Override
    public int compare(Product a, Product b) {
        return Integer.compare(a.getPrice(), b.getPrice());
    }
});

// 방법2: 람다 (현대적)
products.sort((a, b) -> Integer.compare(a.getPrice(), b.getPrice()));

// 방법3: Comparator.comparing (가장 간결)
products.sort(Comparator.comparing(Product::getPrice));

// 역순
products.sort(Comparator.comparing(Product::getPrice).reversed());
```

---

## 다중 정렬 기준 — thenComparing

```java
// 가격 오름차순 → 같으면 이름 오름차순
products.sort(Comparator
    .comparing(Product::getPrice)
    .thenComparing(Product::getName));

// 카테고리 정렬 → 카테고리 같으면 가격 역순 → 가격 같으면 이름 순
orders.sort(Comparator
    .comparing(Order::getCategory)
    .thenComparing(Comparator.comparing(Order::getPrice).reversed())
    .thenComparing(Order::getName));
```

---

## Collections.sort vs List.sort vs Stream.sorted

```java
// 셋 다 TimSort (O(n log n), 안정 정렬)

// Collections.sort — 정적 메서드
Collections.sort(list);
Collections.sort(list, comparator);

// List.sort — 인스턴스 메서드 (Java 8+, 더 선호)
list.sort(null);          // null → Comparable 사용
list.sort(comparator);

// Stream.sorted — 원본 불변, 새 스트림 반환
List<Product> sorted = products.stream()
    .sorted(Comparator.comparing(Product::getPrice))
    .toList();
```

---

## stylink 실전 적용

```java
// 재고 목록을 가격순 정렬
inventories.sort(Comparator.comparing(Inventory::getPrice));

// 예약 목록: 날짜 오름차순 → 같으면 생성시간 오름차순
reservations.sort(Comparator
    .comparing(Reservation::getReservationDate)
    .thenComparing(Reservation::getCreatedAt));

// 주문 상태별 그룹화 (Map 활용)
Map<OrderStatus, List<Order>> ordersByStatus = orders.stream()
    .collect(Collectors.groupingBy(Order::getStatus));
```

---

## 면접 Q&A

**Q: Comparable과 Comparator의 차이는?**  
A: `Comparable`은 객체 자체에 기본 정렬 기준을 심는 인터페이스. `compareTo()`를 구현한다. `Comparator`는 정렬 기준을 외부에서 제공하는 인터페이스. 정렬 시 별도 전달한다. 하나의 객체에 여러 정렬 기준이 필요하거나 외부 라이브러리 클래스를 정렬할 때 Comparator를 쓴다.

**Q: 순회 중 삭제를 for-each 안에서 하면 왜 예외가 발생하나?**  
A: ArrayList 내부의 `modCount`가 수정 시 증가한다. for-each가 사용하는 iterator는 순회 시작 시 modCount를 기억하고, `next()` 호출마다 현재 modCount와 비교해 변경을 감지하면 `ConcurrentModificationException`을 던진다. `iterator.remove()`는 iterator 내부 modCount를 동기화해 이 문제를 우회한다.
