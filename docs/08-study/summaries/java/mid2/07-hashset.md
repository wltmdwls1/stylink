# 컬렉션 프레임워크 - HashSet

> 자바 중급2편 | DEEP

---

## 전체 흐름 한눈에 보기

```
Set — 중복 없는 컬렉션
  → 주요 특성: 중복 불허, 순서 미보장, O(1) 추가/조회/삭제

HashSet 내부 구현
  → HashMap에 값을 키로, 더미 값(PRESENT)을 value로 저장
  → hashCode() + equals()로 중복 판단

중복 판단 흐름:
  1. hashCode() → 버킷 위치 계산
  2. 같은 버킷에 있는 요소들과 equals() 비교
  3. 둘 다 같으면 중복 → 저장 안 함

hashCode() / equals() 오버라이드 필요성
  → 커스텀 객체를 HashSet에 넣을 때 반드시 필요
```

---

## HashSet 기본 사용

```java
Set<String> set = new HashSet<>();
set.add("A");
set.add("B");
set.add("A");  // 중복 → 무시됨
System.out.println(set.size());  // 2
System.out.println(set);  // [B, A] (순서 미보장!)

set.contains("A");  // true O(1)
set.remove("B");    // true O(1)
set.isEmpty();      // false
```

---

## 중복 판단 메커니즘

```java
// HashSet이 중복을 판단하는 방법:
// 1. 새 요소의 hashCode() 계산 → 버킷 찾기
// 2. 해당 버킷의 기존 요소들과 equals() 비교
// 3. equals() true인 요소가 있으면 → 중복, 저장 안 함
```

**커스텀 객체에서 주의:**
```java
public class User {
    private Long id;
    private String name;

    // equals, hashCode 오버라이드 안 하면:
}

Set<User> users = new HashSet<>();
users.add(new User(1L, "김철수"));
users.add(new User(1L, "김철수"));  // 다른 객체 → 중복 허용!? (의도와 다름)
System.out.println(users.size());  // 2 (기대: 1)
```

해결:
```java
@Override
public boolean equals(Object o) {
    if (!(o instanceof User other)) return false;
    return Objects.equals(id, other.id);
}

@Override
public int hashCode() {
    return Objects.hash(id);
}
// 이제 id가 같으면 중복으로 처리
```

---

## HashSet 내부 구현

자바 HashSet은 내부적으로 HashMap을 사용한다:

```java
// HashSet 내부 (개념적)
public class HashSet<E> {
    private HashMap<E, Object> map;
    private static final Object PRESENT = new Object();  // 더미 값

    public boolean add(E e) {
        return map.put(e, PRESENT) == null;  // 키로 저장
    }

    public boolean contains(Object o) {
        return map.containsKey(o);  // 키 조회
    }
}
```

---

## Set 성능 특성

| 연산 | HashSet | TreeSet | LinkedHashSet |
|---|---|---|---|
| add/contains/remove | O(1) 평균 | O(log n) | O(1) 평균 |
| 순서 | 없음 | 정렬 순서 | 삽입 순서 |
| null 허용 | 1개 | 불가 | 1개 |

---

## 집합 연산

```java
Set<String> set1 = new HashSet<>(Set.of("A", "B", "C"));
Set<String> set2 = new HashSet<>(Set.of("B", "C", "D"));

// 합집합 (A, B, C, D)
Set<String> union = new HashSet<>(set1);
union.addAll(set2);

// 교집합 (B, C)
Set<String> intersection = new HashSet<>(set1);
intersection.retainAll(set2);

// 차집합 set1 - set2 (A)
Set<String> difference = new HashSet<>(set1);
difference.removeAll(set2);
```

---

## 실전 활용 — 중복 제거

```java
// 중복 ID 제거
List<Long> orderIds = List.of(1L, 2L, 1L, 3L, 2L, 4L);
Set<Long> uniqueIds = new HashSet<>(orderIds);
// {1, 2, 3, 4}

// 특정 값이 이미 처리됐는지 빠른 확인
Set<Long> processedIds = new HashSet<>();
for (Order order : orders) {
    if (!processedIds.contains(order.getId())) {
        process(order);
        processedIds.add(order.getId());
    }
}
```

---

## 면접 Q&A

**Q: HashSet에 커스텀 객체를 넣을 때 hashCode/equals를 오버라이드해야 하는 이유는?**  
A: HashSet은 중복 판단에 hashCode()와 equals()를 사용한다. 오버라이드하지 않으면 Object의 기본 구현(참조값 비교)을 사용해 내용이 같아도 다른 객체이면 중복이 허용된다. id가 같은 User를 두 개 넣으면 두 개가 모두 저장되는 버그가 생긴다.

**Q: HashSet이 내부적으로 HashMap을 사용하는 이유는?**  
A: HashMap은 키의 중복을 허용하지 않고 O(1) 조회를 제공한다. HashSet은 저장하려는 요소를 HashMap의 키로 쓰고 더미 값(PRESENT)을 value로 저장한다. 이렇게 하면 HashSet의 핵심 기능(중복 불허, O(1) 접근)을 HashMap 위에서 재구현할 수 있다.
