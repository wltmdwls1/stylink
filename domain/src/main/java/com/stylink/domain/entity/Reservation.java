package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseEntity;
import com.stylink.domain.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 예약 (2차 O2O 출장 스타일링)
 * 고객이 가능 일정 선택 시 즉시 CONFIRMED — StylistSchedule BOOKED + 재고 RESERVED
 * IN_PROGRESS 시점: 재고 IN_TRANSIT (이후 취소 불가)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reservation")
public class Reservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "stylist_id", nullable = false)
    private Long stylistId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    // 출장 예정 일시 (CONFIRMED 시점에 확정)
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    // 출장지 우편번호 — 서비스 지역 판단 (현재 서울 한정: 앞 두 자리 01~08)
    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;

    // 출장 주소
    @Column(name = "address", length = 255)
    private String address;

    // 출장비 (V8) — 예약 시점 금액 저장 (일반 20,000 / VIP 1회 무료면 0), 현장 결제 때 옷값과 합산 / 누적 구매금액 제외
    @Column(name = "travel_fee", nullable = false)
    private Long travelFee;

    // 출장 시작 시각 — IN_PROGRESS 전환 시 기록 (V6, styling_session에서 이동)
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    // 현장 완료 시각 — COMPLETED 전환 시 기록 (V6) / 반납 지연 알림 배치의 "완료 후 24시간" 기준
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
