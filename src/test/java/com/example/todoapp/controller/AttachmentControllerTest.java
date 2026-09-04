package com.example.todoapp.controller;

import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.todoapp.domain.Attachment;
import com.example.todoapp.domain.AttachmentRepository;
import com.example.todoapp.domain.AttachmentStatus;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import com.example.todoapp.security.JwtTokenProvider;
import com.example.todoapp.service.storage.StoragePurpose;
import com.example.todoapp.service.storage.StorageSignature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 테스트 9~12번 (ROADMAP Phase 12). 기존 1~8번 체계를 잇는다.
 *
 * <p>인증은 {@code @WithMockUser}가 아니라 실제 JWT를 발급해 헤더에 싣는 방식이다 — 인증 파이프라인 전체를 통과시켜야
 * 필터·SecurityConfig 설정까지 함께 검증된다.
 *
 * <p>JSON 파싱에 {@code ObjectMapper}를 주입하지 않고 {@link JsonPath}를 쓴다. Spring Boot 4는
 * Jackson 3(`tools.jackson`)을 쓰므로 {@code com.fasterxml.jackson.databind.ObjectMapper} 타입의
 * 빈이 존재하지 않는다 (그 2.x 클래스는 jjwt-jackson이 끌어온 것일 뿐이다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttachmentControllerTest {

    private static final byte[] PNG_BYTES = "fake-png-content".getBytes();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AttachmentRepository attachmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private StorageSignature storageSignature;

    private User saveUser(String email) {
        return userRepository.save(
                User.createLocal(email, passwordEncoder.encode("test1234"), "닉네임"));
    }

    private String tokenFor(User user) {
        return jwtTokenProvider.createAccessToken(user.getId());
    }

    /** presign을 호출하고 응답 본문 전체를 돌려준다. */
    private String presign(String jwt, String filename, String contentType, long size)
            throws Exception {
        return mockMvc.perform(
                        post("/api/attachments/presign")
                                .header("Authorization", "Bearer " + jwt)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"filename":"%s","contentType":"%s","fileSize":%d}
                                        """
                                                .formatted(filename, contentType, size)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static long attachmentIdOf(String presignBody) {
        Number id = JsonPath.read(presignBody, "$.data.attachmentId");
        return id.longValue();
    }

    // ------------------------------------------------------------------
    // 테스트 9번 — presign → upload → complete 전체 흐름과 상태 전이
    // ------------------------------------------------------------------

    @Test
    void 업로드_전체흐름과_상태전이() throws Exception {
        String jwt = tokenFor(saveUser("flow@example.com"));

        String body = presign(jwt, "photo.png", "image/png", PNG_BYTES.length);
        long attachmentId = attachmentIdOf(body);

        // presign 응답에 storageKey를 담지 않는다 (PRD NF-33).
        Map<String, Object> data = JsonPath.read(body, "$.data");
        assertThat(data).doesNotContainKey("storageKey");
        assertThat((String) JsonPath.read(body, "$.data.uploadUrl"))
                .contains("/api/attachments/" + attachmentId);

        assertThat(attachmentRepository.findById(attachmentId).orElseThrow().getStatus())
                .isEqualTo(AttachmentStatus.PENDING);

        // 업로드는 JWT가 아니라 서명 토큰으로 인가한다. Authorization 헤더를 싣지 않는다.
        mockMvc.perform(
                        put("/api/attachments/{id}/upload", attachmentId)
                                .param(
                                        "token",
                                        storageSignature.sign(attachmentId, StoragePurpose.UPLOAD))
                                .content(PNG_BYTES))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/attachments/{id}/complete", attachmentId)
                                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewUrl").exists());

        Attachment uploaded = attachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(uploaded.getStatus()).isEqualTo(AttachmentStatus.UPLOADED);
        // 크기는 신고값이 아니라 실측값으로 덮어써진다 (PRD NF-34).
        assertThat(uploaded.getFileSize()).isEqualTo(PNG_BYTES.length);
    }

    // ------------------------------------------------------------------
    // 테스트 10번 — 소유권 404와 서명 토큰 거부
    // ------------------------------------------------------------------

    @Test
    void 타인접근은_404이고_서명토큰_위조와_교차사용은_거부된다() throws Exception {
        User owner = saveUser("owner@example.com");
        User stranger = saveUser("stranger@example.com");

        long attachmentId = attachmentIdOf(presign(tokenFor(owner), "a.png", "image/png", 10));

        // 타인은 403이 아니라 404를 받는다 (CLAUDE.md 절대 규칙 4, PRD NF-03).
        mockMvc.perform(
                        post("/api/attachments/{id}/complete", attachmentId)
                                .header("Authorization", "Bearer " + tokenFor(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        mockMvc.perform(
                        delete("/api/attachments/{id}", attachmentId)
                                .header("Authorization", "Bearer " + tokenFor(stranger)))
                .andExpect(status().isNotFound());

        // 위조 토큰
        mockMvc.perform(get("/api/attachments/{id}/raw", attachmentId).param("token", "forged"))
                .andExpect(status().isNotFound());

        // 다른 첨부용으로 발급된 토큰
        mockMvc.perform(
                        get("/api/attachments/{id}/raw", attachmentId)
                                .param(
                                        "token",
                                        storageSignature.sign(
                                                attachmentId + 999, StoragePurpose.VIEW)))
                .andExpect(status().isNotFound());

        // 조회용 토큰으로 업로드를 호출하는 교차 사용. 조회 URL은 <img> 태그에 노출되므로
        // 이 구분이 없으면 노출된 토큰이 곧 쓰기 권한이 된다.
        mockMvc.perform(
                        put("/api/attachments/{id}/upload", attachmentId)
                                .param(
                                        "token",
                                        storageSignature.sign(attachmentId, StoragePurpose.VIEW))
                                .content(PNG_BYTES))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // 테스트 11번 — 크기·타입 검증
    // ------------------------------------------------------------------

    @Test
    void 크기초과와_SVG와_화이트리스트밖_타입은_거부된다() throws Exception {
        String jwt = tokenFor(saveUser("limits@example.com"));

        // SVG는 스크립트를 실행할 수 있어 목록에 없다 (PRD F-47).
        mockMvc.perform(
                        post("/api/attachments/presign")
                                .header("Authorization", "Bearer " + jwt)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"filename":"x.svg","contentType":"image/svg+xml","fileSize":100}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"));

        mockMvc.perform(
                        post("/api/attachments/presign")
                                .header("Authorization", "Bearer " + jwt)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"filename":"x.exe","contentType":"application/octet-stream","fileSize":100}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"));

        // 신고값이 상한을 넘으면 presign 단계에서 즉시 거른다
        mockMvc.perform(
                        post("/api/attachments/presign")
                                .header("Authorization", "Bearer " + jwt)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"filename":"big.png","contentType":"image/png","fileSize":99999999}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));

        // 신고값을 작게 속이고 큰 본문을 보내도 스트림 바이트 카운트가 잡는다 (PRD NF-34).
        long attachmentId = attachmentIdOf(presign(jwt, "sneaky.png", "image/png", 10));
        mockMvc.perform(
                        put("/api/attachments/{id}/upload", attachmentId)
                                .param(
                                        "token",
                                        storageSignature.sign(attachmentId, StoragePurpose.UPLOAD))
                                .content(new byte[6 * 1024 * 1024]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
    }

    // ------------------------------------------------------------------
    // 테스트 12번 — 할 일 연결, 본문 이탈 시 정리, sanitize 이후 수집 순서
    // ------------------------------------------------------------------

    @Test
    void 할일저장시_LINKED승격되고_본문에서_사라지면_SoftDelete된다() throws Exception {
        String jwt = tokenFor(saveUser("link@example.com"));

        long keptId = uploadOne(jwt, "kept.png");
        long removedId = uploadOne(jwt, "removed.png");

        String created =
                mockMvc.perform(
                                post("/api/todos")
                                        .header("Authorization", "Bearer " + jwt)
                                        .contentType("application/json")
                                        .content(
                                                """
                                                {"title":"첨부 있는 할 일","content":"<p><img data-attachment-id=\\"%d\\" alt=\\"a\\"><img data-attachment-id=\\"%d\\" alt=\\"b\\"></p>","priority":"MEDIUM","dueDate":null}
                                                """
                                                        .formatted(keptId, removedId)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Number todoId = JsonPath.read(created, "$.data.id");
        String savedContent = JsonPath.read(created, "$.data.content");

        // Jsoup Safelist를 통과해 img가 살아 있고, src는 저장되지 않는다 (PRD F-49).
        assertThat(savedContent).contains("data-attachment-id");
        assertThat(savedContent).doesNotContain("src=");

        assertThat(attachmentRepository.findById(keptId).orElseThrow().getStatus())
                .isEqualTo(AttachmentStatus.LINKED);
        assertThat(attachmentRepository.findById(removedId).orElseThrow().getStatus())
                .isEqualTo(AttachmentStatus.LINKED);

        // 하나만 남기고 수정하면 사라진 쪽이 Soft Delete된다
        mockMvc.perform(
                        put("/api/todos/{id}", todoId.longValue())
                                .header("Authorization", "Bearer " + jwt)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"title":"첨부 하나만","content":"<p><img data-attachment-id=\\"%d\\" alt=\\"a\\"></p>","priority":"MEDIUM","dueDate":null}
                                        """
                                                .formatted(keptId)))
                .andExpect(status().isOk());

        assertThat(attachmentRepository.findById(keptId).orElseThrow().isDeleted()).isFalse();
        assertThat(attachmentRepository.findById(removedId).orElseThrow().isDeleted()).isTrue();

        // 조회 URL은 일괄로 발급된다 (본문 이미지 수만큼 왕복하지 않는다)
        mockMvc.perform(
                        post("/api/attachments/view-urls")
                                .header("Authorization", "Bearer " + jwt)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"attachmentIds":[%d]}
                                        """
                                                .formatted(keptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['" + keptId + "']").exists());
    }

    @Test
    void sanitize가_제거할_태그속_첨부는_LINKED로_승격되지_않는다() throws Exception {
        String jwt = tokenFor(saveUser("order@example.com"));
        long insideScript = uploadOne(jwt, "hidden.png");

        // <script>는 Jsoup이 통째로 지운다. 수집이 sanitize보다 먼저 돌면 이 첨부가 LINKED가 되고,
        // 본문에 존재하지 않는 파일이 정리 배치 대상에서도 빠진 채 영구 보존된다.
        mockMvc.perform(
                        post("/api/todos")
                                .header("Authorization", "Bearer " + jwt)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"title":"순서 검증","content":"<script><img data-attachment-id=\\"%d\\"></script><p>본문</p>","priority":"MEDIUM","dueDate":null}
                                        """
                                                .formatted(insideScript)))
                .andExpect(status().isOk());

        Attachment attachment = attachmentRepository.findById(insideScript).orElseThrow();
        assertThat(attachment.getStatus())
                .as("sanitize 이후에 수집해야 UPLOADED로 남는다")
                .isEqualTo(AttachmentStatus.UPLOADED);
        assertThat(attachment.getTodo()).isNull();
    }

    @Test
    void 타인의_첨부id를_본문에_섞으면_404다() throws Exception {
        User owner = saveUser("mine@example.com");
        User stranger = saveUser("theirs@example.com");
        long strangersAttachment = uploadOne(tokenFor(stranger), "theirs.png");

        mockMvc.perform(
                        post("/api/todos")
                                .header("Authorization", "Bearer " + tokenFor(owner))
                                .contentType("application/json")
                                .content(
                                        """
                                        {"title":"도둑질","content":"<p><img data-attachment-id=\\"%d\\"></p>","priority":"MEDIUM","dueDate":null}
                                        """
                                                .formatted(strangersAttachment)))
                .andExpect(status().isNotFound());
    }

    /** presign → upload → complete를 한 번에 수행하고 attachmentId를 돌려준다. */
    private long uploadOne(String jwt, String filename) throws Exception {
        long attachmentId = attachmentIdOf(presign(jwt, filename, "image/png", PNG_BYTES.length));

        mockMvc.perform(
                        put("/api/attachments/{id}/upload", attachmentId)
                                .param(
                                        "token",
                                        storageSignature.sign(attachmentId, StoragePurpose.UPLOAD))
                                .content(PNG_BYTES))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/attachments/{id}/complete", attachmentId)
                                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());

        return attachmentId;
    }
}
