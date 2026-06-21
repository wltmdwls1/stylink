# 동시성 컬렉션

> 자바 고급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
일반 컬렉션(ArrayList, HashMap)은 멀티스레드 환경에서 안전하지 않음
  → 내부 배열 크기 조정, 인덱스 이동 등이 원자적이지 않음

V1 — BasicList: ArrayList 직접 사용 → ConcurrentModificationException, 데이터 손실
V2 — SynchronizedList: Collections.synchronizedList() 래핑 (프록시 패턴)
  → 모든 메서드에 synchronized → 느림, 컴파운드 연산에도 주의 필요
V3 — CopyOnWriteArrayList: 쓰기 시 전체 복사
  → 읽기 多 쓰기 少 상황에서 최적
V4 — ConcurrentHashMap: 세그먼트/버킷 단위 락
  → HashMap의 스레드 안전 버전, 실무 기본 선택
```

---

## V1 — ArrayList 직접 사용: 위험

```java
List<Integer> list = new ArrayList<>();
// 스레드A, B 동시에:
list.add(1);
list.add(2);
// 결과: 데이터 손실, ConcurrentModificationException 가능
```

ArrayList 내부의 크기 조정(배열 복사, 인덱스 이동)은 여러 단계로 이루어지는데, 그 중간에 다른 스레드가 끼어들면 데이터가 엉킨다.

---

## V2 — Collections.synchronizedList() : 프록시 패턴

```java
List<Integer> list = Collections.synchronizedList(new ArrayList<>());
list.add(1);   // synchronized
list.get(0);   // synchronized
list.size();   // synchronized
```

**Collections.synchronizedList()는 프록시 패턴**이다.  
원본 ArrayList를 감싸는 래퍼를 만들고, 모든 메서드 호출에 `synchronized`를 붙여 위임한다.

**주의 — 컴파운드 연산은 별도 동기화 필요:**
```java
// 이 코드는 여전히 안전하지 않음
if (!list.contains(x)) {  // synchronized
    list.add(x);          // synchronized (하지만 두 연산 사이에 다른 스레드가 끼어들 수 있음)
}

// 안전하게 하려면 외부 동기화 필요
synchronized (list) {
    if (!list.contains(x)) {
        list.add(x);
    }
}
```

**V2의 남은 문제**: 모든 메서드에 같은 락 → 경쟁 심하면 성능 저하. 반복 중 수정 시 ConcurrentModificationException.

---

## V3 — CopyOnWriteArrayList: 쓰기 시 전체 복사

```java
List<Integer> list = new CopyOnWriteArrayList<>();
```

**동작 방식**: 쓰기(add/remove/set) 발생 시 내부 배열 전체를 복사하고, 복사본에 수정 후 교체.  
읽기(get/iterator)는 락 없이 현재 배열의 스냅샷을 봄.

```
쓰기 전: [1, 2, 3]  (원본)
add(4) 호출:
  → [1, 2, 3, 4] 복사본 생성
  → 원본 참조를 복사본으로 교체
  → 진행 중이던 읽기는 기존 [1, 2, 3] 스냅샷을 계속 봄
```

**적합한 상황**: 읽기가 압도적으로 많고 쓰기가 드문 경우 (이벤트 리스너 목록, 설정값 캐시 등)

**부적합한 상황**: 쓰기가 자주 발생하는 경우 → 매번 전체 복사로 메모리/성능 낭비

---

## V4 — ConcurrentHashMap: 실무 표준

```java
Map<String, Integer> map = new ConcurrentHashMap<>();
map.put("key", 1);
map.get("key");
map.putIfAbsent("key", 2);  // 없을 때만 넣기 (원자적)
```

**HashMap 대신 ConcurrentHashMap을 써야 하는 이유:**
- `HashMap`은 멀티스레드에서 무한 루프(구 Java 7 이하), 데이터 손실, NPE 등이 발생할 수 있다
- `ConcurrentHashMap`은 내부 버킷 단위로만 락을 걸어 전체를 잠그지 않음 → 높은 병렬성

**원자적 복합 연산:**
```java
// 없을 때만 초기화 (체크-앤-액트를 원자적으로)
map.computeIfAbsent("key", k -> new ArrayList<>());

// 기존 값 있으면 업데이트
map.merge("key", 1, Integer::sum);  // 없으면 1, 있으면 기존값 + 1
```

---

## 동시성 컬렉션 선택 가이드

| 상황 | 선택 |
|---|---|
| 일반 읽기/쓰기 리스트 | `Collections.synchronizedList()` |
| 읽기 多, 쓰기 少 리스트 | `CopyOnWriteArrayList` |
| Map (스레드 안전) | `ConcurrentHashMap` |
| 생산자-소비자 큐 | `BlockingQueue` (`ArrayBlockingQueue` 등) |

---

## 면접 Q&A

**Q: HashMap이 멀티스레드에서 위험한 이유는?**  
A: HashMap 내부의 리사이징(rehashing) 과정과 버킷에 요소 추가 과정이 원자적이지 않다. 여러 스레드가 동시에 수정하면 무한 루프(Java 7 이하), 데이터 손실, NPE 등이 발생한다. 멀티스레드 환경에서는 반드시 ConcurrentHashMap을 써야 한다.

**Q: Collections.synchronizedMap()과 ConcurrentHashMap의 차이는?**  
A: synchronizedMap()은 모든 메서드에 단일 락을 건다. ConcurrentHashMap은 내부를 버킷 단위로 나누어 다른 버킷에는 동시 접근을 허용한다. ConcurrentHashMap이 훨씬 높은 동시성을 제공한다.

**Q: CopyOnWriteArrayList 언제 쓰나?**  
A: 읽기가 압도적으로 많고 쓰기가 드문 자료구조에 쓴다. 스프링의 이벤트 리스너 목록처럼 등록은 초기 한 번, 이후 조회만 수천 번 하는 경우에 적합하다. 쓰기가 잦으면 매번 전체 복사로 성능이 나빠진다.

## stylink 실전 적용

```java
// 재고 HOLD 상태를 빠르게 조회해야 하는 경우
private final ConcurrentHashMap<Long, InventoryStatus> inventoryStatusCache 
    = new ConcurrentHashMap<>();

// 원자적 상태 전이
inventoryStatusCache.replace(itemId, InventoryStatus.AVAILABLE, InventoryStatus.RESERVED);
// AVAILABLE일 때만 RESERVED로 바꿈 (CAS와 같은 원리)
```
