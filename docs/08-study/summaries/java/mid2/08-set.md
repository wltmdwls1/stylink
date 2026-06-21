# 컬렉션 프레임워크 - Set

> 자바 중급2편 | DEEP

---

## 전체 흐름 한눈에 보기

```
Set 구현체 3종류
  → HashSet: 순서 없음, O(1) 속도
  → LinkedHashSet: 삽입 순서 유지, O(1) 속도
  → TreeSet: 정렬 순서, O(log n) 속도

언제 어떤 걸 쓰나?
  → 단순 중복 제거, 빠른 contains: HashSet
  → 삽입 순서도 필요: LinkedHashSet
  → 정렬된 순서로 순회: TreeSet

Set vs List 선택 기준
  → 중복 제거가 목적이고 순서 불필요: Set
  → 순서/인덱스 필요: List
```

---

## HashSet vs LinkedHashSet vs TreeSet

**HashSet** — 순서 없음, 가장 빠름:
```java
Set<String> hashSet = new HashSet<>(Set.of("C", "A", "B"));
hashSet.forEach(System.out::print);  // 순서 불정: BCA 또는 ACB 등
```

**LinkedHashSet** — 삽입 순서 유지:
```java
Set<String> linkedSet = new LinkedHashSet<>();
linkedSet.add("C");
linkedSet.add("A");
linkedSet.add("B");
linkedSet.forEach(System.out::print);  // "CAB" (삽입 순서)
```

**TreeSet** — 자연 정렬 순서 (Comparable 필요):
```java
Set<String> treeSet = new TreeSet<>(Set.of("C", "A", "B"));
treeSet.forEach(System.out::print);  // "ABC" (알파벳 순)

// TreeSet만의 기능
treeSet.first();           // "A" (가장 작은 요소)
treeSet.last();            // "C" (가장 큰 요소)
treeSet.headSet("B");      // ["A"] (B 미만)
treeSet.tailSet("B");      // ["B", "C"] (B 이상)
treeSet.subSet("A", "C"); // ["A", "B"] (A이상 C미만)
```

---

## TreeSet에서 커스텀 정렬

```java
// 방법1: 객체가 Comparable 구현
public class User implements Comparable<User> {
    private String name;
    @Override
    public int compareTo(User other) {
        return this.name.compareTo(other.name);
    }
}
Set<User> users = new TreeSet<>();  // name 순으로 정렬

// 방법2: TreeSet에 Comparator 전달
Set<User> users = new TreeSet<>(Comparator.comparing(User::getAge));
```

---

## 성능 비교 요약

| | HashSet | LinkedHashSet | TreeSet |
|---|---|---|---|
| add | O(1) | O(1) | O(log n) |
| contains | O(1) | O(1) | O(log n) |
| remove | O(1) | O(1) | O(log n) |
| 순서 | 없음 | 삽입 순서 | 정렬 순서 |
| 메모리 | 가장 적음 | 약간 더 | 약간 더 |

---

## 실전 선택 가이드

```java
// 단순 중복 제거, 빠른 조회
Set<Long> processedIds = new HashSet<>();

// 처리 순서를 기록하면서 중복 제거
Set<Long> orderedIds = new LinkedHashSet<>();

// 정렬된 범위 조회 (예: 특정 날짜 범위의 ID들)
TreeSet<LocalDate> dates = new TreeSet<>();
dates.subSet(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1));

// 멀티스레드 환경
Set<String> concurrentSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
```

---

## 면접 Q&A

**Q: HashSet, LinkedHashSet, TreeSet 차이는?**  
A: HashSet은 순서가 없고 O(1) 속도. LinkedHashSet은 삽입 순서를 유지하고 O(1). TreeSet은 자연 정렬 순서를 유지하고 O(log n). 단순 중복 제거는 HashSet, 순서도 필요하면 LinkedHashSet, 정렬된 순회나 범위 조회가 필요하면 TreeSet을 사용한다.

**Q: TreeSet에 커스텀 객체를 넣으려면?**  
A: 두 가지 방법이 있다. (1) 객체 클래스가 `Comparable<T>`를 구현해 `compareTo()` 메서드를 제공한다. (2) TreeSet 생성 시 `Comparator`를 전달한다. 둘 다 없으면 `ClassCastException`이 발생한다.
