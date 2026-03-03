package com.fit.fitnessapp.model.fatsecret;

import com.fit.fitnessapp.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "fatsecret_connections")
@Getter
@Setter
public class UserFatsecretConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String accessToken;
    private String accessTokenSecret;

    @CreationTimestamp
    private LocalDateTime createdAt;

}
