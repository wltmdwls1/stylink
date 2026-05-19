package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseLogEntity;
import com.stylink.domain.enums.InventoryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 상태 변경 이력 (append-only)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "inventory_log")
public class InventoryLog extends BaseLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private InventoryStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private InventoryStatus newStatus;

    // 변경 사유 (주문번호, 예약번호, 배치 만료 등)
    @Column(name = "reason", length = 255)
    private String reason;
}
