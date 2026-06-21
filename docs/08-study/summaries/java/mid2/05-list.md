# 컬렉션 프레임워크 - List

> 자바 중급2편 | DEEP

---

## 전체 흐름 한눈에 보기

```
List 인터페이스 — 순서가 있고 중복을 허용하는 컬렉션

주요 구현체
  → ArrayList: 배열 기반, 인덱스 O(1)
  → LinkedList: 연결 리스트, 앞뒤 O(1)
  → Vector: ArrayList의 동기화 버전 (레거시)
  → Stack: Vector 기반 (레거시 → ArrayDeque 사용 권장)

불변 리스트 vs 가변 리스트
  → List.of(): 완전 불변 (추가/삭제/변경 모두 불가)
  → Collections.unmodifiableList(): 뷰만 불변 (원본 변경 시 뷰도 변경됨)
  → new ArrayList<>(List.of(...)): 완전 가변

정렬, 검색, 복사 유틸리티 (Collections 클래스)
```

---

## List 인터페이스 주요 메서드

```java
List<String> list = new ArrayList<>(Arrays.asList("B", "A", "C"));

list.add("D");              // 마지막 추가
list.add(1, "X");           // 인덱스 삽입
list.get(0);                // 인덱스 접근
list.set(0, "Z");           // 인덱스 교체
list.remove(0);             // 인덱스 삭제
list.remove("A");           // 값으로 삭제 (첫 번째만)
list.size();                // 크기
list.isEmpty();             // 비어있는지
list.contains("B");         // 포함 여부
list.indexOf("B");          // 첫 번째 인덱스 (없으면 -1)
list.subList(1, 3);         // 부분 리스트 뷰 [1, 3)
list.toArray();             // 배열로 변환
list.clear();               // 전체 삭제
```

---

## 불변 vs 가변 리스트

```java
// 완전 불변 — Java 9+
List<String> immutable = List.of("A", "B", "C");
immutable.add("D");   // UnsupportedOperationException!
immutable.set(0, "X"); // UnsupportedOperationException!
// null도 허용 안 함

// 뷰 불변 (원본은 가변)
List<String> original = new ArrayList<>(Arrays.asList("A", "B"));
List<String> view = Collections.unmodifiableList(original);
view.add("C");      // UnsupportedOperationException!
original.add("C");  // 가능 → view.get(2)도 "C"가 됨 (원본 변경 반영됨)

// 불변 리스트를 가변으로 만들기 (방어적 복사)
List<String> mutable = new ArrayList<>(List.of("A", "B", "C"));
mutable.add("D");   // 가능
```

---

## Collections 유틸리티

```java
List<Integer> list = new ArrayList<>(Arrays.asList(3, 1, 4, 1, 5));

Collections.sort(list);                    // 자연 정렬 (오름차순)
Collections.sort(list, Comparator.reverseOrder()); // 내림차순

Collections.reverse(list);                // 역순
Collections.shuffle(list);                // 랜덤 섞기
Collections.min(list);                    // 최솟값
Collections.max(list);                    // 최댓값
Collections.frequency(list, 1);           // 특정 값 등장 횟수

Collections.fill(list, 0);               // 모두 0으로
Collections.copy(dest, src);             // src → dest 복사 (dest 크기가 src 이상이어야 함)

// 동기화 래핑 (멀티스레드 — ConcurrentHashMap 등 전용 클래스 권장)
List<String> syncList = Collections.synchronizedList(list);
```

---

## 배열 ↔ 리스트 변환

```java
// 배열 → 리스트 (가변 ArrayList)
String[] arr = {"A", "B", "C"};
List<String> list1 = new ArrayList<>(Arrays.asList(arr));

// 배열 → 리스트 (불변)
List<String> list2 = List.of(arr);

// 리스트 → 배열
String[] arr2 = list1.toArray(new String[0]);
// new String[0]은 타입 힌트용, 실제 크기는 list가 결정
```

---

## for-each와 iterator

```java
// for-each (내부적으로 iterator 사용)
for (String s : list) {
    System.out.println(s);
}

// iterator — 순회 중 삭제할 때 필요
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String s = it.next();
    if (s.equals("A")) {
        it.remove();  // ConcurrentModificationException 없이 안전하게 삭제
    }
}
// list.remove("A")를 for-each 안에서 직접 하면 ConcurrentModificationException!
```

---

## 면접 Q&A

**Q: List.of()와 Arrays.asList()의 차이는?**  
A: `List.of()`는 완전 불변으로 추가/삭제/변경 모두 불가하고 null도 허용하지 않는다. `Arrays.asList()`는 크기는 고정이지만 `set()`으로 값 변경은 가능하다. 또한 Arrays.asList는 원본 배열과 연결되어 있어 배열 변경이 리스트에도 반영된다.

**Q: for-each 안에서 요소를 삭제하면 왜 ConcurrentModificationException이 발생하나?**  
A: ArrayList는 수정 횟수를 `modCount`로 추적한다. for-each(iterator)는 순회 시작 시 modCount를 기억하고 매 `next()` 호출마다 현재 modCount와 비교한다. `list.remove()`로 직접 삭제하면 modCount가 달라져 예외가 발생한다. `iterator.remove()`를 쓰면 iterator가 내부 modCount를 동기화하므로 안전하다.
