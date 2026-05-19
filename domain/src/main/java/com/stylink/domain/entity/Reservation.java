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
 * CONFIRMED 시점: 스타일리스트 배정 + 날짜 확정 + 재고 RESERVED
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

    // CONFIRMED 시점에 배정됨 (nullable: PENDING 상태에서는 미배정)
    @Column(name = "stylist_id")
    private Long stylistId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    // 출장 예정 일시 (CONFIRMED 시점에 확정)
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    // 출장 주소
    @Column(name = "address", length = 255)
    private String address;
}
