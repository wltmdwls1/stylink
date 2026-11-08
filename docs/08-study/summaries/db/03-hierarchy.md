# 계층 구조 설계

> 데이터베이스 로드맵 | DB 설계 패턴

---

## 전체 흐름 한눈에 보기

```
계층 구조 필요 (카테고리, 조직도, 댓글, 폴더)
  ↓
인접 리스트 모델 (parent_id 자기 참조 FK)
  → 직속 자식/부모 조회: 쉬움
  → 모든 자손/조상 조회: 어려움
  ↓
해결 시도 1: 애플리케이션 반복 호출 → 네트워크 비용
해결 시도 2: JOIN 고정 깊이 → 가변 깊이 대응 불가
해결 시도 3: UNION ALL → 가변 깊이 대응 불가
  ↓
CTE 재귀 쿼리 (WITH RECURSIVE) — SQL 표준
  → 가변 깊이 지원, 단일 쿼리, 경로/깊이 계산 가능
  ↓
[성능 극한 필요 시] 폐쇄 테이블 모델
  → 모든 조상-자손 관계를 미리 저장 → 단순 JOIN으로 조회
```

---

## 인접 리스트 모델

각 행이 부모를 참조하는 자기 참조(Self-Referencing) 외래 키 방식.

```
category
  category_id (PK)
  name
  parent_id → category(category_id)  ← 자기 자신 참조

루트 = parent_id IS NULL
```

**장점**: 직관적, 추가/이동/삭제 간단, 저장 공간 효율적, FK로 참조 무결성 보장

**단점**: 깊이를 모르는 상황에서 모든 자손/조상 한 번에 조회 불가

---

## 계층 구조 조회 방법 비교

| 방법 | 장점 | 단점 |
|------|------|------|
| 앱 반복 호출 | 구현 간단 | 계층마다 쿼리 발생, 네트워크 비용 |
| JOIN 고정 깊이 | 단일 쿼리 | 깊이 고정 필요, 결과 중복 |
| UNION ALL | 레벨별 분리 | 깊이 고정, 쿼리 복잡 |
| **재귀 CTE** | **가변 깊이, 단일 쿼리** | MySQL 8.0 이상 필요 |

---

## CTE와 재귀 쿼리

**CTE(Common Table Expression)**: `WITH` 절로 정의하는 임시 이름 있는 결과 집합. SQL 표준 — MySQL, PostgreSQL, SQL Server, Oracle 공통 지원.

### 재귀 CTE 구조

```sql
WITH RECURSIVE cte_name AS (
    -- 기본 케이스 (Anchor): 재귀 시작점, 한 번만 실행
    SELECT ...

    UNION ALL

    -- 재귀 케이스 (Recursive): 이전 결과를 참조하여 반복
    SELECT ... FROM cte_name WHERE ...
)
SELECT * FROM cte_name;
```

실행 흐름: 기본 케이스 → 재귀 케이스 반복 (새 행 없으면 종료) → 전체 결과 합산 반환

### 모든 자손 조회

```sql
WITH RECURSIVE descendants AS (
    SELECT category_id, name, parent_id, 1 AS depth
    FROM category WHERE category_id = 1  -- 시작 노드

    UNION ALL

    SELECT c.category_id, c.name, c.parent_id, d.depth + 1
    FROM category c
    JOIN descendants d ON c.parent_id = d.category_id  -- 자식 찾기
)
SELECT * FROM descendants;
```

### 모든 조상 조회

```sql
WITH RECURSIVE ancestors AS (
    SELECT category_id, name, parent_id, 1 AS depth
    FROM category WHERE category_id = 7  -- 시작 노드 (노트북)

    UNION ALL

    SELECT c.category_id, c.name, c.parent_id, a.depth + 1
    FROM category c
    JOIN ancestors a ON c.category_id = a.parent_id  -- 부모 찾아 올라감
)
SELECT * FROM ancestors;
```

> 자손 조회: `c.parent_id = d.category_id` (자식의 parent_id = 부모 id)
> 조상 조회: `c.category_id = a.parent_id` (부모 id = 자식의 parent_id)

### 특정 깊이까지만 조회

```sql
-- 재귀 케이스에 WHERE d.depth < 2 추가
JOIN descendants d ON c.parent_id = d.category_id
WHERE d.depth < 2  -- "부모의 depth가 2 미만일 때만 자식을 찾아라"
```

**왜 `< N`이고 `<= N`이 아닌가**: WHERE는 '새로 만들 자식'이 아니라 '현재 기준이 되는 부모(d)'를 검사한다.
- `depth < 2` → 부모가 depth=1일 때만 자식 생성 → 결과는 depth=2까지
- `depth <= 2` → 부모가 depth=2일 때도 자식 생성 → 결과는 depth=3까지

### 경로 문자열 생성

