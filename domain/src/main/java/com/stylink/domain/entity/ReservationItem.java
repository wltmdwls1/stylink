package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 예약 상품 (2차 O2O 출장 스타일링) — 예약 ↔ 재고 연결
 * 1차 OrderItem과 같은 역할: 예약 1건에 재고 여러 벌 연결
 *
 * 예약 확정 시 찜 상품마다 AVAILABLE 재고 1벌 RESERVED + 이 행 저장
 * 반납 확인 대상: 예약 COMPLETED + 재고 IN_TRANSIT + returnedAt IS NULL
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reservation_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reservation_id", "inventory_id"}))
public class ReservationItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    // 반납 확인 시각 — NULL이면 미반납
    // 같은 재고가 반납 후 다른 예약으로 다시 나가도 옛 예약 행이 반납 대상으로 잡히지 않게 구분
    @Column(name = "returned_at")
    private LocalDateTime returnedAt;
}
