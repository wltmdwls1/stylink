package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 스타일리스트 가능 일정 (2차 O2O 출장 스타일링)
 * 예약 확정 시 is_booked = true (동시성 제어 필수 — 중복 예약 방지)
 * 예약 취소 시 is_booked = false 복구
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stylist_schedule")
public class StylistSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stylist_id", nullable = false)
    private Long stylistId;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "is_booked", nullable = false)
    private boolean isBooked = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
