# 스트림 API1 - 기본

> 자바 고급3편 | 20페이지 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터] 람다, 함수형 인터페이스 학습
  → filter/map을 직접 구현한 MyStreamV1 ~ V3 만들어봄

자바 스트림 API 등장
  → 우리가 만든 것과 구조는 같지만 훨씬 많은 기능과 최적화가 담겨있음
  → 선언형 프로그래밍: "어떻게(How)"가 아닌 "무엇(What)"에 집중

스트림 기본 구조
  → [생성] → [중간 연산] → [최종 연산]

외부 반복 → 내부 반복 (forEach)
  → 스트림이 반복을 직접 제어 → 병렬 처리 전환이 쉬워짐

지연 연산 (Lazy Evaluation)
  → 중간 연산은 최종 연산 호출 전까지 실행 안 됨 → 불필요한 처리 줄임
  
[다음 챕터] 스트림 API2 — 더 많은 중간/최종 연산, 수집(Collectors)
```

---

## 스트림이 나온 이유

점수 80점 이상인 학생의 이름만 대문자로 뽑아서 리스트로 만들고 싶다고 해보자.

**기존 방식 (명령형)**
```java
List<String> result = new ArrayList<>();
for (Student s : students) {
    if (s.getScore() >= 80) {
        result.add(s.getName().toUpperCase());
    }
}
```
어떻게(How) 돌아가는지는 명확하지만, "80점 이상 → 이름 → 대문자"라는 의도를 파악하려면 코드를 한 줄씩 읽어야 한다.

**스트림 방식 (선언형)**
```java
List<String> result = students.stream()
        .filter(s -> s.getScore() >= 80)
        .map(s -> s.getName().toUpperCase())
        .toList();
```
코드를 읽으면 "80점 이상 → 이름 → 대문자 → 리스트"가 한눈에 들어온다. **무엇을 원하는지**에 집중하는 방식. 이를 **선언적 프로그래밍**이라 한다.

이 구조는 이전 챕터에서 직접 만들어본 `MyStreamV3`와 같은 아이디어다.  
자바 스트림 API는 거기에 지연 연산, 병렬 처리, 다양한 종단 연산 등을 더한 완성형이다.

---

## 스트림의 3단계 구조

```
[1. 생성] → [2. 중간 연산 (여러 개 가능)] → [3. 최종 연산]
```

### 1단계: 스트림 생성 (Source)
```java
names.stream()            // List, Set 등 컬렉션
Arrays.stream(arr)        // 배열
Stream.of("a", "b", "c") // 직접 값을 나열
```

### 2단계: 중간 연산 (Intermediate Operations)
데이터를 걸러내거나 변환한다. **스트림을 반환**하기 때문에 여러 개를 이어붙일 수 있다.
```java
.filter(name -> name.startsWith("B"))  // 조건 충족하는 요소만 통과
.map(s -> s.toUpperCase())             // 각 요소를 다른 값으로 변환
.distinct()                            // 중복 제거
.sorted()                              // 정렬 (기본: 자연 정렬)
.limit(10)                             // 앞에서 최대 10개만
.skip(5)                               // 앞에서 5개 건너뛰기
```

### 3단계: 최종 연산 (Terminal Operations)
스트림을 소비하고 결과를 만들어낸다. **한 번만 호출 가능, 이후 스트림 재사용 불가.**
```java
.toList()                              // List로 수집
.collect(Collectors.toSet())           // Set으로 수집
.forEach(s -> System.out.println(s))   // 각 요소에 동작 실행
.count()                               // 요소 개수
.findFirst()                           // 첫 번째 요소 (Optional 반환)
.anyMatch(s -> s.length() > 5)         // 하나라도 조건 충족하면 true
.allMatch(s -> s.length() > 0)         // 모두 조건 충족하면 true
.min() / .max()                        // 최솟값 / 최댓값
```

---

## 외부 반복 → 내부 반복

스트림 이전에는 개발자가 직접 `for`문으로 반복을 제어했다 — **외부 반복**.  
스트림의 `forEach()`는 반복 자체를 스트림에 위임한다 — **내부 반복**.

```java
// 외부 반복 - 내가 직접 루프를 제어
for (String s : result) {
    System.out.println(s);
}

// 내부 반복 - 스트림이 알아서 반복
stream.forEach(s -> System.out.println(s));

