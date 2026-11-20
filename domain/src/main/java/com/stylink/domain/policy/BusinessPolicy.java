package com.stylink.domain.policy;

/**
 * 비즈니스 정책 값 모음 (fo-api / bo-api / batch 공통)
 *
 * - 환경(개발/운영)마다 다른 값이 아니므로 yml이 아닌 domain 모듈에 둠
 * - 등급마다 다른 값(출장 옷 개수)은 MemberGrade Enum에 둠
 * - 확장 포인트: 운영자가 바꾸는 금액(출장비, VIP 기준 등)은 DB 정책 테이블 + BO 화면 수정 + 변경 이력으로 이전
 *   (예약에는 예약 시점 출장비를 reservation.travel_fee로 저장하므로 이전해도 기존 예약 영향 없음)
 */
public final class BusinessPolicy {

    private BusinessPolicy() {
    }

    // ===== 공통 (1차/2차) =====

    /** VIP 기준 누적 구매금액 (원) — 1차 구매확정 / 2차 현장 결제 시 반영, 출장비 제외 */
    public static final long VIP_THRESHOLD_AMOUNT = 1_000_000L;

    // ===== 1차 (온라인) =====

    /** 결제대기 만료 (분) — 서버 판단 기준 (화면 타이머는 10분, 5분 여유) */
    public static final int PAYMENT_EXPIRE_MINUTES = 15;

    /** 반품 가능 기간 (일) — 배송완료(delivery.delivered_at) 기준, 지나면 batch 구매확정 */
    public static final int RETURN_PERIOD_DAYS = 7;

    // ===== 2차 (출장) =====

    /** 기본 출장비 (원) — 현장 후불, 예약 시점 금액을 reservation.travel_fee에 저장 */
    public static final long TRAVEL_FEE = 20_000L;

    /** VIP 무료 출장 취소 시 무료 기회 복구 기준 (시간) — 출장 이 시간 전까지 고객 취소면 복구, 이내면 소진 */
    public static final int FREE_TRAVEL_RESTORE_HOURS = 24;

    /** 반납 지연 알림 기준 (시간) — 예약 COMPLETED 후 이 시간이 지나도 반납 확인 안 된 재고 → 스타일리스트 + 담당 매니저 알림 */
    public static final int RETURN_DELAY_ALERT_HOURS = 24;
}
