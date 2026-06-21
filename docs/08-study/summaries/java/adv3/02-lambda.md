# 람다

> 자바 고급3편 | DEEP

---

## 전체 흐름 한눈에 보기

```
람다 정의: 이름 없는 함수 → (매개변수) -> { 본문 }

람다 문법 간소화 단계
  → 기본형 → 타입 생략 → 중괄호 생략 → 매개변수 괄호 생략

람다의 조건: 함수형 인터페이스에만 사용 가능 (추상 메서드 1개)

람다를 변수처럼 다루기
  → 변수에 대입, 파라미터로 전달, 반환값으로 사용 (고차 함수)

캡처(Capture): 람다 바깥 변수를 참조할 때 effectively final이어야 함
```

---

## 람다 문법

```java
// 일반 메서드
int add(int x, int y) {
    return x + y;
}

// 람다 (기본형)
(int x, int y) -> { return x + y; }

// 타입 추론: 컴파일러가 인터페이스에서 타입을 추론
(x, y) -> { return x + y; }

// 본문이 한 줄이면 중괄호와 return 생략
(x, y) -> x + y

// 매개변수가 1개면 괄호 생략
x -> x * 2

// 매개변수 없으면 빈 괄호
() -> System.out.println("hello")
```

---

## 함수형 인터페이스

람다는 추상 메서드가 **정확히 하나**인 인터페이스에만 할당할 수 있다.

```java
@FunctionalInterface  // 함수형 인터페이스임을 명시 (컴파일러가 검증)
interface NumberFilter {
    boolean test(int n);
}

NumberFilter even = n -> n % 2 == 0;
boolean result = even.test(4);  // true
```

`@FunctionalInterface`는 필수가 아니지만, 실수로 추상 메서드를 더 추가하면 컴파일 에러로 잡아주므로 붙이는 게 좋다.

---

## 람다를 변수처럼 다루기

```java
// 1. 변수에 담기
Runnable task = () -> System.out.println("실행");
task.run();

// 2. 메서드 파라미터로 전달
executeTask(task);

static void executeTask(Runnable r) {
    r.run();
}

// 3. 반환값으로 사용 (고차 함수)
static Runnable createGreeter(String name) {
    return () -> System.out.println("Hello " + name);  // 람다 반환
}

Runnable greeter = createGreeter("Seungjin");
greeter.run();  // Hello Seungjin
```

---

## 고차 함수 (Higher-Order Function)

함수를 인자로 받거나 반환하는 함수를 **고차 함수**라 한다.

```java
// filter는 조건 함수를 인자로 받음 → 고차 함수
List<Integer> result = filter(numbers, n -> n > 5);

// createMultiplier는 함수를 반환 → 고차 함수
Function<Integer, Integer> triple = createMultiplier(3);
triple.apply(10);  // 30

static Function<Integer, Integer> createMultiplier(int factor) {
    return x -> x * factor;  // factor를 캡처한 람다 반환
}
```

---

## 람다 캡처 (변수 캡처)

람다는 자신이 선언된 범위의 지역 변수를 참조할 수 있다.

```java
String prefix = "User";  // 지역 변수
Function<String, String> addPrefix = name -> prefix + ": " + name;
// prefix를 람다가 캡처함
```

**조건: effectively final** — 람다가 캡처한 지역 변수는 변경할 수 없다.

```java
String prefix = "User";
prefix = "Admin";  // ← 이 줄이 있으면 위 람다에서 컴파일 에러
                   // effectively final이 아니기 때문
```

**왜 이 제약이 있는가?**: 람다는 다른 스레드에서 실행될 수 있다. 지역 변수는 스택에 있어서 메서드 종료 후 사라지는데, 람다가 나중에 실행될 때 그 값이 없어졌을 수 있다. 그래서 람다는 실제로 값을 **복사**해서 갖는다 — 복사 후 원본이 바뀌면 불일치가 생기므로 변경을 금지한다.

인스턴스 변수(힙에 있음)는 이 제약이 없다.

---

## 람다와 익명 클래스의 차이: this

```java
public class OuterClass {
    private String name = "Outer";

    void demo() {
        // 익명 클래스: this = 익명 클래스 자신
        Runnable anon = new Runnable() {
            @Override
            public void run() {
                System.out.println(this.getClass()); // 익명 클래스
            }
        };

        // 람다: this = 람다가 선언된 OuterClass의 인스턴스
        Runnable lambda = () -> {
            System.out.println(this.getClass()); // OuterClass
            System.out.println(this.name);       // Outer
        };
    }
}
```

---

## 면접 Q&A

**Q: 람다를 쓸 수 있는 조건은?**  
A: 대상이 함수형 인터페이스(추상 메서드가 정확히 하나)여야 한다. 컴파일러가 인터페이스 시그니처로 매개변수 타입과 반환 타입을 추론해 람다를 적용한다.

**Q: 람다에서 지역 변수를 캡처할 때 effectively final이어야 하는 이유는?**  
A: 람다는 지역 변수를 값으로 복사해 갖는다. 복사 후 원본이 바뀌면 불일치가 생기기 때문에 변경을 금지한다. 또한 람다가 다른 스레드에서 실행될 때 스택의 지역 변수는 이미 사라졌을 수 있어 안전하지 않다.

**Q: 람다에서 this가 가리키는 것은?**  
A: 람다가 선언된 외부 클래스의 인스턴스다. 익명 클래스에서 this는 익명 클래스 자신을 가리키지만, 람다는 별도의 클래스 컨텍스트를 만들지 않고 외부 클래스의 컨텍스트를 유지한다.
