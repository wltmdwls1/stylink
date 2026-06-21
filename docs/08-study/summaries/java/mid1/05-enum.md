# 열거형 - Enum

> 자바 중급1편 | DEEP

---

## 전체 흐름 한눈에 보기

```
문자열로 상태값 관리 → 타입 안전성 없음, 오타 가능, 컴파일 검증 불가

V1 — String으로 등급 관리 → "GOL", "vip" 오타 불가능
V2 — 상수(static final int) → 타입 안전 아님 (아무 int 가능)
V3 — 타입 안전 열거형 패턴 (직접 구현) → 복잡함
V4 — Java Enum 사용 → 타입 안전 + 컴파일 검증 + 풍부한 기능

Enum의 특징:
  → 값에 추가 데이터 붙이기 (description, discountRate 등)
  → 추상 메서드로 각 상수별 다른 동작
  → values(), ordinal(), name() 유틸리티 메서드
  → switch 표현식과 결합
```

---

## V1 — String으로 관리: 타입 안전성 없음

```java
// 호출하는 측에서 어떤 값이든 넣을 수 있음
discountService.discount("GOL", price);   // 오타 — 런타임 버그
discountService.discount("DIAMONDD", price); // 오타 — 런타임 버그
discountService.discount("vip", price);   // 아예 없는 등급 — 할인 0원
```

컴파일 시 잡을 수 없다. 런타임에야 발견된다.

**V1의 남은 문제**: 유효하지 않은 값을 막을 방법이 없음.

---

## V2 — 상수(static final): 여전히 부족

```java
public class Grade {
    public static final int BASIC = 1;
    public static final int GOLD = 2;
    public static final int DIAMOND = 3;
}

discount(Grade.GOLD, price);  // 좋아짐
discount(999, price);  // 여전히 컴파일 에러 없음 — 아무 int나 가능
```

int 타입이므로 존재하지 않는 값도 받아버린다.

**V2의 남은 문제**: 타입이 int라 유효하지 않은 값을 막지 못함.

---

## V3 — 타입 안전 열거형 패턴 (직접 구현)

```java
public class Grade {
    public static final Grade BASIC = new Grade();
    public static final Grade GOLD = new Grade();
    public static final Grade DIAMOND = new Grade();
    private Grade() {}  // 외부에서 new 불가
}

discount(Grade.GOLD, price);   // 정상
discount(new Grade(), price);  // 컴파일 에러! (private 생성자)
```

타입 안전성 확보. 하지만 직접 구현하기 복잡하고 switch 지원도 안 됨.

---

## V4 — Java Enum (완성형)

```java
public enum Grade {
    BASIC, GOLD, DIAMOND
}

discount(Grade.GOLD, price);   // 정상
discount(Grade.VIP, price);    // 컴파일 에러! (VIP 없음)
```

---

## Enum에 데이터 추가하기

```java
public enum Grade {
    BASIC("일반", 10),
    GOLD("골드", 20),
    DIAMOND("다이아몬드", 30);

    private final String description;
    private final int discountPercent;

    Grade(String description, int discountPercent) {
        this.description = description;
        this.discountPercent = discountPercent;
    }

    public int discount(int price) {
        return price * discountPercent / 100;
    }
}

// 사용
int amount = Grade.GOLD.discount(10000);  // 2000
System.out.println(Grade.GOLD.description);  // "골드"
```

**핵심 패턴**: 설명(description)과 비즈니스 로직을 Enum 안에 캡슐화.

---

## Enum 유틸리티 메서드

```java
Grade.values()              // [BASIC, GOLD, DIAMOND] 배열
Grade.valueOf("GOLD")       // Grade.GOLD (문자열 → Enum, 없으면 예외)
Grade.GOLD.name()           // "GOLD" (상수명 문자열)
Grade.GOLD.ordinal()        // 1 (0부터 순서, DB 저장에 주의!)
Grade.GOLD.toString()       // "GOLD"
```

**ordinal() 주의**: 순서가 바뀌면 DB 저장값 의미가 달라진다. JPA에서 `@Enumerated(EnumType.STRING)` 사용 권장.

---

## switch 표현식과 Enum

```java
String discount = switch (grade) {
    case BASIC -> "10% 할인";
    case GOLD -> "20% 할인";
    case DIAMOND -> "30% 할인";
};
// 모든 case를 다루지 않으면 컴파일 에러 → 빠짐없는 처리 보장
```

---

## stylink 적용 예시

프로젝트에서 이미 사용 중인 패턴 (CLAUDE.md 규칙):

```java
public enum InventoryStatus {
    AVAILABLE("판매 가능"),
    RESERVED("예약됨"),
    IN_TRANSIT("출장 중"),
    SOLD("판매 완료");

    private final String description;

    InventoryStatus(String description) {
        this.description = description;
    }
}
```

---

## 면접 Q&A

**Q: Enum을 쓰는 이유는?**  
A: 타입 안전성과 컴파일 타임 검증. 문자열이나 상수 int로 상태를 관리하면 유효하지 않은 값이 들어와도 컴파일 에러가 없어 런타임 버그로 이어진다. Enum을 사용하면 정의된 상수만 사용할 수 있어 컴파일 타임에 오류를 잡을 수 있다.

**Q: @Enumerated(EnumType.ORDINAL) vs STRING?**  
A: ORDINAL은 Enum의 순서(0, 1, 2...)를 저장한다. 나중에 Enum 상수 순서가 바뀌면 기존 DB 데이터의 의미가 바뀌는 심각한 버그가 생긴다. STRING은 이름("BASIC", "GOLD")을 저장하므로 순서 변경에 안전하다. 실무에서는 반드시 `EnumType.STRING`을 사용한다.

**Q: Enum에 메서드를 추가하는 이유는?**  
A: 해당 상태값과 관련된 비즈니스 로직을 Enum 안에 캡슐화할 수 있다. 예를 들어 `Grade.GOLD.discount(price)`처럼 호출하면 외부에서 if-else로 등급을 분기하는 코드가 없어지고, 새 등급 추가 시 Enum에만 추가하면 된다.
