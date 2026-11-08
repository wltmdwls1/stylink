# 통계 테이블 설계

> 데이터베이스 로드맵 | DB 설계 패턴

---

## 전체 흐름 한눈에 보기

```
원본 GROUP BY (주니어 실수)
  1억 건 orders 테이블 → CPU/메모리 과다 → 서비스 장애 전파
  ↓
[해결] 일별 통계 테이블 (daily_sales_stats)
  하루 1행 → 10년 = 3,650행 → 조회 0.001초
  배치: 새벽에 D-1일 데이터 집계 후 INSERT
  단점: 실시간성 없음
  ↓
[확장] 주간/월간은 일별 테이블 재집계
  3,650행 GROUP BY → 부하 거의 0
  별도 weekly/monthly 테이블 불필요
  ↓
[실시간 요구] 하이브리드 설계
  과거: daily_sales_stats UNION ALL 오늘: orders 원본
  핵심: order_date 인덱스가 반드시 있어야 함
  ↓
[안전성] 멱등성 설계
  비멱등: UPDATE SET amount = amount + 50000 → 재실행 시 뻥튀기
  멱등: DELETE & INSERT → 100번 재실행해도 결과 동일
  ↓
[초대박 서비스] 마이크로 배치
  intraday_sales_stats → 5분마다 갱신
  조회: daily UNION ALL intraday (원본 아예 안 건드림)
  ↓
[최적화] UPSERT (마이크로 배치용)
  INSERT ... ON DUPLICATE KEY UPDATE
  DELETE+INSERT보다 2~3배 빠름, 멱등성 보장
```

---

## 원본 직접 집계가 위험한 이유

```sql
-- 주니어 개발자의 실수: 원본 테이블 직접 집계
SELECT DATE(order_date) as stat_date, COUNT(*), SUM(total_amount)
FROM orders
WHERE order_status = 'COMPLETED'
GROUP BY DATE(order_date);
```

개발 서버에서 0.001초면 나온다 → 배포 → 데이터 1억 건 쌓임 → 문제 발생.

| 문제 | 설명 |
|------|------|
| 성능 저하 | `GROUP BY DATE()` 는 인덱스를 제대로 활용하기 어려움 |
| DB 부하 | 집계 쿼리 실행 중 CPU/메모리 폭증 |
| 서비스 장애 전파 | 관리자가 조회 버튼 누르는 순간 고객 주문/결제 INSERT도 느려짐 |

> "통계 조회는 새벽에만 하세요" / "조회 버튼 누르면 DB 뻗으니 누르지 마세요"  
> — 이런 슬픈 공지가 실제로 생긴다.

---

## 통계 테이블 설계 (요약 테이블)

핵심 아이디어: **미리 다 계산해서 저장해두기**

```sql
CREATE TABLE daily_sales_stats (
    stat_date           DATE   NOT NULL,
    total_order_count   INT    NOT NULL DEFAULT 0,
    total_sales_amount  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (stat_date)
);
```

- `stat_date`가 PK → 하루에 딱 1행
- 10년이 지나도 **3,650행** → 원본이 1억 건이어도 조회 0.001초

### 배치 실행 (새벽, 멱등하게)

```sql
-- 1단계: 해당 날짜 기존 데이터 삭제
DELETE FROM daily_sales_stats WHERE stat_date = '2026-01-01';

-- 2단계: 원본에서 재집계 후 INSERT (트랜잭션으로 묶기)
INSERT INTO daily_sales_stats (stat_date, total_order_count, total_sales_amount)
SELECT DATE(order_date), COUNT(*), SUM(total_amount)
FROM orders
WHERE order_date >= '2026-01-01 00:00:00'
  AND order_date < '2026-01-02 00:00:00'
  AND order_status = 'COMPLETED'
GROUP BY DATE(order_date);
```

실행 시각: 글로벌 서비스가 아니라면 새벽 3~5시 (트래픽 최저점).

**장점**: 조회 속도 극적 개선, 무거운 집계 부하가 새벽에만 1회 발생  
**단점**: 실시간성 없음 (어제까지 데이터만), 배치 실패 시 갱신 안 됨

---

## 주간·월간 통계: 별도 테이블 불필요

일별 테이블(3,650행)을 `GROUP BY`하는 것은 DB에 거의 부하가 없다. `weekly_sales_stats`, `monthly_sales_stats` 별도 테이블을 만들지 말 것.

