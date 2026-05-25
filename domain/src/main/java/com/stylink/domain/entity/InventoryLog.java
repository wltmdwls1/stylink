package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseLogEntity;
import com.stylink.domain.enums.InventoryChangeReason;
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

    // 이벤트 타입 Enum (필수)
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private InventoryChangeReason reason;

    // 추가 맥락 (자유텍스트, 선택)
    @Column(name = "description", length = 255)
    private String description;
}
