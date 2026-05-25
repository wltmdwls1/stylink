package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseLogEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 외부 API 수신 로그 (인바운드, append-only)
 * 외부 시스템으로부터 수신된 콜백/이벤트 기록 (배송 상태 콜백 등)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "inbound_api_log")
public class InboundApiLog extends BaseLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 연관 주문 ID
    @Column(name = "order_id")
    private Long orderId;

    // 호출 출처 시스템 (DELIVERY, PG 등)
    @Column(name = "api_source", nullable = false, length = 30)
    private String apiSource;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "status_code")
    private Integer statusCode;
}
