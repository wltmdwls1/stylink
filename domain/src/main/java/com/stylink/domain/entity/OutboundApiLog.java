package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseLogEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 외부 API 호출 로그 (아웃바운드, append-only)
 * PG / 배송 / 알림 / 인증 Mock 호출 기록
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "outbound_api_log")
public class OutboundApiLog extends BaseLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 연관 주문 ID
    @Column(name = "order_id")
    private Long orderId;

    // 호출 대상 시스템 (PG, DELIVERY, NOTIFICATION, AUTH)
    @Column(name = "api_type", nullable = false, length = 30)
    private String apiType;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "status_code")
    private Integer statusCode;
}
