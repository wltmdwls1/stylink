# 컬렉션 프레임워크 - Map, Stack, Queue

> 자바 중급2편 | DEEP

---

## 전체 흐름 한눈에 보기

```
Map — 키-값 쌍, 키 중복 불허
  → HashMap: 순서 없음, O(1)
  → LinkedHashMap: 삽입 순서 유지
  → TreeMap: 키 기준 정렬

Stack — LIFO (Last In First Out)
  → 레거시: java.util.Stack (Vector 기반)
  → 현대: Deque<T> (ArrayDeque 권장)

Queue — FIFO (First In First Out)
  → 인터페이스: Queue<T>
  → 구현체: ArrayDeque, LinkedList, PriorityQueue
```

---

## HashMap 기본 사용

```java
Map<String, Integer> map = new HashMap<>();
map.put("A", 1);           // 키-값 저장
map.put("B", 2);
map.put("A", 10);          // 키 중복 → 덮어씀 (값 교체)

map.get("A");              // 10 (없으면 null)
map.getOrDefault("C", 0); // 0 (없으면 기본값)
map.containsKey("B");     // true
map.containsValue(2);     // true
map.remove("B");          // 삭제
map.size();               // 1

// 순회
for (Map.Entry<String, Integer> entry : map.entrySet()) {
    System.out.println(entry.getKey() + " = " + entry.getValue());
}
map.forEach((k, v) -> System.out.println(k + " = " + v));

map.keySet();    // 키 Set
map.values();    // 값 Collection
map.entrySet();  // 키-값 쌍 Set
```

---

## HashMap 편리 메서드 (Java 8+)

```java
// putIfAbsent: 키가 없을 때만 저장
map.putIfAbsent("A", 999);  // 이미 있으면 무시

// computeIfAbsent: 없으면 계산해서 저장 (초기화 패턴에 유용)
Map<String, List<String>> groupMap = new HashMap<>();
groupMap.computeIfAbsent("KEY", k -> new ArrayList<>()).add("value");

// merge: 키가 있으면 함수 적용, 없으면 새 값 저장
map.merge("A", 1, Integer::sum);  // A가 있으면 기존값 + 1, 없으면 1

// 단어 빈도 카운트 패턴
Map<String, Integer> freq = new HashMap<>();
for (String word : words) {
    freq.merge(word, 1, Integer::sum);
}
```

---

## LinkedHashMap vs TreeMap

```java
// LinkedHashMap — 삽입 순서 유지
Map<String, Integer> linked = new LinkedHashMap<>();
linked.put("C", 3);
linked.put("A", 1);
linked.put("B", 2);
linked.forEach((k, v) -> System.out.print(k));  // "CAB" (삽입 순서)

// TreeMap — 키 기준 자연 정렬
Map<String, Integer> tree = new TreeMap<>();
tree.put("C", 3); tree.put("A", 1); tree.put("B", 2);
tree.forEach((k, v) -> System.out.print(k));  // "ABC" (정렬)

// TreeMap 전용 메서드
TreeMap<String, Integer> tmap = new TreeMap<>(map);
tmap.firstKey();             // 가장 작은 키
tmap.lastKey();              // 가장 큰 키
tmap.headMap("B");           // B 미만 키들의 서브맵
tmap.tailMap("B");           // B 이상 키들의 서브맵
tmap.subMap("A", "C");      // A이상 C미만
```

---

## Stack — LIFO

```java
// 레거시: java.util.Stack (Thread-safe하지만 느림)
Stack<String> stack = new Stack<>();
stack.push("A");
stack.push("B");
stack.peek();   // "B" (제거 안 함)
stack.pop();    // "B" (제거)

// 현대: ArrayDeque (권장)
Deque<String> stack = new ArrayDeque<>();
stack.push("A");    // addFirst
stack.push("B");
stack.peek();       // "B"
stack.pop();        // "B"   (removeFirst)
```

**Stack 활용**: 괄호 검사, 재귀→반복 변환, DFS, 뒤로가기 기능.

---

## Queue — FIFO

```java
// ArrayDeque (일반 큐로 사용)
Queue<String> queue = new ArrayDeque<>();
queue.offer("A");   // 뒤에 추가 (= addLast)
queue.offer("B");
queue.peek();       // "A" (앞 조회, 제거 안 함)
queue.poll();       // "A" (앞 제거)

// PriorityQueue (우선순위 큐 — 최소힙)
Queue<Integer> pq = new PriorityQueue<>();
pq.offer(3); pq.offer(1); pq.offer(2);
pq.poll();  // 1 (항상 가장 작은 값 먼저)

// 최대힙
Queue<Integer> maxPq = new PriorityQueue<>(Comparator.reverseOrder());
```

---

## 면접 Q&A

**Q: HashMap에서 키가 없을 때 put vs putIfAbsent의 차이는?**  
A: `put(key, value)`는 키가 있으면 덮어쓴다. `putIfAbsent(key, value)`는 키가 없을 때만 저장하고 기존 값이 있으면 무시한다. 초기값 설정이나 캐시 패턴에서 putIfAbsent가 유용하다.

**Q: Stack과 Queue를 어떤 클래스로 구현해야 하나?**  
A: Stack은 `java.util.Stack`(레거시) 대신 `ArrayDeque`를 `Deque<T>`로 선언해서 쓴다. Queue는 `ArrayDeque`를 `Queue<T>`로 선언. 두 클래스 모두 `ArrayDeque`가 내부적으로 배열을 사용해 LinkedList보다 성능이 좋고, 불필요한 동기화가 없다.