```sql
-- 월별 통계 (일별 테이블에서 재집계)
SELECT DATE_FORMAT(stat_date, '%Y-%m') AS month,
       SUM(total_order_count), SUM(total_sales_amount)
FROM daily_sales_stats
WHERE stat_date >= '2025-11-01' AND stat_date < '2026-02-01'
GROUP BY DATE_FORMAT(stat_date, '%Y-%m');

-- 연별 통계
SELECT DATE_FORMAT(stat_date, '%Y') AS year,
       SUM(total_order_count), SUM(total_sales_amount)
FROM daily_sales_stats
GROUP BY DATE_FORMAT(stat_date, '%Y');
```

> **원본(1억 건) → 일별 통계(수천 건)**: 획기적으로 줄어든다.  
> **일별 통계 → 월별/연간**: 이미 충분히 작다. 쿼리로 해결하라.

---

## 실시간 통계: 하이브리드 설계

"오늘 지금 이 순간 매출이 궁금해요" → daily_stats에는 오늘 데이터 없음.

**전략**: 과거는 통계 테이블, 오늘은 원본 테이블에서 `UNION ALL`로 결합.

```sql
-- 과거 데이터 (통계 테이블, 매우 빠름)
SELECT stat_date, total_order_count, total_sales_amount
FROM daily_sales_stats
WHERE stat_date >= '2026-01-01' AND stat_date < '2026-01-04'

UNION ALL

-- 오늘 실시간 데이터 (원본 테이블, 하루치만 스캔)
SELECT DATE(order_date), COUNT(*), SUM(total_amount)
FROM orders
WHERE order_date >= '2026-01-04 00:00:00'
  AND order_status = 'COMPLETED'
GROUP BY DATE(order_date);
```

**핵심 전제**: `orders.order_date` 에 반드시 인덱스가 있어야 한다.

```sql
CREATE INDEX idx_orders_order_date ON orders (order_date);
```

인덱스 없이 1억 건 테이블에서 오늘 데이터 찾으면 → Full Table Scan → 서비스 장애.

오늘 하루치 데이터는 수만~수십만 건 수준 → 인덱스 + 집계로 DB가 충분히 감당 가능.

---

## 멱등성(Idempotency) 설계

### 비멱등적 방식 (금지)

```sql
-- 위험: 재실행할 때마다 값이 쌓임
UPDATE daily_sales_stats
SET total_sales_amount = total_sales_amount + 50000,
    total_order_count = total_order_count + 1
WHERE stat_date = '2026-01-01';
-- 1회 실행: 50,000원 ✅
-- 2회 실행: 100,000원 ❌ (매출 뻥튀기)
```

배치가 네트워크 오류로 중단되면 운영자가 재실행 → 데이터 오염.

### 멱등적 방식: DELETE & INSERT

```sql
-- 1. 먼저 지운다 (데이터가 없어도 괜찮음)
DELETE FROM daily_sales_stats WHERE stat_date = '2026-01-01';

-- 2. 원본에서 다시 계산해서 넣는다
INSERT INTO daily_sales_stats ...
SELECT DATE(order_date), COUNT(*), SUM(total_amount) FROM orders
WHERE order_date >= '2026-01-01' ... AND order_status = 'COMPLETED'
GROUP BY DATE(order_date);
```

100번 재실행해도 결과는 항상 `orders` 테이블의 현재 상태와 일치.

**멱등성의 힘**:
- "배치 실패했나요?" → **다시 돌리세요**
- "데이터 꼬였나요?" → **다시 돌리세요**
- "3일 전 주문 취소됐나요?" → **그 날짜 배치 다시 돌리세요**

### 환불 발생 시나리오

1월 1일 주문(10,000원)이 1월 4일에 취소됨 → 1월 1일 통계에 여전히 50,000원  
→ 1월 1일 배치 재실행 → DELETE & INSERT → 40,000원으로 정확히 갱신.  
복잡한 `UPDATE ... -10000` 로직 없이 "재실행" 하나로 해결.

---

## 마이크로 배치

하루 주문 100만~1000만 건 → 오늘 데이터 실시간 집계도 DB에 부담.

**전략**: 당일 통계용 별도 테이블을 만들고 **5분마다 갱신**.

