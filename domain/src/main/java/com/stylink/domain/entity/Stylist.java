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

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    // BCrypt 단방향 해시 — bo-api 로그인용 (Admin과 별도 계정 체계)
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    // AES256(CBC/GCM) 암호화 — 복호화 가능, 화면 표시용
    @Column(name = "phone", nullable = false, length = 255)
    private String phone;

    // SHA-256 해시 — 단방향, 중복체크/검색/인증용
    @Column(name = "phone_hash", nullable = false, unique = true, length = 64)
    private String phoneHash;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;
}
