package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseEntity;
import com.stylink.domain.enums.AuthStatus;
import com.stylink.domain.enums.MemberGrade;
import com.stylink.domain.enums.MemberStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    // BCrypt 암호화 (단방향)
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    // AES256 암호화 (양방향)
    @Column(name = "phone", nullable = false, length = 255)
    private String phone;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_status", nullable = false, length = 20)
    private AuthStatus authStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 20)
    private MemberGrade grade;

    // 누적 구매 금액 (VIP 등급 판단 기준)
    @Column(name = "total_purchase_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal totalPurchaseAmount;
}