```sql
CONCAT(cp.path, ' > ', c.name)
-- 결과: "전자제품 > 컴퓨터 > 노트북"
-- Breadcrumb 네비게이션에 활용
```

---

## 폐쇄 테이블 모델

**언제**: 카테고리 수만 개, 깊이 10단계 이상, 조회가 압도적으로 많을 때

**핵심 아이디어**: 조회 시점의 재귀를 저장 시점으로 옮김. 모든 조상-자손 관계를 미리 계산해 별도 테이블에 저장.

```
category_closure (노드 테이블)
  category_id (PK), name

category_path (경로 테이블)
  ancestor_id, descendant_id, depth
  PK (ancestor_id, descendant_id)
  -- 자기 자신: ancestor=descendant, depth=0
  -- 직속 부모/자식: depth=1
```

경로 데이터 예시 (노트북이 컴퓨터 하위, 컴퓨터가 전자제품 하위인 경우):
```
ancestor | descendant | depth
전자제품   노트북       2
컴퓨터     노트북       1
노트북     노트북       0
```

### 조회 (단순 JOIN, 재귀 없음)

```sql
-- 모든 자손
SELECT * FROM category_closure c
JOIN category_path p ON c.category_id = p.descendant_id
WHERE p.ancestor_id = 1;

-- 모든 조상
SELECT * FROM category_closure c
JOIN category_path p ON c.category_id = p.ancestor_id
WHERE p.descendant_id = 4;

-- 직속 자식만
WHERE p.ancestor_id = 1 AND p.depth = 1;
```

### 노드 추가 패턴

```sql
-- 1. 노드 추가
INSERT INTO category_closure VALUES (8, '게이밍노트북');

-- 2. 자기 자신 경로
INSERT INTO category_path VALUES (8, 8, 0);

-- 3. 부모(노트북=4)의 모든 조상 경로를 한번에 추가
INSERT INTO category_path (ancestor_id, descendant_id, depth)
SELECT ancestor_id, 8, depth + 1
FROM category_path WHERE descendant_id = 4;
```

---

## 모델 선택 기준

| 항목 | 인접 리스트 + CTE | 폐쇄 테이블 |
|------|-----------------|------------|
| 저장 공간 | 효율적 (n개 행) | 비효율적 (O(n²)) |
| 모든 자손 조회 | CTE 재귀 필요 | 단순 JOIN (매우 빠름) |
| 노드 추가/이동 | 매우 간단 | 경로 재계산 필요 |
| 구현 복잡도 | 낮음 | 높음 |
| **선택 기준** | **대부분의 경우** | **조회 압도, 데이터 방대** |

> 대부분의 경우 **인접 리스트 + CTE**로 충분. 성능 문제가 실제 발생할 때 폐쇄 테이블 도입 검토.
> 자주 변경되지 않는다면 **애플리케이션 캐시**도 좋은 대안.

---

## 면접 Q&A

**Q: 계층 구조를 DB에 저장하는 방법과 각각의 트레이드오프는?**
A: 대표적으로 인접 리스트 모델과 폐쇄 테이블 모델이 있다. 인접 리스트는 `parent_id`로 직속 부모만 참조하는 방식으로 구현이 단순하고 노드 추가/이동이 쉽다. 단점은 모든 자손/조상 조회에 재귀 쿼리가 필요하다는 것이다. 폐쇄 테이블은 모든 조상-자손 관계를 미리 계산해 별도 테이블에 저장하므로 조회가 단순 JOIN으로 빠르지만, 노드 추가/삭제/이동 시 경로 데이터를 함께 관리해야 해서 복잡하다.

**Q: 재귀 CTE에서 `WHERE d.depth < N`과 `<= N`의 차이는?**
A: WHERE 조건이 검사하는 대상은 '새로 만들 자식'이 아니라 '현재 기준이 되는 부모(d)'다. `depth < 2`는 부모의 depth가 1일 때만 자식을 찾으므로 결과는 depth=2까지만 생성된다. 반면 `depth <= 2`는 부모의 depth가 2일 때도 자식을 찾아 depth=3까지 생성된다. 즉 원하는 최대 깊이보다 `<`(미만)으로 조건을 걸어야 한다.

**Q: stylink 프로젝트에서 계층 구조가 필요한 곳은?**
A: stylink의 상품 카테고리(대분류→중분류→소분류)가 대표적인 케이스다. 카테고리 변경 빈도는 낮고 조회는 빈번하므로 인접 리스트 + CTE 조합이 기본이다. 특정 카테고리의 모든 상품 조회 시 재귀 CTE로 하위 카테고리 ID를 먼저 수집한 후 `WHERE category_id IN (...)` 패턴을 사용한다. 카테고리 구조가 거의 변경되지 않으므로 조회 결과는 로컬 캐시에 저장해 성능을 높일 수 있다.
