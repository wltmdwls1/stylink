# 컬렉션 프레임워크 - LinkedList

> 자바 중급2편 | DEEP

---

## 전체 흐름 한눈에 보기

```
[이전 챕터] ArrayList: 배열 기반, 인덱스 접근 O(1), 중간 삽입 O(n)

LinkedList
  → 각 노드(Node)가 다음 노드를 참조 (포인터 연결)
  → 중간 삽입/삭제: O(1) (포인터만 변경)
  → 인덱스 접근: O(n) (처음부터 순차 탐색)

ArrayList vs LinkedList 비교
  → 중간 삽입이 많으면 LinkedList
  → 인덱스 접근이 많으면 ArrayList

→ 실무에서는 LinkedList 쓸 일이 거의 없음 (ArrayList가 대부분 유리)
```

---

## LinkedList 구조

```
노드(Node): 데이터 + 다음 노드 참조

[A | →] → [B | →] → [C | →] → [D | null]
 head                            tail
```

```java
// Node 내부 구조 (개념)
class Node<T> {
    T data;
    Node<T> next;  // 다음 노드 참조
}
```

---

## LinkedList의 장점 — 중간 삽입/삭제

배열처럼 요소를 밀거나 당길 필요 없이 포인터만 변경:

```
[A] → [B] → [C] → [D]

B와 C 사이에 X 삽입:
[A] → [B] → [X] → [C] → [D]
                ↑
     B.next = X, X.next = 기존 B.next (C)
```

삽입할 **위치를 찾는 것은 O(n)**, 위치를 안다면 실제 삽입은 O(1).

---

## LinkedList의 단점 — 인덱스 접근

배열과 달리 메모리가 연속적이지 않아 인덱스 접근을 할 수 없다.  
`get(5)` → 처음(head)부터 5번 따라가야 함 → O(n).

---

## ArrayList vs LinkedList 비교

| 연산 | ArrayList | LinkedList |
|---|---|---|
| `get(index)` | O(1) | O(n) |
| `add(last)` | O(1) 아모타이즈드 | O(1) (tail 직접 접근) |
| `add(index)` | O(n) | O(n) (위치 탐색) + O(1) (삽입) |
| `remove(index)` | O(n) | O(n) (위치 탐색) + O(1) (삭제) |
| 메모리 | 연속 → 캐시 효율 좋음 | 불연속 → 캐시 미스 많음 |

**결론**: "중간 삽입이 많으면 LinkedList가 낫다"고 하지만,  
실제로는 **위치 탐색에 O(n)**이 들어서 큰 차이가 없다.  
현대 CPU의 캐시 효율 때문에 ArrayList가 대부분의 경우 더 빠르다.

---

## Deque로서의 LinkedList

자바 `LinkedList`는 `List`이기도 하고 `Deque`이기도 하다:

```java
LinkedList<String> deque = new LinkedList<>();
deque.addFirst("A");   // 앞에 추가
deque.addLast("B");    // 뒤에 추가
deque.peekFirst();     // 앞 조회 (제거 안 함)
deque.peekLast();      // 뒤 조회 (제거 안 함)
deque.pollFirst();     // 앞 제거
deque.pollLast();      // 뒤 제거

// 스택처럼
deque.push("A");  // addFirst
deque.pop();      // removeFirst

// 큐처럼
deque.offer("A"); // addLast
deque.poll();     // removeFirst
```

---

## 실무에서의 선택

```java
// 일반 목록 → ArrayList 기본
List<Order> orders = new ArrayList<>();

// 스택/큐 용도 → ArrayDeque 권장 (LinkedList보다 빠름)
Deque<String> stack = new ArrayDeque<>();
Queue<String> queue = new ArrayDeque<>();

// LinkedList가 유리한 경우 — 거의 없음
// 이론상: 앞쪽 삽입/삭제가 매우 빈번한 경우
```

---

## 면접 Q&A

**Q: ArrayList와 LinkedList 어떤 걸 써야 하나?**  
A: 대부분의 경우 ArrayList를 쓴다. LinkedList는 중간 삽입/삭제가 O(1)이라고 하지만, 위치를 찾는 탐색이 O(n)이라 실제로는 차이가 없다. 또한 LinkedList는 노드마다 다음 포인터를 저장해야 해 메모리를 더 쓰고, 연속 메모리가 아니라 CPU 캐시 효율이 나빠 실제 벤치마크에서 ArrayList가 더 빠른 경우가 많다.

**Q: LinkedList가 실제로 유리한 경우는?**  
A: 맨 앞이나 맨 뒤에서만 추가/삭제하는 큐나 스택 용도. 하지만 이 경우도 `ArrayDeque`이 더 성능이 좋아 LinkedList보다 ArrayDeque을 권장한다.