```sql
CREATE TABLE intraday_sales_stats (
    today_date          DATE     NOT NULL,
    total_order_count   INT      NOT NULL DEFAULT 0,
    total_sales_amount  BIGINT   NOT NULL DEFAULT 0,
    updated_at          DATETIME,            -- 배치 마지막 실행 시각
    PRIMARY KEY (today_date)
);
```

마이크로 배치 실행 (5분마다):
```sql
START TRANSACTION;
DELETE FROM intraday_sales_stats WHERE today_date = '2026-01-02';
INSERT INTO intraday_sales_stats (today_date, total_order_count, total_sales_amount, updated_at)
SELECT DATE(order_date), COUNT(*), SUM(total_amount), NOW()
FROM orders
WHERE order_date >= '2026-01-02' AND order_date < '2026-01-03'
  AND order_status = 'COMPLETED'
GROUP BY DATE(order_date);
COMMIT;
```

트랜잭션으로 묶으면 DELETE 후 INSERT 완료 전 짧은 순간에도 다른 사용자는 삭제된 상태를 볼 수 없음.

**조회 쿼리** (원본 orders 전혀 건드리지 않음):
```sql
SELECT stat_date, total_order_count, total_sales_amount FROM daily_sales_stats
WHERE stat_date >= '2026-01-01'
UNION ALL
SELECT today_date, total_order_count, total_sales_amount FROM intraday_sales_stats
WHERE today_date = '2026-01-02';
```

`updated_at` 컬럼으로 사용자에게 "기준 시간: 14:05" 표시 가능.

| 항목 | 내용 |
|------|------|
| 장점 | 원본 조회 완전 차단, 수백 명 동시 조회에도 DB 무관 |
| 단점 | 최대 5분 지연, 스케줄러+별도 테이블 관리 필요 |

---

## UPSERT 최적화 (`INSERT ... ON DUPLICATE KEY UPDATE`)

마이크로 배치에서 DELETE & INSERT를 반복하면:

| 문제 | 설명 |
|------|------|
| 인덱스 파괴/재구축 | 인덱스 3개 × (DELETE + INSERT) = 6번 인덱스 조작 |
| Redo/Undo Log 과다 | 삭제 로그 + 삽입 로그 모두 기록 |
| 페이지 단편화 | 잦은 삭제·삽입 → 데이터 저장 공간에 구멍 발생 |

### UPSERT 원리

**없으면 INSERT, 있으면 UPDATE** — 행(Row)을 파괴하지 않고 값만 덮어씀.

```sql
-- MySQL 8.0.20+ 방식 (서브쿼리 별칭 사용)
INSERT INTO intraday_sales_stats (today_date, total_order_count, total_sales_amount, updated_at)
SELECT * FROM (
    SELECT DATE(order_date) AS today_date,
           COUNT(*) AS total_order_count,
           SUM(total_amount) AS total_sales_amount,
           NOW() AS updated_at
    FROM orders
    WHERE order_date >= '2026-01-02' AND order_status = 'COMPLETED'
    GROUP BY DATE(order_date)
) AS new_stats
ON DUPLICATE KEY UPDATE
    total_order_count  = new_stats.total_order_count,
    total_sales_amount = new_stats.total_sales_amount,
    updated_at         = new_stats.updated_at;
```

> MySQL 8.0.20 이전: `ON DUPLICATE KEY UPDATE col = VALUES(col)` (Deprecated)  
> MySQL 8.0.20+: 서브쿼리에 별칭(`new_stats`)을 붙여 참조

**UPSERT 장점**:
- **원자성**: DELETE와 INSERT 사이에 틈이 없음 → 데이터 유실 없음
- **멱등성**: 덮어쓰기 방식 → 100번 실행해도 결과 동일
- **성능**: 인덱스 보존, 최소 로그, 페이지 재사용

### DELETE+INSERT vs UPSERT 성능 비교

| 상황 | DELETE+INSERT | UPSERT |
|------|--------------|--------|
| 인덱스 없는 테이블 | 비슷 | 비슷 |
| 인덱스 3~5개 (일반) | 기준 | **2~3배 빠름** |
| 동시성 높은 환경 | 기준 | **10배+ 빠름** |

DELETE+INSERT는 찰나지만 데이터 없는 Gap을 만들어 Lock Wait 유발.

### 언제 무엇을 쓸까?

