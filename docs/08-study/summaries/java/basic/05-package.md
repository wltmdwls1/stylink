# 패키지 (Package)

> 자바 기본편 | LIGHT

---

## 핵심 개념 요약

패키지는 관련된 클래스를 그룹화하는 폴더 구조.  
이름 충돌 방지 + 코드 구조화 역할.

---

## 패키지 선언과 import

```java
// 파일 최상단에 패키지 선언
package com.stylink.domain.order;

// 다른 패키지 클래스 사용 시 import
import com.stylink.domain.user.User;
import java.util.List;
import java.util.*;  // 패키지 전체 import (와일드카드)

public class OrderService {
    // 같은 패키지: import 불필요
    // 다른 패키지: import 필요 또는 FQCN 사용
    // java.lang.*은 자동 import (String, Object 등)
}
```

---

## 패키지 명명 규칙

```
com.회사명.프로젝트명.모듈.레이어
com.stylink.domain.order        → 주문 도메인 엔티티/로직
com.stylink.fo.order.controller → FO API 주문 컨트롤러
com.stylink.common.exception    → 공통 예외
```

- 소문자만 사용
- 역방향 도메인 관례: `com.company.project`

---

## 이름 충돌 시 FQCN

```java
import java.util.Date;
// java.sql.Date도 쓰고 싶다면:
java.sql.Date sqlDate = new java.sql.Date(System.currentTimeMillis());
// 하나는 import, 나머지는 패키지명 포함 전체 이름(FQCN)으로 사용
```

---

## 면접 Q&A

**Q: 패키지의 역할은?**  
A: 두 가지다. 첫째, 이름 충돌 방지 — 서로 다른 패키지에 같은 이름의 클래스가 있어도 구분 가능하다. 둘째, 코드 구조화 — 관련 클래스를 그룹화해 프로젝트 구조를 명확하게 한다. 또한 `package-private` 접근 제어 수준이 패키지 단위로 적용된다.
