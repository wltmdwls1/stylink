package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseEntity;
import com.stylink.domain.enums.OrderStatus;
import com.stylink.domain.enums.OrderType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 주문 — 1차 온라인(ONLINE) + 2차 출장 현장(FIELD) 공용
 * 테이블명: orders (order는 SQL 예약어)
 * FIELD 주문: 배송 없음, PENDING → PAID(끝) / FAILED, 반품 불가(확장 포인트)
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

    // 주문 유형 (V6) — 결제대기 만료 배치는 ONLINE만 대상
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    // [2차] 현장 주문일 때만 — 어느 출장 예약에서 나온 주문인지 (ONLINE은 null)
    @Column(name = "reservation_id")
    private Long reservationId;

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
