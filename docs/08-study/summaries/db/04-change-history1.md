# 데이터 변경 이력 설계1 — 변경 추적 컬럼

> 데이터베이스 로드맵 | DB 설계 패턴

---

## 전체 흐름 한눈에 보기

```
이력 없는 테이블 → UPDATE 시 과거 정보 영구 소멸
  ↓
4가지 질문
  Q1. 누가 변경했나?   → 기본 추적 컬럼 (created_by / updated_by)
  Q2. 언제 변경했나?   → 기본 추적 컬럼 (created_at / updated_at)
  Q3. 왜 변경했나?     → 변경 사유 컬럼 (change_type / change_reason)
  Q4. 이전 값은?      → 미해결 (다음 편: 이력 테이블)
  [추가] 어디서 변경?  → 감사 컬럼 (source_system / client_ip)
```

---

## 변경 이력이 필요한 이유

이력 없는 테이블은 UPDATE 시 과거 데이터가 즉시 사라진다.

| 실무 사례 | 이력 없을 때 결과 |
|----------|----------------|
| 고객: "저는 15,000원에 주문했는데요?" | 현재 12,000원만 조회됨 — 반박 불가 |
| 배송 반송 — 주소가 바뀐 건지 오류인지 | 주소 변경 이력 없음 — 원인 불명 |
| 금융 감사: "한도를 누가, 왜 변경했나요?" | 현재 값만 존재 — 규정 위반 |

> 데이터 변경 이력은 선택이 아니라 필수다.

---

## 1단계: 기본 변경 추적 컬럼

거의 모든 테이블에 적용하는 기본 4개 컬럼.

```
created_at  DATETIME  DEFAULT CURRENT_TIMESTAMP         -- 최초 등록 시간
created_by  VARCHAR(100)                                -- 최초 등록자
updated_at  DATETIME  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
updated_by  VARCHAR(100)                                -- 마지막 수정자
```

**등록 시에도 `updated_at`을 채우는 이유**: `NULL`로 두면 "최근 변경순 정렬" 시 `COALESCE(updated_at, created_at)` 함수를 매번 써야 함. 생성을 첫 번째 수정으로 간주해 처음부터 채우는 게 간단하다.

**해결**: Q1(누가), Q2(언제) ✅  
**미해결**: Q3(왜), Q4(이전 값)

**적합한 경우**: 이력이 중요하지 않은 마스터 데이터(코드 테이블, 설정 테이블), 내부 관리용 데이터

---

## 2단계: 변경 사유 컬럼

```
change_type    VARCHAR(50)   -- 변경 유형 (표준화된 코드)
change_reason  VARCHAR(500)  -- 변경 사유 (자유 텍스트)
```

### change_type을 따로 분리하는 이유

`change_reason`만 쓰면 "가격 변경", "할인 적용", "금액 수정" 등 사람마다 다르게 적는다 → 검색·통계 불가.

```sql
-- change_type 없을 때 (비효율)
WHERE change_reason LIKE '%가격%' OR change_reason LIKE '%할인%' ...

-- change_type 있을 때 (효율)
WHERE change_type = 'PRICE_CHANGE'
```

| change_type | 설명 |
|------------|------|
| CREATE | 신규 등록 |
| PRICE_CHANGE | 가격 변경 |
| STOCK_ADJUST | 재고 조정 |
| STATUS_CHANGE | 상태 변경 |
| PROMOTION | 프로모션 적용 |
| CORRECTION | 오류 정정 |

> `change_type` = 시스템을 위한 분류 코드, `change_reason` = 사람을 위한 상세 설명

**해결**: Q1 ✅ Q2 ✅ Q3 ✅  
**미해결**: Q4(이전 값)

---

## 3단계: 감사(Audit) 컬럼

현대 시스템은 하나의 데이터를 여러 경로(웹, API, 배치, 모바일)에서 변경한다. 문제 발생 시 "어디서 변경됐나"를 알아야 원인을 빠르게 추적할 수 있다.

