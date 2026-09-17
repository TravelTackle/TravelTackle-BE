package Timeout.travel_tackle.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 공개 경로에서 매핑 없는 요청이 500 이 아니라 404/405 JSON 으로 나가는지 HTTP 레벨에서 확인
@SpringBootTest
@AutoConfigureMockMvc
class NotFoundApiTests {

    @Autowired MockMvc mockMvc;

    @Test
    void unknownPublicPathReturns404Json() throws Exception {
        mockMvc.perform(get("/api/feed/regions/does-not-exist/extra"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_003"));
    }

    @Test
    void wrongMethodOnPublicPathReturns405Json() throws Exception {
        mockMvc.perform(delete("/v3/api-docs"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_004"));
    }
}
