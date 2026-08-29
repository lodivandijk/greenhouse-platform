package com.greenhouse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"greenhouse.evaluation.enabled=false",
                "greenhouse.daily-briefing.enabled=false"})
class GreenhouseApplicationTests {

    @Test
    void contextLoads() {
    }
}
