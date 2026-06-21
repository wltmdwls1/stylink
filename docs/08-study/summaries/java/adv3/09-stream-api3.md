# 스트림 API3 - 컬렉터

> 자바 고급3편 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터] 스트림 최종 연산: toList(), count(), sum() 등 단순한 것들

Collectors — 더 복잡한 수집 방법
  → toMap: 스트림 요소를 Map으로 수집
  → groupingBy: 기준에 따라 그룹핑
  → partitioningBy: true/false 두 그룹으로 분할
  → joining: 문자열 연결
  → 다운스트림 컬렉터: 그룹 안에서 추가 집계 (counting, summingInt 등)

→ SQL의 GROUP BY + 집계 함수를 자바로 표현하는 것
```

---

## toMap — 스트림을 Map으로 수집

```java
// 사용자 목록 → id-name 맵
Map<Long, String> idToName = users.stream()
    .collect(Collectors.toMap(
        User::getId,    // key
        User::getName   // value
    ));

// id-User 전체 맵
Map<Long, User> idToUser = users.stream()
    .collect(Collectors.toMap(User::getId, u -> u));
// 또는
    .collect(Collectors.toMap(User::getId, Function.identity()));
```

**중복 키 처리** (없으면 예외 발생):
```java
// 같은 이름이 있을 경우 어떻게 합칠지 지정
Map<String, Long> nameToId = users.stream()
    .collect(Collectors.toMap(
        User::getName,
        User::getId,
        (existingId, newId) -> existingId  // 기존 값 유지
    ));
```

---

## groupingBy — 그룹핑 (SQL GROUP BY)

```java
// 상태별로 주문 그룹핑
Map<OrderStatus, List<Order>> byStatus = orders.stream()
    .collect(Collectors.groupingBy(Order::getStatus));
// { PENDING: [주문1, 주문3], COMPLETED: [주문2], ... }

// 나이대별로 사용자 그룹핑
Map<Integer, List<User>> byAgeGroup = users.stream()
    .collect(Collectors.groupingBy(u -> u.getAge() / 10 * 10));
// { 20: [...], 30: [...], 40: [...] }
```

---

## 다운스트림 컬렉터 — 그룹 안에서 추가 집계

`groupingBy`의 두 번째 인자로 다운스트림 컬렉터를 지정하면 그룹별 집계가 가능하다.

```java
// 상태별 주문 개수 (SQL: SELECT status, COUNT(*) GROUP BY status)
Map<OrderStatus, Long> countByStatus = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getStatus,
        Collectors.counting()  // 각 그룹의 요소 수
    ));

// 카테고리별 상품 평균 가격
Map<Category, Double> avgPriceByCategory = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        Collectors.averagingInt(Product::getPrice)
    ));

// 상태별 주문 금액 합계
Map<OrderStatus, Integer> sumByStatus = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getStatus,
        Collectors.summingInt(Order::getAmount)
    ));

// 상태별 주문 번호 목록 (List가 아닌 Set으로)
Map<OrderStatus, Set<Long>> idsByStatus = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getStatus,
        Collectors.mapping(Order::getId, Collectors.toSet())
    ));
```

---

## partitioningBy — 두 그룹으로 분할

`groupingBy`와 같지만 Predicate(true/false)로 딱 두 그룹으로 나눈다.

```java
// 80점 이상 / 미만으로 분할
Map<Boolean, List<Student>> partition = students.stream()
    .collect(Collectors.partitioningBy(s -> s.getScore() >= 80));

List<Student> passed = partition.get(true);
List<Student> failed = partition.get(false);

// 성인/미성년자
Map<Boolean, Long> ageCount = users.stream()
    .collect(Collectors.partitioningBy(
        u -> u.getAge() >= 18,
        Collectors.counting()  // 다운스트림 컬렉터
    ));
```

---

## joining — 문자열 연결

```java
// 이름들을 쉼표로 연결
String names = users.stream()
    .map(User::getName)
    .collect(Collectors.joining(", "));
// "Alice, Bob, Charlie"

// 구분자 + 앞뒤 추가
String result = users.stream()
    .map(User::getName)
    .collect(Collectors.joining(", ", "[", "]"));
// "[Alice, Bob, Charlie]"
```

---

## summarizingInt — 통계 한 번에

```java
IntSummaryStatistics stats = orders.stream()
    .collect(Collectors.summarizingInt(Order::getAmount));

stats.getCount();  // 개수
stats.getSum();    // 합계
stats.getMin();    // 최솟값
stats.getMax();    // 최댓값
stats.getAverage(); // 평균
```

---

## 면접 Q&A

**Q: groupingBy와 partitioningBy의 차이는?**  
A: `groupingBy`는 분류 함수 결과에 따라 여러 그룹으로 나눈다. `partitioningBy`는 Predicate(boolean)에 따라 true/false 두 그룹으로만 나눈다. 딱 두 그룹이 필요할 때는 `partitioningBy`가 더 명확하다.

**Q: 다운스트림 컬렉터란?**  
A: `groupingBy`의 두 번째 인자로, 각 그룹 안에서 추가로 적용할 컬렉터다. 예를 들어 `groupingBy(status, counting())`은 상태별로 그룹핑하고, 각 그룹의 요소 수를 집계한다. SQL의 `SELECT status, COUNT(*) GROUP BY status`와 같은 표현이다.

**Q: toMap에서 중복 키가 있을 때 어떻게 처리하나?**  
A: 세 번째 인자로 merge 함수를 지정한다. `(existing, incoming) -> existing`이면 기존 값 유지, `(a, b) -> b`이면 새 값으로 덮어쓰기. 지정하지 않으면 `IllegalStateException`이 발생한다.

## stylink 실전 적용

```java
// 카테고리별 재고 수 집계
Map<Category, Long> availableByCategory = inventoryItems.stream()
    .filter(i -> i.getStatus() == InventoryStatus.AVAILABLE)
    .collect(Collectors.groupingBy(
        InventoryItem::getCategory,
        Collectors.counting()
    ));

// 예약 상태별 예약 목록
Map<ReservationStatus, List<Reservation>> byStatus = reservations.stream()
    .collect(Collectors.groupingBy(Reservation::getStatus));

// 상품 ID → 상품 조회용 맵 (N+1 해결)
Map<Long, Product> productMap = products.stream()
    .collect(Collectors.toMap(Product::getId, Function.identity()));
```
