package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseLogEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 상품 (append-only — 생성 후 변경 없음)
 * 주문 시점의 가격을 price에 스냅샷으로 저장
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_item")
public class OrderItem extends BaseLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    // 주문 시점 상품명 스냅샷
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 주문 시점 가격 스냅샷
    @Column(name = "price", nullable = false)
    private Long price;
}
