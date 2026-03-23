package com.fit.fitnessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.model.dto.LoginRequest;
import com.fit.fitnessapp.model.dto.RegisterRequest;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.Role;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest // Поднимает всё приложение целиком
@AutoConfigureMockMvc // Настраивает MockMvc для отправки запросов
@Transactional // Важно! Откатывает изменения в БД после каждого теста
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository; // Реальный репозиторий для проверки БД

    @Autowired
    private PasswordEncoder passwordEncoder; // Чтобы проверить хеширование пароля

    @Autowired
    private JwtCore jwtCore;

    private ObjectMapper objectMapper = new ObjectMapper();


    @BeforeEach
    void setUp(){

        User registerUser = new User();
        registerUser.setUsername("existing_user");
        registerUser.setEmail("existing_user@realdb.com");
        registerUser.setPassword(passwordEncoder.encode("somepass"));
        registerUser.setRoles(Set.of(Role.USER));

        User vipUser = new User();
        vipUser.setUsername("vip_user");
        vipUser.setEmail("vip_email");
        vipUser.setPassword(passwordEncoder.encode("vip_password"));
        vipUser.setRoles(Set.of(Role.USER,Role.ADMIN));


        userRepository.save(registerUser);
        userRepository.save(vipUser);

    }

    @Test
    void login_shouldFailWithExceptionUSERNAME() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("UserNameNotExists");
        loginRequest.setPassword("DOESNT MATTER");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Wrong login or password"));
    }

    @Test
    void login_shouldFailWithExceptionPASS() throws Exception{

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("existing_user");
        loginRequest.setPassword("WRONG-PASS");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Wrong login or password"));
    }

    @Test
    void login_shouldWorkAndReturnToken() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("existing_user");
        loginRequest.setPassword("somepass");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())))
                // Простейшая проверка: начинается с eyJ (стандарт для JWT)
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("eyJ")))
                // ИЛИ проверка регуляркой (3 части разделенные точками)
                .andExpect(content().string(matchesPattern("^[\\w-]*\\.[\\w-]*\\.[\\w-]*$")));
    }
    @Test
    @WithMockUser(username = "username",roles = {"USER"})
    void apiImport_shouldThrow401() throws Exception {
        mockMvc.perform(get("/api/import/jetfit")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
    @Test
    @WithUserDetails(value = "vip_user", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void apiImport_testVipUser_shouldThrow401() throws Exception {

        mockMvc.perform(get("/api/import/jetfit")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

    }

    @Test
    void register_ShouldSaveUserToDatabase() throws Exception {
        // 1. GIVEN - Подготовка данных
        RegisterRequest regRequest = new RegisterRequest();
        regRequest.setUsername("integration_user");
        regRequest.setEmail("test@realdb.com");
        regRequest.setPassword("securePass123");

        // 2. WHEN - Выполняем реальный запрос
        mockMvc.perform(post("/auth/register") // Твой эндпоинт регистрации
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string("Registered successfully!"));

        // 3. THEN - Проверяем БАЗУ ДАННЫХ
        // Ищем пользователя, которого только что создали
        User savedUser = userRepository.findByUsername("integration_user").orElse(null);

        Assertions.assertNotNull(savedUser, "Пользователь должен сохраниться в БД");
        Assertions.assertEquals("test@realdb.com", savedUser.getEmail());

        // ВАЖНО: Проверяем, что пароль не лежит в открытом виде!
        Assertions.assertNotEquals("securePass123", savedUser.getPassword());
        Assertions.assertTrue(passwordEncoder.matches("securePass123", savedUser.getPassword()),
                "Пароль в БД должен быть захеширован корректно");
    }

    @Test
    void register_ShouldThrowExceptionUserAlreadyExists() throws Exception{
        RegisterRequest regRequest = new RegisterRequest();
        regRequest.setUsername("existing_user");
        regRequest.setEmail("existing_user@realdb.com");
        regRequest.setPassword("somepass");


        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("User with such username already exists"));

    }

}
/*
{
  "id": 105,
  "username": "dima_fit",
  "email": "dima@test.com",
  "isActive": true,
  "stats": {
    "weight": 90.5,
    "height": 185
  },
  "roles": ["USER", "ADMIN"],
  "workouts": [
    { "id": 1, "name": "Leg Day" },
    { "id": 2, "name": "Chest Day" }
  ]
}

1.Проверка простых полей
.andExpect(jsonPath("$.username").value("dima_fit"))  // Строка
.andExpect(jsonPath("$.id").value(105))               // Число
.andExpect(jsonPath("$.isActive").value(true))        // Булево
$ — это корень JSON объекта.

2. Проверка вложенных объектов (объекты внутри объектов)
.andExpect(jsonPath("$.stats.weight").value(90.5))
.andExpect(jsonPath("$.stats.height").value(185))

3. Проверка массивов (List)
// Проверяем, что ролей ровно две
.andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasSize(2)))
// Проверяем конкретный элемент по индексу [0]
.andExpect(jsonPath("$.roles[0]").value("USER"))
// Проверяем, что массив содержит определенные значения (порядок не важен)
.andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("ADMIN", "USER")))

4. Проверка массива объектов
// Проверяем имя первой тренировки
.andExpect(jsonPath("$.workouts[0].name").value("Leg Day"))
// Продвинуто: Проверяем, что ХОТЯ БЫ ОДНА тренировка называется "Chest Day" (без привязки к индексу)
// [*] означает "любой элемент массива"
.andExpect(jsonPath("$.workouts[*].name", org.hamcrest.Matchers.hasItem("Chest Day")))

5. Проверка существования/отсутствия полей
Это очень полезно для безопасности. Например, ты не должен возвращать поле password в JSON.
// Поле "email" должно присутствовать
.andExpect(jsonPath("$.email").exists())
// Поле "password" НЕ должно присутствовать
.andExpect(jsonPath("$.password").doesNotExist())

6. Проверка типов данных
.andExpect(jsonPath("$.id").isNumber())
.andExpect(jsonPath("$.username").isString())
.andExpect(jsonPath("$.isActive").isBoolean())
.andExpect(jsonPath("$.roles").isArray())
.andExpect(jsonPath("$.stats").isMap()) // Объект/Map
 */