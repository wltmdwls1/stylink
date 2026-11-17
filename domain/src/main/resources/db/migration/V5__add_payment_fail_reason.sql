-- 1차 결제 실패 사유 저장
-- 결제 실패는 예외 롤백이 아니라 Payment FAILED 행으로 정상 저장 → 실패 이유를 BO에서 바로 확인
-- 원문(PG 요청/응답)은 outbound_api_log에 별도 저장
ALTER TABLE payment
    ADD COLUMN fail_code    VARCHAR(50)  NULL COMMENT '실패 코드 (PG 에러코드 그대로 / PG 응답 없으면 우리 코드: PG_CONNECTION_FAILED, PG_TIMEOUT)' AFTER pg_transaction_id,
    ADD COLUMN fail_message VARCHAR(255) NULL COMMENT '실패 메시지 (PG 응답 메시지 또는 우리 설명)' AFTER fail_code;
