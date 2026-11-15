package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseEntity;
import com.stylink.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 주문 (1차 온라인 주문 중심)
 * 테이블명: orders (order는 SQL 예약어)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 고객 노출용 주문번호 (ORD+날짜8자리+시퀀스5자리)
    @Column(name = "order_number", nullable = false, unique = true, length = 16)
    private String orderNumber;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal totalAmount;

    // 낙관적 락 — 결제(PENDING→PAID)와 만료 배치(PENDING→FAILED)가 동시에 바꾸면 늦은 쪽 실패
    // 주의: 벌크 UPDATE(JPQL/네이티브)는 @Version을 확인하지 않음 → 배치는 엔티티 단위로 변경
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
