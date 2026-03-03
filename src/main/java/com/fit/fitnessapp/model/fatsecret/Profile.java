package com.fit.fitnessapp.model.fatsecret;

import com.fit.fitnessapp.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "profiles")
@Getter
@Setter
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String goal_weight_kg; //"85.0000",
    private String height_cm;// "186.00",
    private String height_measure;// "Cm",
    private String last_weight_date_int;// "20497",
    private String last_weight_kg;//"89.1000",
    private String weight_measure; //"Kg"

    @OneToOne
    @MapsId // Связывает ID профиля с ID пользователя
    @JoinColumn(name = "user_id")
    private User user;
}
