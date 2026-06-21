# 람다가 필요한 이유

> 자바 고급3편 | DEEP

---

## 전체 흐름 한눈에 보기

```
문제: 조건마다 메서드를 따로 만들어야 함 → 코드 중복 폭발
  → 내부 클래스 → 익명 클래스 → 람다로 점진적 개선

V1 — filterEven(), filterOdd() 각각 구현 → 조건 바뀔 때마다 메서드 추가
V2 — 내부 클래스로 조건을 파라미터로 전달 → 인터페이스 활용
V3 — 익명 클래스로 간소화 → 보일러플레이트가 너무 많음
V4 — 람다(Lambda): 메서드의 본문만 전달 → 핵심 코드만 남음

→ 람다 = 함수를 값처럼 전달하는 것
```

---

## V1 — 조건마다 메서드를 따로 만든다

```java
// 짝수 필터
static List<Integer> filterEven(List<Integer> numbers) {
    List<Integer> result = new ArrayList<>();
    for (Integer n : numbers) {
        if (n % 2 == 0) result.add(n);  // ← 이 조건만 다름
    }
    return result;
}

// 홀수 필터 (짝수와 거의 동일)
static List<Integer> filterOdd(List<Integer> numbers) {
    List<Integer> result = new ArrayList<>();
    for (Integer n : numbers) {
        if (n % 2 == 1) result.add(n);  // ← 이 조건만 다름
    }
    return result;
}
```

"5 이상", "소수만", "10의 배수" 조건이 생길 때마다 메서드를 하나씩 더 만들어야 한다.

**V1의 남은 문제**: 필터 구조가 완전히 동일한데도 조건마다 메서드를 복사해야 함.

---

## V2 — 조건을 인터페이스로 전달

달라지는 부분(조건 `n % 2 == 0`)을 인터페이스로 분리하면 하나의 메서드로 모든 조건을 처리할 수 있다.

```java
interface NumberFilter {
    boolean test(int number);  // 조건을 담는 함수형 인터페이스
}

// 하나의 filter 메서드로 통합
static List<Integer> filter(List<Integer> numbers, NumberFilter f) {
    List<Integer> result = new ArrayList<>();
    for (Integer n : numbers) {
        if (f.test(n)) result.add(n);
    }
    return result;
}

// 사용 — 내부 클래스로 조건 전달
class EvenFilter implements NumberFilter {
    public boolean test(int n) { return n % 2 == 0; }
}

filter(numbers, new EvenFilter());
```

**V2의 남은 문제**: 조건을 쓸 때마다 클래스를 만들어야 함. 파일이 늘어나고 번거롭다.

---

## V3 — 익명 클래스로 간소화

클래스를 따로 파일로 만들지 않고 즉석에서 정의한다.

```java
filter(numbers, new NumberFilter() {
    @Override
    public boolean test(int n) {
        return n % 2 == 0;  // ← 이게 전달하고 싶은 핵심인데
    }
    // 나머지는 전부 의례적인 코드(보일러플레이트)
});
```

**V3의 남은 문제**: `new NumberFilter() { @Override public boolean test(int n) {` 이 부분은 항상 같고 쓸모없는 코드. 실제로 원하는 건 `return n % 2 == 0` 이 한 줄뿐.

---

## V4 — 람다: 핵심만 남기기

```java
// 익명 클래스
filter(numbers, new NumberFilter() {
    @Override
    public boolean test(int n) { return n % 2 == 0; }
});

// 람다: 매개변수와 본문만 남긴 것
filter(numbers, n -> n % 2 == 0);
```

익명 클래스에서 불필요한 껍데기를 벗겨낸 것이 람다다.  
자바 컴파일러가 인터페이스 정보를 보고 나머지를 추론한다.

**람다는 함수를 값처럼 다루는 것**:
```java
NumberFilter even = n -> n % 2 == 0;  // 변수에 담을 수 있음
filter(numbers, even);                  // 인자로 전달 가능
```

---

## 함수 vs 메서드

자바에서 메서드는 반드시 클래스에 속해야 한다. 독립적인 함수는 없다.  
람다는 이 제약을 우회하여 "함수처럼" 값을 전달할 수 있게 해준다.  
단, 내부적으로는 여전히 함수형 인터페이스의 인스턴스로 표현된다.

---

## 면접 Q&A

**Q: 람다가 왜 생겼나?**  
A: 익명 클래스의 보일러플레이트 코드를 제거하고, 함수를 값처럼 전달할 수 있게 하기 위해 자바 8에서 도입됐다. 특히 컬렉션 처리(filter/map/sort)에서 조건마다 클래스를 만드는 번거로움을 없애준다.

**Q: 람다와 익명 클래스의 차이는?**  
A: 람다는 함수형 인터페이스(추상 메서드 하나짜리)만 구현할 수 있고, `this`는 람다를 선언한 외부 클래스를 가리킨다. 익명 클래스는 여러 메서드를 가진 인터페이스도 구현할 수 있고, `this`는 익명 클래스 자신을 가리킨다.

**Q: 람다가 인스턴스를 생성하나?**  
A: 그렇다. 람다도 익명 클래스처럼 내부적으로 클래스가 만들어지고 인스턴스가 생성된다. `getClass()`로 확인하면 `$$Lambda/...` 형태로 나온다.
