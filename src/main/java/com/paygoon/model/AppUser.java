package com.paygoon.model;


import jakarta.persistence.Id;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class AppUser {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String email;

    private String passwordHash;

    private String nickname;

    private String rol;

    @Enumerated(EnumType.STRING)
    private LoginType loginType = LoginType.EMAIL;

    private boolean verified = false;

    private boolean premium = false;

    private String documentType;

    private String documentNumber;

    public enum LoginType {
        EMAIL, GOOGLE, APPLE
    }
}

