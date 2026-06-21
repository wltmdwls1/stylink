# 컬렉션 프레임워크 - 해시(Hash)

> 자바 중급2편 | DEEP

---

## 전체 흐름 한눈에 보기

```
배열/리스트에서 특정 값 찾기 → O(n) 순차 탐색

해시 알고리즘
  → 해시코드로 인덱스를 계산 → O(1) 접근
  → 해시 충돌(collision): 같은 인덱스에 여러 값 → 체이닝으로 해결
  → 로드팩터(Load Factor): 충돌 많아지면 크기 2배 확장(rehashing)

직접 구현하며 원리 이해
  → MyHashSetV0 (배열) → O(n)
  → MyHashSetV1 (해시코드 + 배열) → O(1), 충돌 없다고 가정
  → MyHashSetV2 (해시코드 + 연결 리스트 체이닝) → 충돌 처리

→ HashMap, HashSet이 이 원리로 동작
```

---

## 해시 없이 값 찾기 — O(n)

```java
// 특정 값이 있는지 확인: 순차 탐색 O(n)
int[] arr = {10, 20, 30, 40, 50, ...};
for (int val : arr) {
    if (val == target) return true;  // 최악의 경우 전체 탐색
}
```

100만 개 중 하나를 찾으면 최대 100만 번 비교. 느리다.

---

## 해시 알고리즘 — O(1)

**아이디어**: 값을 저장할 인덱스를 값에서 직접 계산할 수 없을까?

```
저장: 10 → hashCode(10) % 배열크기 = 0번 버킷에 저장
      20 → hashCode(20) % 배열크기 = 1번 버킷에 저장

조회: 찾고 싶은 값 10 → hashCode(10) % 배열크기 = 0번 버킷 확인 → O(1)
```

---

## 해시 충돌 (Hash Collision)

서로 다른 값이 같은 버킷 인덱스를 가질 수 있다.

```
hashCode("AB") % 10 = 2
hashCode("BA") % 10 = 2  ← 같은 인덱스! (충돌)

버킷 2번: [AB, BA] ← 연결 리스트(체이닝)로 여러 개 저장
```

**체이닝(Chaining)**: 충돌 시 같은 버킷에 연결 리스트로 이어붙임.

```
조회 시:
1. hashCode로 버킷 인덱스 계산 → O(1)
2. 버킷 내 연결 리스트에서 equals()로 찾기 → O(k) (k: 버킷 내 요소 수)

충돌이 적으면 k≈1 → 전체적으로 O(1)
충돌이 많으면 O(n) 최악
```

---

## hashCode와 equals의 계약

```java
// 규칙: equals()가 true면 hashCode()도 반드시 같아야 함
// 이유: 같은 버킷(hashCode)에 있어야 equals() 비교를 할 수 있기 때문

Object a = new Object();
Object b = a;
a.equals(b);  // true → a.hashCode() == b.hashCode()도 반드시 true

// 반대 방향은 성립 안 함:
// hashCode() 같아도 equals()는 false 가능 (해시 충돌)
```

---

## 로드팩터 (Load Factor) 와 Rehashing

```
로드팩터 = 현재 요소 수 / 버킷 수

Java HashMap 기본값: 0.75
→ 버킷의 75%가 채워지면 버킷 수를 2배로 늘리고 전체 재배치(rehashing)
```

```java
HashMap<String, Integer> map = new HashMap<>(16, 0.75f);
//                                            ↑    ↑
//                                   초기 버킷수  로드팩터
```

로드팩터가 낮을수록 충돌 적고 메모리 낭비. 높을수록 메모리 효율적이지만 충돌 많음.

---

## String의 hashCode

```java
String s = "hello";
s.hashCode();  // 99162322 (문자들의 값으로 계산된 정수)

// 같은 값이면 항상 같은 hashCode
"hello".hashCode() == "hello".hashCode()  // true
new String("hello").hashCode() == "hello".hashCode()  // true
```

---

## 직접 구현으로 원리 이해

```java
// MyHashSetV2 — 해시 + 체이닝
class MyHashSetV2 {
    private static final int CAPACITY = 16;
    private LinkedList<Integer>[] buckets = new LinkedList[CAPACITY];

    public boolean add(int value) {
        int index = Math.abs(value % CAPACITY);  // 버킷 인덱스 계산
        if (buckets[index] == null) {
            buckets[index] = new LinkedList<>();
        }
        if (buckets[index].contains(value)) return false;  // 중복 체크
        buckets[index].add(value);  // 체이닝
        return true;
    }

    public boolean contains(int value) {
        int index = Math.abs(value % CAPACITY);
        return buckets[index] != null && buckets[index].contains(value);
    }
}
```

---

## 면접 Q&A

**Q: HashMap에서 값 조회가 O(1)인 이유는?**  
A: 키의 `hashCode()`로 버킷 인덱스를 계산해 바로 해당 버킷에 접근하기 때문이다. 배열처럼 순차 탐색이 없다. 단, 충돌이 많으면 같은 버킷 내에서 `equals()` 비교가 필요해 O(n)까지 될 수 있다.

**Q: equals()만 오버라이드하고 hashCode()를 안 오버라이드하면 어떻게 되나?**  
A: 논리적으로 같은 객체가 다른 `hashCode()`를 가져 다른 버킷에 들어간다. `map.get(key)`로 찾을 때 해당 버킷에 없어서 null을 반환하거나 HashSet에 중복이 허용되는 버그가 생긴다.

**Q: 로드팩터 0.75의 의미는?**  
A: HashMap에 저장된 요소 수가 버킷 수의 75%를 넘으면 버킷 수를 2배로 늘리고 전체 데이터를 재배치(rehashing)한다. 0.75는 시간(검색 속도)과 공간(메모리) 사이의 경험적 균형값이다.
