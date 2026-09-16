package Timeout.travel_tackle.auth;

import Timeout.travel_tackle.auth.jwt.service.JwtService;
import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.image.service.ImageStorageService;
import Timeout.travel_tackle.image.service.ImageStorageService.Kind;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// S3 업로드는 목으로 대체하고 URL 저장·교체·삭제 흐름을 확인한다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileImageTests {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @MockitoBean ImageStorageService imageStorageService;

    @Test
    void uploadReplaceAndRemoveProfileImage() throws Exception {
        User user = userRepository.save(new User("profile-image@example.com", "프로필", "KR"));
        Cookie cookie = new Cookie("access_token", jwtService.createAccessToken(user));
        when(imageStorageService.upload(eq(user.getId()), any(MultipartFile.class), eq(Kind.PROFILE)))
                .thenReturn("https://cdn.test/profiles/" + user.getId() + "/1.jpg", "https://cdn.test/profiles/" + user.getId() + "/2.jpg");

        mockMvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(jsonPath("$.profileImageUrl").doesNotExist());

        mockMvc.perform(multipart(PUT, "/api/auth/me/profile-image")
                        .file(new MockMultipartFile("image", "me.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value("https://cdn.test/profiles/" + user.getId() + "/1.jpg"));
        verify(imageStorageService, never()).deleteAfterCommit(any(), any(), any());
        verify(imageStorageService).deleteOnRollback(user.getId(), List.of("https://cdn.test/profiles/" + user.getId() + "/1.jpg"), Kind.PROFILE);

        // 교체하면 이전 사진은 커밋 후 삭제 대상이 된다
        mockMvc.perform(multipart(PUT, "/api/auth/me/profile-image")
                        .file(new MockMultipartFile("image", "me2.jpg", "image/jpeg", new byte[]{4, 5, 6}))
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value("https://cdn.test/profiles/" + user.getId() + "/2.jpg"));
        verify(imageStorageService).deleteAfterCommit(user.getId(), List.of("https://cdn.test/profiles/" + user.getId() + "/1.jpg"), Kind.PROFILE);
        assertEquals("https://cdn.test/profiles/" + user.getId() + "/2.jpg", userRepository.findById(user.getId()).orElseThrow().getProfileImageUrl());

        mockMvc.perform(delete("/api/auth/me/profile-image").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").doesNotExist());
        verify(imageStorageService).deleteAfterCommit(user.getId(), List.of("https://cdn.test/profiles/" + user.getId() + "/2.jpg"), Kind.PROFILE);
        assertNull(userRepository.findById(user.getId()).orElseThrow().getProfileImageUrl());

        // 이미 없는 상태에서 다시 지워도 성공하고 추가 삭제 예약은 없다
        mockMvc.perform(delete("/api/auth/me/profile-image").cookie(cookie)).andExpect(status().isOk());
        verify(imageStorageService, times(2)).deleteAfterCommit(any(), any(), any());
    }

    @Test
    void missingFileAndUnauthenticatedRequestsAreRejected() throws Exception {
        User user = userRepository.save(new User("profile-image2@example.com", "프로필", "KR"));
        Cookie cookie = new Cookie("access_token", jwtService.createAccessToken(user));

        mockMvc.perform(multipart(PUT, "/api/auth/me/profile-image").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        mockMvc.perform(multipart(PUT, "/api/auth/me/profile-image")
                        .file(new MockMultipartFile("image", "me.jpg", "image/jpeg", new byte[]{1})))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/auth/me/profile-image")).andExpect(status().isUnauthorized());
        verify(imageStorageService, never()).upload(any(), any(), any());
    }
}
