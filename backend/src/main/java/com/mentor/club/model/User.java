package com.mentor.club.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

public class User {
    @Id
    @Getter
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;

    @Getter
    @Setter
    @Column(name = "name")
    @JsonProperty("name")
    private String name;

    @Getter
    @Setter
    @Column(name = "username")
    @JsonProperty("username")
    private String username;

    @Getter
    @Setter
    @Column(name = "email")
    @JsonProperty("email")
    private String email;

    @Getter
    @Setter
    @Column(name = "hashedPassword")
    @JsonProperty("hashedPassword")
    private String hashedPassword;

    @Getter
    @Setter
    @Column(name = "thumbnailBase64")
    @JsonProperty("thumbnailBase64")
    private String thumbnailBase64;

    @Getter
    @Setter
    @Column(name = "status")
    @JsonProperty("status")
    private UserStatus userStatus = UserStatus.CREATED_UNCONFIRMED_EMAIL;
}
