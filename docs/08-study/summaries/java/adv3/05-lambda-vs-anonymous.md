# 람다 vs 익명 클래스

> 자바 고급3편 | LIGHT (차이점 인식 목적)

---

## 전체 흐름 한눈에 보기

```
람다는 익명 클래스를 대부분 대체할 수 있지만 완전히 같지는 않음

차이점 요약:
  1. 문법: 람다가 훨씬 간결
  2. 대상: 람다는 함수형 인터페이스만, 익명 클래스는 모든 인터페이스/클래스
  3. this: 람다=외부 클래스, 익명 클래스=자기 자신
  4. 상태(필드): 익명 클래스만 가능
  5. 호환성: 익명 클래스는 Java 1.1부터, 람다는 Java 8부터

→ 실무에서는 함수형 인터페이스라면 람다를 쓰고,
   여러 메서드가 필요하거나 상태가 필요하면 익명 클래스(또는 내부 클래스)를 씀
```

---

## 레거시 코드에서 익명 클래스 마주치기

Java 8 이전 코드(레거시 금융 시스템, 구형 Spring 설정 등)에서 이런 패턴을 볼 수 있다:

```java
// Java 8 이전 스타일 — 이벤트 핸들러
button.addActionListener(new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
        System.out.println("버튼 클릭");
    }
});

// Java 8+ 람다로 대체
button.addActionListener(e -> System.out.println("버튼 클릭"));
```

```java
// 구형 Comparator
List<String> names = new ArrayList<>(Arrays.asList("Charlie", "Alice", "Bob"));
Collections.sort(names, new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return a.compareTo(b);
    }
});

// 현대 방식
names.sort(Comparator.naturalOrder());
```

---

## 핵심 차이점 비교

| 항목 | 람다 | 익명 클래스 |
|---|---|---|
| 대상 | 함수형 인터페이스만 | 모든 인터페이스, 추상/일반 클래스 |
| `this` | 외부 클래스 인스턴스 | 익명 클래스 자신 |
| 상태(멤버 변수) | 불가 | 가능 |
| 여러 메서드 오버라이드 | 불가 | 가능 |
| Java 버전 | Java 8+ | Java 1.1+ |

```java
// 익명 클래스만 가능: 여러 메서드 오버라이드
Comparator<String> c = new Comparator<String>() {
    @Override
    public int compare(String a, String b) { return a.compareTo(b); }

    @Override
    public boolean equals(Object obj) { return false; }  // 람다로는 불가
};
```

---

## 익명 클래스를 유지해야 하는 경우

1. **여러 메서드가 필요한 인터페이스** — 메서드가 2개 이상인 경우
2. **내부 상태(필드)가 필요한 경우** — 카운터, 누적 값 등
3. **추상 클래스 상속** — 람다는 인터페이스만 구현 가능

```java
// 상태가 있는 익명 클래스 — 람다로 대체 불가
Runnable counter = new Runnable() {
    private int count = 0;  // 멤버 변수
    @Override
    public void run() {
        count++;
        System.out.println("count: " + count);
    }
};
```

---

## 면접 Q&A

**Q: 람다와 익명 클래스를 언제 구분해서 쓰나?**  
A: 추상 메서드가 하나이고 상태가 필요 없으면 람다를 쓴다. 여러 메서드를 오버라이드해야 하거나, 내부 상태(카운터 등)가 필요하거나, 추상 클래스를 상속해야 하면 익명 클래스 또는 별도 클래스를 쓴다.

**Q: 레거시 코드에서 익명 클래스를 람다로 리팩토링할 때 주의할 것은?**  
A: `this`의 의미가 바뀐다. 익명 클래스에서 `this`는 익명 클래스 자신이었지만, 람다에서 `this`는 외부 클래스다. 익명 클래스 안에서 `this`를 사용하는 코드가 있다면 의도를 확인하고 리팩토링해야 한다.
