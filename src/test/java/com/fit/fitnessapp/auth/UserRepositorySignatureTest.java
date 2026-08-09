package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.ParameterizedType;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositorySignatureTest {

    @Test
    void userRepositoryUsesLongAsEntityIdType() {
        ParameterizedType jpaRepositoryType = Arrays.stream(UserRepository.class.getGenericInterfaces())
                .filter(ParameterizedType.class::isInstance)
                .map(ParameterizedType.class::cast)
                .filter(type -> type.getRawType().equals(JpaRepository.class))
                .findFirst()
                .orElseThrow();

        assertThat(jpaRepositoryType.getActualTypeArguments()[1]).isEqualTo(Long.class);
    }
}
