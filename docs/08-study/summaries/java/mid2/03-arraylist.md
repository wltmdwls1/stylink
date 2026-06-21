# 컬렉션 프레임워크 - ArrayList

> 자바 중급2편 | DEEP

---

## 전체 흐름 한눈에 보기

```
배열의 한계
  → 크기가 고정됨 → 동적 추가/삭제 불편

직접 구현으로 ArrayList 원리 이해
  → MyArrayList: 배열 기반, 꽉 차면 2배 확장 (grow)
  → 인덱스 삽입: 뒤의 요소들을 한 칸씩 밀기 → O(n)
  → 인덱스 삭제: 뒤의 요소들을 한 칸씩 당기기 → O(n)

자바 ArrayList 사용
  → 내부적으로 Object[] 사용, 초기 capacity = 10

성능 특성
  → 인덱스 접근: O(1)
  → 마지막 추가: 보통 O(1), 확장 시 O(n)
  → 중간 삽입/삭제: O(n) (시프트 필요)
```

---

## 배열의 한계

```java
int[] arr = new int[5];  // 크기 고정
arr[5] = 6;  // ArrayIndexOutOfBoundsException!

// 더 큰 배열이 필요하면? 직접 복사해야 함
int[] newArr = new int[10];
System.arraycopy(arr, 0, newArr, 0, arr.length);
arr = newArr;  // 번거롭고 실수하기 쉬움
```

---

## ArrayList 내부 동작 원리

**핵심 구조:**
```java
// ArrayList 내부 (개념적)
private Object[] elementData;  // 실제 데이터 저장
private int size;              // 현재 저장된 요소 수
// capacity: elementData.length (할당된 배열 크기)
```

**add(value) — 마지막에 추가:**
```java
// capacity가 꽉 찼으면 2배 크기로 새 배열 생성 후 복사 (grow)
if (size == elementData.length) {
    elementData = Arrays.copyOf(elementData, elementData.length * 2);
}
elementData[size++] = value;  // O(1), 가끔 grow 시 O(n)
```

**add(index, value) — 중간 삽입:**
```java
// index 위치부터 뒤의 요소들을 한 칸씩 오른쪽으로 밀기 → O(n)
System.arraycopy(elementData, index, elementData, index + 1, size - index);
elementData[index] = value;
size++;
```

**remove(index) — 중간 삭제:**
```java
// index + 1부터 뒤의 요소들을 한 칸씩 왼쪽으로 당기기 → O(n)
System.arraycopy(elementData, index + 1, elementData, index, size - index - 1);
elementData[--size] = null;  // GC를 위해 null 처리
```

---

## 성능 특성 요약

| 연산 | 시간복잡도 | 이유 |
|---|---|---|
| `get(index)` | O(1) | 배열 인덱스 직접 접근 |
| `add(last)` | O(1) 아모타이즈드 | 보통은 O(1), 확장 시 O(n) |
| `add(index)` | O(n) | 뒤 요소 전부 시프트 |
| `remove(index)` | O(n) | 뒤 요소 전부 시프트 |
| `contains(value)` | O(n) | 순차 탐색 |

**적합한 상황:**
- 인덱스로 자주 접근
- 끝에서만 추가/삭제
- 데이터 수가 많지 않고 중간 삽입 드문 경우

**부적합한 상황:**
- 자주 중간에 삽입/삭제 → LinkedList 고려

---

## 실제 ArrayList 사용

```java
List<String> list = new ArrayList<>();       // 기본 capacity 10
List<String> list = new ArrayList<>(100);    // 예상 크기 미리 지정 (grow 최소화)

list.add("A");
list.add(0, "B");         // 인덱스 삽입 (느림)
list.get(0);              // "B" (빠름)
list.set(0, "C");         // 인덱스 교체 (빠름)
list.remove(0);           // 인덱스 삭제 (느림)
list.remove("C");         // 값으로 삭제 (느림, 탐색 + 시프트)
list.size();              // 현재 요소 수
list.contains("A");       // 포함 여부 (O(n))
list.indexOf("A");        // 첫 번째 인덱스 (-1이면 없음)
Collections.sort(list);   // 정렬
```

---

## 면접 Q&A

**Q: ArrayList의 내부 구현은?**  
A: 내부적으로 `Object[]` 배열을 사용한다. 요소가 꽉 차면 현재 크기의 약 1.5배(Java 8) 또는 2배 크기의 새 배열을 생성하고 기존 데이터를 복사한다. 이 확장 연산이 가끔 발생하지만, 아모타이즈드(amortized) 분석으로 평균 O(1)이다.

**Q: ArrayList와 배열의 차이는?**  
A: 배열은 크기가 고정. ArrayList는 내부 배열이 꽉 차면 더 큰 배열로 자동 확장된다. 또한 ArrayList는 제네릭, 크기 관리 메서드, 다양한 유틸리티 메서드를 제공한다.

**Q: ArrayList에서 중간 삽입이 느린 이유는?**  
A: 배열은 연속 메모리 구조이므로 특정 위치에 요소를 삽입하면 그 뒤의 모든 요소를 한 칸씩 오른쪽으로 밀어야 한다. n개 중 맨 앞에 삽입하면 n번의 이동이 필요하므로 O(n)이다.
