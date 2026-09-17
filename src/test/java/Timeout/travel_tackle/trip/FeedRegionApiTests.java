package Timeout.travel_tackle.trip;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 비로그인 공개 API 로 노출되는지와 날짜 파라미터 파싱을 HTTP 레벨에서 확인
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedRegionApiTests {

    @Autowired MockMvc mockMvc;

    @Test
    void regionsEndpointIsPublicAndAcceptsIsoDates() throws Exception {
        mockMvc.perform(get("/api/feed/regions").param("from", "2026-07-01").param("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void malformedDateReturns400InsteadOf500() throws Exception {
        mockMvc.perform(get("/api/feed/regions").param("from", "2026-7-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void reversedPeriodReturns400() throws Exception {
        mockMvc.perform(get("/api/feed/regions").param("from", "2026-07-31").param("to", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }
}
