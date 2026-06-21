# 병렬 스트림

> 자바 고급3편 | DEEP

---

## 전체 흐름 한눈에 보기

```
단일 스트림 → 순차 처리 (1개 스레드)
  → 1~8 계산: 8초 걸림 (각 1초씩)

멀티스레드 직접 사용 → 병렬 처리 가능하지만 복잡
Fork/Join 프레임워크
  → 분할(Fork): 큰 작업을 작은 작업으로 재귀 분할
  → 합산(Join): 결과를 모음
  → 작업 훔치기: 빈 스레드가 바쁜 스레드의 큐에서 작업을 가져감

parallelStream() — Fork/Join을 자동으로 사용
  → stream()을 parallelStream()으로만 바꾸면 됨
  → ForkJoinPool.commonPool() 공유 스레드풀 사용 (CPU 코어 수)

병렬 스트림 주의점
  → 항상 빠른 게 아님 → 오버헤드 vs 이득 측정 필요
  → 공유 상태 수정 시 동시성 문제 발생
```

---

## 단일 스트림 vs 병렬 스트림

```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8);

// 단일 스트림: 순차 처리 (8초)
int sum = numbers.stream()
    .mapToInt(HeavyJob::heavyTask)  // 각 1초 걸리는 작업
    .sum();

// 병렬 스트림: 병렬 처리 (약 2초, 4코어 기준)
int sumParallel = numbers.parallelStream()
    .mapToInt(HeavyJob::heavyTask)
    .sum();
```

`parallelStream()`으로만 바꾸면 내부적으로 Fork/Join 프레임워크가 자동으로 작동한다.

---

## Fork/Join 프레임워크

병렬 스트림의 기반 기술. "분할 후 정복" 전략.

```
전체 작업 [1,2,3,4,5,6,7,8]
    ↓ Fork
[1,2,3,4]          [5,6,7,8]
    ↓ Fork              ↓ Fork
[1,2] [3,4]        [5,6] [7,8]
  ↓     ↓            ↓     ↓
 실행   실행          실행   실행   ← 스레드들이 병렬로
  ↓     ↓ Join        ↓     ↓
 [3]   [7]           [11]  [15]
      ↓ Join               ↓ Join
     [10]                 [26]
            ↓ Join
           [36]
```

---

## 작업 훔치기 (Work Stealing)

분할된 작업들이 각 스레드의 덱(Deque)에 배분된다. 자기 덱의 작업이 비면 다른 스레드의 덱 뒤쪽에서 작업을 훔쳐온다.

→ 작업이 골고루 분배되어 특정 스레드만 놀지 않음 → 효율적

---

## parallelStream 사용 시 주의점

### 1. 항상 빠른 게 아니다

```java
// 데이터가 적거나 작업이 간단하면 오히려 느림
List<Integer> small = List.of(1, 2, 3);
int sum = small.parallelStream()  // 스레드 분배 오버헤드 > 이득
    .mapToInt(n -> n)
    .sum();
// 이 경우 stream()이 더 빠름
```

**병렬 스트림이 유리한 조건:**
- 요소 수가 많다 (수천 개 이상)
- 각 연산이 CPU를 많이 쓴다 (계산 위주)
- 데이터를 쉽게 분할할 수 있다 (ArrayList는 인덱스 분할 가능, LinkedList는 비효율)
- 스레드 간 공유 상태가 없다

**주의: IO 바운드 작업에는 비효율적**  
공유 스레드 풀(`commonPool`)을 IO가 점유하면 같은 JVM의 다른 병렬 스트림도 영향받음.

### 2. 공유 상태 수정 시 동시성 문제

```java
// 위험한 코드 — 여러 스레드가 result를 동시에 수정
List<Integer> result = new ArrayList<>();
numbers.parallelStream()
    .filter(n -> n > 3)
    .forEach(n -> result.add(n));  // ConcurrentModificationException 가능!

// 올바른 방법: collect 사용 (스레드 안전)
List<Integer> result = numbers.parallelStream()
    .filter(n -> n > 3)
    .collect(Collectors.toList());
```

### 3. 순서 의존적인 작업에 주의

```java
// 순서가 보장되지 않을 수 있음
numbers.parallelStream()
    .forEachOrdered(System.out::println);  // 순서 보장 but 성능 저하
    // vs
    .forEach(System.out::println);         // 순서 미보장 but 더 빠름
```

---

## 언제 쓸까 (요약)

| 조건 | 추천 |
|---|---|
| 요소 적음 또는 연산 가벼움 | `stream()` |
| 요소 많음 + CPU 집중 연산 + 독립 | `parallelStream()` |
| IO 작업(DB, HTTP) | `stream()` + 별도 스레드풀 |
| 순서 중요 | `stream()` 또는 `forEachOrdered()` |

---

## 면접 Q&A

**Q: parallelStream()은 항상 빠른가?**  
A: 아니다. 작업 분할, 스레드 관리, 결과 합산 오버헤드가 있다. 데이터가 적거나 연산이 간단하면 오히려 느릴 수 있다. CPU 바운드 작업이고 요소가 많을 때 유리하다. IO 바운드 작업에는 공유 스레드풀을 점유해 다른 병렬 작업에 영향을 줄 수 있어 주의해야 한다.

**Q: Fork/Join 프레임워크란?**  
A: 큰 작업을 재귀적으로 작은 단위로 분할(Fork)하고 결과를 합산(Join)하는 분할-정복 방식의 병렬 처리 프레임워크. 작업 훔치기(Work Stealing) 알고리즘으로 스레드가 놀지 않고 효율적으로 작업을 처리한다. `parallelStream()`이 내부적으로 이를 사용한다.

**Q: parallelStream()에서 공유 상태를 수정하면 안 되는 이유는?**  
A: 여러 스레드가 동시에 공유 컬렉션(ArrayList 등)을 수정하면 `ConcurrentModificationException`이나 데이터 손실이 발생한다. 상태를 만들어야 한다면 `collect(Collectors.toList())`처럼 스레드-로컬 방식으로 수집해야 한다.
