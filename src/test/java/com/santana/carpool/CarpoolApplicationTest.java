package com.santana.carpool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

@DisplayName("CarpoolApplication Tests")
class CarpoolApplicationTest {

    @Test
    @DisplayName("Should delegate main method to SpringApplication.run")
    void testMain() {
        String[] args = new String[]{"--spring.main.web-application-type=none"};

        try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
            CarpoolApplication.main(args);
            mocked.verify(() -> SpringApplication.run(CarpoolApplication.class, args));
        }
    }
}
