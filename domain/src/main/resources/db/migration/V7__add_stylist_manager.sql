-- 2차 스타일리스트 담당 매니저 (A-5 후속)

-- 스타일리스트마다 소속 매니저 1명 고정 (예약할 때마다 바뀌지 않음)
--   - 반납 지연 알림 batch: 해당 스타일리스트 + 담당 매니저에게 알림
--   - MANAGER 권한 Admin만 지정 가능 (등록/수정 로직에서 검증)
--   - 매니저 퇴사/팀 이동 시 BO에서 수정 → 이후 알림은 새 매니저에게 (소속 변경 이력은 확장 포인트)
--   - 지역(강남/잠실 등) 테이블은 두지 않음 — 지역별 운영은 매니저 소속 데이터로 표현 (지역 관리는 확장 포인트)
--   - NOT NULL: stylist 데이터가 없는 상태에서 추가 (2026-11-21 확인)
ALTER TABLE stylist
    ADD COLUMN manager_id BIGINT NOT NULL COMMENT '담당 매니저 (admin.id, MANAGER 권한) — 반납 지연 알림 대상' AFTER id,
    ADD KEY idx_stylist_manager_id (manager_id),
    ADD CONSTRAINT fk_stylist_manager FOREIGN KEY (manager_id) REFERENCES admin (id);
