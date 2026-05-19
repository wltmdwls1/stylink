package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member_profile")
public class MemberProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 단방향 FK
    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "height")
    private Integer height;

    @Column(name = "weight")
    private Integer weight;

    // 상의 사이즈 (S, M, L, XL 등)
    @Column(name = "top_size", length = 10)
    private String topSize;

    // 하의 사이즈 (28, 30 등)
    @Column(name = "bottom_size", length = 10)
    private String bottomSize;
}
