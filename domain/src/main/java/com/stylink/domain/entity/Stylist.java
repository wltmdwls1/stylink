package com.stylink.domain.entity;

import com.stylink.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stylist")
public class Stylist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    // AES256 암호화 (양방향)
    @Column(name = "phone", nullable = false, length = 255)
    private String phone;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;
}
