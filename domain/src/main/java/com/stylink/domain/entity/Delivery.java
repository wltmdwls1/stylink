package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseEntity;
import com.stylink.domain.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배송 (Mock 기반, 1차 온라인 전용)
 * RETURNED 시 → Order 상태 변경 + 재고 복구 + 환불 처리 연계
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "delivery")
public class Delivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "address_detail", length = 100)
    private String addressDetail;

    // 운송장 번호 (Mock)
    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;
}