// 메서드 참조로 더 간결하게
stream.forEach(System.out::println);
```

**내부 반복의 실질적 장점**: 나중에 배울 병렬 처리에서 나온다.  
`stream()` → `parallelStream()`으로만 바꿔도 멀티코어를 활용하는 병렬 처리로 전환된다. 외부 반복이었다면 코드를 훨씬 많이 바꿔야 했을 것이다.

---

## 메서드 참조 (Method Reference)

람다에서 단순히 특정 메서드를 호출하기만 하는 경우, 메서드 참조로 더 간결하게 쓸 수 있다.

```java
// 람다                                   → 메서드 참조
.map(s -> s.toUpperCase())               → .map(String::toUpperCase)
.forEach(s -> System.out.println(s))     → .forEach(System.out::println)
.map(s -> new Order(s))                  → .map(Order::new)
.map(s -> Integer.parseInt(s))           → .map(Integer::parseInt)
```

형태별 구분:
- `String::toUpperCase` — **임의 객체의 인스턴스 메서드** (스트림의 각 요소가 this가 됨)
- `System.out::println` — **특정 객체의 인스턴스 메서드** (`System.out`이 고정)
- `Order::new` — **생성자 참조**
- `Integer::parseInt` — **정적 메서드 참조**

---

## 지연 연산 (Lazy Evaluation) — 핵심 동작 원리

중간 연산은 **최종 연산이 호출되기 전까지 아무 것도 실행하지 않는다.**

```java
Stream<String> stream = names.stream()
    .filter(name -> {
        System.out.println("filter 실행: " + name);  // 언제 실행될까?
        return name.startsWith("B");
    })
    .map(s -> {
        System.out.println("map 실행: " + s);        // 언제 실행될까?
        return s.toUpperCase();
    });

// 여기까지 실행해도 위의 println은 단 한 줄도 출력되지 않음

List<String> result = stream.toList();  // 이 시점에 비로소 filter/map이 실행됨
```

이 덕분에 불필요한 처리를 줄일 수 있다.  
예를 들어 100만 명 중 첫 번째 80점 이상 학생만 찾는다면, `limit(1)` 또는 `findFirst()`와 조합하면 조건 충족 순간 나머지는 처리하지 않고 즉시 멈춘다.

```java
// 100만 명 중 80점 이상인 첫 번째 학생 이름
Optional<String> first = students.stream()
        .filter(s -> s.getScore() >= 80)
        .map(Student::getName)
        .findFirst();   // 첫 번째 충족 요소 발견 즉시 중단, 나머지 처리 안 함
```

---

## 스트림 사용 시 주의할 것

**스트림은 한 번만 사용할 수 있다.**
```java
Stream<String> stream = names.stream().filter(name -> name.startsWith("B"));
List<String> list1 = stream.toList();
List<String> list2 = stream.toList();  // ← IllegalStateException! 이미 닫힌 스트림
```
재사용이 필요하면 `names.stream()`을 다시 호출해 새 스트림을 만들어야 한다.

**람다 안에서 캡처한 지역 변수는 effectively final이어야 한다.**
```java
String prefix = "B";
names.stream().filter(name -> name.startsWith(prefix));  // 가능 (변경 안 했으니)
prefix = "C";  // 컴파일 에러. 람다 안에서 참조한 변수를 나중에 변경할 수 없음
```

---

## 면접 Q&A

**Q: 스트림과 컬렉션의 차이는?**  
A: 컬렉션은 데이터를 저장하는 자료구조이고, 스트림은 데이터를 처리하는 파이프라인이다. 컬렉션은 여러 번 순회할 수 있지만, 스트림은 최종 연산을 한 번 호출하면 소비되어 재사용이 불가능하다.

**Q: 스트림의 지연 연산이란?**  
A: 중간 연산(`filter`, `map` 등)은 최종 연산(`toList`, `forEach` 등)이 호출되기 전까지 실제로 실행되지 않는다. `findFirst()`나 `limit()`과 함께 쓰면 필요한 요소만 처리하고 나머지는 건너뛰는 최적화가 자동으로 이루어진다.

**Q: forEach와 for문 중 언제 뭘 써야 하나?**  
A: 필터링, 변환, 집계 등 데이터 파이프라인 표현에는 스트림이 가독성이 좋다. 반면 인덱스가 필요하거나 중간에 `break`/`continue`가 필요한 경우, 또는 단순 순회라면 for문이 더 적합하다.

**Q: `parallelStream()`은 항상 빠른가?**  
A: 아니다. 요소가 많고 각 연산이 독립적이고 CPU를 많이 쓸 때 유리하다. 요소가 적거나 IO 작업이 섞여있거나 순서에 의존하는 작업에서는 스레드 생성·병합 오버헤드 때문에 오히려 느릴 수 있다.

**Q: 스트림이 I/O 스트림과 다른 점은?**  
A: 이름만 같고 전혀 다른 개념이다. `java.io.InputStream/OutputStream`은 파일·네트워크 등 데이터 입출력을 위한 것이고, `java.util.stream.Stream`은 컬렉션 데이터를 선언적으로 처리하기 위한 것이다.

---

## stylink 실전 적용

### OrderService - 주문 항목 총액 계산
```java
// 취소되지 않은 항목만 합산
int totalPrice = orderItems.stream()
        .filter(item -> item.getStatus() != OrderItemStatus.CANCELLED)
        .mapToInt(item -> item.getPrice() * item.getQuantity())
        .sum();
```

### ReservationService - 특정 날짜 확정 예약 목록 조회
```java
List<ReservationResponse> confirmed = reservations.stream()
        .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
        .filter(r -> r.getScheduledDate().equals(targetDate))
        .map(ReservationResponse::from)
        .toList();
```

### InventoryService - 카테고리별 가용 재고 수 집계
```java
Map<Category, Long> availableByCategory = inventoryItems.stream()
        .filter(item -> item.getStatus() == InventoryStatus.AVAILABLE)
        .collect(Collectors.groupingBy(InventoryItem::getCategory, Collectors.counting()));
```
