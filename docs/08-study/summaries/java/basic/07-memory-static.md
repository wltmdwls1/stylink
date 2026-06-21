# 메모리 구조와 static

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

자바의 메모리 영역 3가지:
- **메서드 영역(Method Area)**: 클래스 정보, static 변수/메서드, 상수 저장
- **스택(Stack)**: 지역 변수, 매개변수, 메서드 호출 정보
- **힙(Heap)**: `new`로 생성된 객체, 인스턴스 변수

---

## 메모리 영역별 역할

```
[메서드 영역]       [스택]              [힙]
클래스 메타데이터   main() 프레임       User 객체 (id=1)
static 변수        ├── args           User 객체 (id=2)
상수풀             └── user → [참조]→ [힙의 User]
                   service() 프레임
```

---

## static 변수와 인스턴스 변수

```java
class Counter {
    static int totalCount = 0;  // static: 모든 인스턴스가 공유, 메서드 영역에 저장
    int instanceCount = 0;      // 인스턴스: 객체마다 따로 존재, 힙에 저장

    Counter() {
        totalCount++;    // 공유
        instanceCount++; // 이 객체만
    }
}

Counter c1 = new Counter();
Counter c2 = new Counter();
System.out.println(Counter.totalCount);  // 2 (공유됨)
System.out.println(c1.instanceCount);    // 1 (c1만)
System.out.println(c2.instanceCount);    // 1 (c2만)
```

---

## static 메서드

```java
class MathUtil {
    // static 메서드: 인스턴스 없이 호출 가능
    public static int add(int a, int b) { return a + b; }

    // static 메서드에서 인스턴스 변수/메서드 사용 불가!
    // (어떤 객체의 변수인지 모름)
}

MathUtil.add(1, 2);  // 클래스명으로 직접 호출
```

---

## static 사용 가이드라인

```java
// static이 적절한 경우:
// 1. 모든 인스턴스가 공유하는 값
private static final int MAX_SIZE = 100;

// 2. 인스턴스 상태에 의존하지 않는 유틸리티 메서드
public static String format(String s) { return s.trim().toLowerCase(); }

// static이 부적절한 경우:
// 멀티스레드 환경에서 공유 상태를 바꾸는 static 변수 → 동시성 문제
static int sharedMutableState = 0;  // 위험!
```

---

## 면접 Q&A

**Q: static 변수와 인스턴스 변수의 차이는?**  
A: `static` 변수는 메서드 영역에 저장되고 모든 인스턴스가 공유한다. 클래스가 로드될 때 생성되어 프로그램 종료까지 유지된다. 인스턴스 변수는 힙에 저장되고 객체마다 독립적으로 존재한다. `new`로 객체를 생성할 때 만들어지고 GC가 수거할 때 사라진다.

**Q: static 메서드에서 this를 쓸 수 없는 이유는?**  
A: `this`는 현재 인스턴스를 가리키는 참조다. static 메서드는 특정 인스턴스 없이 클래스 레벨에서 호출된다. 인스턴스가 없으니 `this`가 가리킬 객체가 없어 컴파일 에러가 발생한다.
