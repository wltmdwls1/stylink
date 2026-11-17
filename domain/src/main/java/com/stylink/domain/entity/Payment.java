package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseEntity;
import com.stylink.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 결제 (Mock 기반)
 * SHIPPED 이전 → 취소(CANCELLED)
 * SHIPPED 이후 → 환불(REFUNDED)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    // PG사 거래 ID (Mock)
    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    // [1차] 결제 실패 사유 — FAILED일 때만 값 있음 (V5)
    // PG 거절: PG 에러코드 그대로 / PG 응답 없음: 우리 코드 (PG_CONNECTION_FAILED, PG_TIMEOUT)
    // PG 요청/응답 원문은 outbound_api_log에 별도 저장
    @Column(name = "fail_code", length = 50)
    private String failCode;

    @Column(name = "fail_message", length = 255)
    private String failMessage;
}
