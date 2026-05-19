package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseEntity;
import com.stylink.domain.enums.InventoryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 (⭐ 핵심 엔티티)
 * 낙관적 락(@Version)으로 동시 주문 시 중복 RESERVED 방지
 *
 * 1차(온라인): AVAILABLE → RESERVED → SOLD
 * 2차(출장):   AVAILABLE → RESERVED → IN_TRANSIT → SOLD
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "inventory")
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryStatus status;

    // 낙관적 락 — 동시 주문 시 한 건만 RESERVED 성공, 나머지 실패
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