```
source_system  VARCHAR(50)  -- 변경 발생 시스템
client_ip      VARCHAR(50)  -- 요청 IP
```

| source_system | 설명 |
|--------------|------|
| WEB_ADMIN | 웹 관리자 화면 |
| WEB_USER | 웹 사용자 화면 |
| API | 외부 API 연동 |
| BATCH | 배치 시스템 |
| MOBILE_APP | 모바일 앱 |
| MANUAL | DBA 직접 수정 |

**MSA 환경 추가 고려**: `request_id` (correlation ID) — 하나의 요청이 여러 서비스를 거칠 때 동일한 ID로 전체 흐름 추적 가능.

---

## 전체 컬럼 구조 (최종)

```sql
-- 기본 변경 추적
created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
created_by    VARCHAR(100) NOT NULL,
updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
updated_by    VARCHAR(100) NOT NULL,

-- 변경 사유
change_type   VARCHAR(50),
change_reason VARCHAR(500),

-- 감사
source_system VARCHAR(50),
client_ip     VARCHAR(50)
```

---

## 한계점 — 다음 편에서 해결

| 질문 | 변경 추적 컬럼으로 가능? |
|------|----------------------|
| 누가 마지막 수정? | O |
| 언제 마지막 수정? | O |
| 왜 수정? | O |
| 어떤 시스템에서? | O |
| **이전 값은?** | **X** |
| **특정 시점 값은?** | **X** |
| **총 몇 번 수정?** | **X** |
| **전체 변경 이력은?** | **X** |

현재 방식은 모두 "현재 테이블에 컬럼을 추가"하는 방식 → 마지막 변경만 남고, 이전 이력은 덮어씌워짐. 다음 편에서 **이력 테이블**로 해결한다.

---

## 면접 Q&A

**Q: 실무에서 데이터 변경 이력 관리가 중요한 이유는?**
A: 서비스가 운영되면 반드시 "이 데이터가 언제, 누가, 왜 변경됐나"는 질문이 생긴다. 가격 분쟁 시 주문 당시 가격 확인, 배송 오류 시 주소 변경 여부 확인, 금융 서비스의 감사 대응 등이 대표적이다. 이력이 없으면 고객 클레임 처리도, 규정 준수도 불가능하므로 이력 관리는 처음부터 설계에 포함해야 한다.

**Q: `change_type`과 `change_reason`을 둘 다 쓰는 이유는?**
A: `change_reason`만 쓰면 사람마다 "가격 변경", "할인 적용", "금액 수정" 등 다양하게 작성해 시스템이 구분할 수 없고 LIKE 검색도 정확하지 않다. `change_type`은 `PRICE_CHANGE` 같은 약속된 코드로 시스템이 정확하게 분류·검색할 수 있도록 한다. 역할 분리: `change_type`은 시스템을 위한 분류 코드, `change_reason`은 사람을 위한 상세 설명이다.

**Q: 등록 시에도 `updated_at`에 값을 채우는 이유는?**
A: NULL로 비워두면 "최근 변경순 정렬" 시 `COALESCE(updated_at, created_at)` 같은 함수를 매번 써야 해서 쿼리가 복잡해진다. 생성을 첫 번째 수정으로 간주해 처음부터 값을 채우면 조회 및 정렬 로직이 단순해진다.

**Q: stylink 프로젝트 적용 포인트는?**
A: stylink는 이미 `BaseEntity`에 `created_at`, `updated_at`을 적용 중이다. 여기에 `created_by`, `updated_by`를 추가하면 기본 추적 컬럼이 완성된다. 재고 상태(AVAILABLE→RESERVED→SOLD), 예약 상태, 주문 상태 등 핵심 비즈니스 컬럼이 변경되는 테이블에는 `change_type`, `change_reason`도 추가하는 것이 좋다. 단, "이전 값"(질문4)은 이력 테이블 설계 없이는 알 수 없다.