| 상황 | 방식 | 이유 |
|------|------|------|
| 일별 새벽 배치 (1회) | **DELETE & INSERT** | 명확함, 디버깅 쉬움, 1회 비효율은 무관 |
| 마이크로 배치 (빈번) | **UPSERT** | 성능, DB 부하 최소화 |

---

## [심화] 증분 업데이트 (극한 상황)

하루 주문 1억 건 → 재집계 10분 → 5분 배치 주기로는 불가능할 때.

```sql
-- last_order_id 기억 → 새 데이터만 읽어서 누적
SELECT ... FROM orders WHERE order_id > last_order_id;
UPDATE intraday SET count = count + new_count;  -- 누적!
UPDATE batch_meta SET last_order_id = max_id;
```

**주의**: `count = count + new_count`는 비멱등. 배치 중단 후 재실행 시 데이터 오염 위험.  
정말 극한의 상황이 아니라면 "오늘 데이터 전체 재집계(UPSERT)" 방식 권장.

---

## 면접 Q&A

**Q: 대용량 서비스에서 통계를 어떻게 설계하는가?**
A: 원본 테이블을 직접 GROUP BY하는 방식은 데이터가 수천만 건 이상이면 CPU/메모리를 폭주시켜 서비스 장애로 이어진다. 실무에서는 별도 요약 테이블(`daily_sales_stats`)을 만들고 새벽 배치로 D-1일 데이터를 미리 집계해 저장한다. 하루 1행만 생성되므로 10년치도 3,650행이고 조회가 0.001초에 끝난다. 실시간 데이터가 필요하면 통계 테이블(과거)과 원본 테이블(오늘)을 UNION ALL로 합치는 하이브리드 전략을 쓴다.

**Q: 월간/연간 통계 테이블을 따로 만들어야 하는가?**
A: 대부분의 경우 불필요하다. 일별 통계 테이블은 10년치가 3,650행에 불과하므로 이를 GROUP BY로 월별/연별 집계해도 부하가 거의 없다. 별도 테이블을 만들면 관리 포인트만 늘어난다. 서비스 규모가 글로벌 급이어서 일별 통계 자체가 수억 건이 되는 경우에만 월별 테이블을 고려한다.

**Q: 통계 배치에서 멱등성이란 무엇이고 왜 중요한가?**
A: 멱등성은 같은 연산을 1번 실행하든 100번 실행하든 결과가 항상 같아야 하는 성질이다. 배치는 언제든 실패할 수 있고 재실행이 필요한데, `UPDATE ... SET amount = amount + 50000` 같은 누적 방식은 재실행 시마다 값이 쌓여 데이터가 오염된다. DELETE & INSERT 방식은 기존 데이터를 먼저 지우고 원본에서 재집계해 넣으므로 몇 번을 재실행해도 결과가 원본 상태와 항상 일치한다. 환불 같은 과거 데이터 변경도 해당 날짜 배치를 재실행하는 것만으로 해결된다.

**Q: UPSERT가 DELETE+INSERT보다 나은 이유는?**
A: DELETE+INSERT는 멀쩡한 행을 파괴하고 다시 만드는 과정에서 인덱스 재구축, Undo/Redo 로그 과다, 페이지 단편화가 발생한다. UPSERT(`INSERT ... ON DUPLICATE KEY UPDATE`)는 행을 파괴하지 않고 값만 덮어쓰므로 인덱스가 보존되고 로그가 최소화된다. 인덱스가 3~5개인 일반 테이블에서 2~3배, 동시성이 높은 환경에서는 10배 이상 빠르다. 새벽 일별 배치는 명확성을 위해 DELETE+INSERT를, 5분마다 도는 마이크로 배치는 성능을 위해 UPSERT를 사용한다.

**Q: stylink 프로젝트 적용 포인트는?**
A: stylink의 1차 온라인 쇼핑몰에서 관리자 매출 대시보드를 구현할 때, `daily_sales_stats` 테이블을 만들고 `batch` 모듈에서 새벽 배치를 실행하는 구조가 적합하다. `order_date`에 인덱스를 걸어두면 하이브리드 방식으로 실시간 조회도 가능하다. 2차 출장 스타일링 서비스의 예약 통계(일별 예약 건수, 매출)도 동일 패턴으로 설계할 수 있다. 배치 구현 시 DELETE & INSERT 패턴으로 멱등성을 보장해 재실행 시 데이터 오염이 없도록 한다.
