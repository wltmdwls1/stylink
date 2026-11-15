-- 2차 출장 서비스 지역 제한 (서울 한정)
-- 출장 가능 여부를 우편번호로 판단 (서울: 앞 두 자리 01~08)
ALTER TABLE reservation
    ADD COLUMN zip_code VARCHAR(10) NOT NULL COMMENT '출장지 우편번호 (서비스 지역 판단)' AFTER scheduled_at;
