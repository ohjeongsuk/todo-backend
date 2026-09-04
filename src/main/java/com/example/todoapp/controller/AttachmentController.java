package com.example.todoapp.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.todoapp.domain.User;
import com.example.todoapp.dto.AttachmentPresignRequest;
import com.example.todoapp.dto.AttachmentPresignResponse;
import com.example.todoapp.dto.AttachmentResponse;
import com.example.todoapp.dto.AttachmentViewUrlsRequest;
import com.example.todoapp.exception.ApiException;
import com.example.todoapp.exception.ApiResponse;
import com.example.todoapp.exception.ErrorCode;
import com.example.todoapp.service.AttachmentService;

/**
 * 첨부 파일 API (PRD F-46 ~ F-51).
 *
 * <p>인증 방식이 두 갈래다.
 *
 * <ul>
 *   <li>presign / complete / view-urls / delete — 일반 JWT 인증
 *   <li>upload / raw — <b>쿼리 서명 토큰</b>. {@code SecurityConfig}에서 permitAll로 열려 있다
 * </ul>
 *
 * <p>후자를 서명 토큰으로 두는 이유는 두 가지다. 브라우저 {@code <img>} 태그는 {@code Authorization} 헤더를
 * 실을 수 없고, S3 presigned PUT은 그 헤더가 붙으면 서명 검증에 실패한다. 로컬과 S3의 인증 형태를 같게 만들어야
 * 프론트엔드가 스토리지를 구분하지 않는다 (PRD F-50).
 *
 * <p>예외는 전부 {@link ApiException}으로 던지고 응답 조립은 {@code GlobalExceptionHandler}가 한다
 * (CLAUDE.md 절대 규칙 6).
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final long maxFileSize;

    public AttachmentController(
            AttachmentService attachmentService,
            @Value("${app.upload.max-file-size}") long maxFileSize) {
        this.attachmentService = attachmentService;
        this.maxFileSize = maxFileSize;
    }

    @PostMapping("/presign")
    public ApiResponse<AttachmentPresignResponse> presign(
            @AuthenticationPrincipal User principal,
            @Valid @RequestBody AttachmentPresignRequest request) {
        return ApiResponse.success(
                attachmentService.presign(
                        principal.getId(),
                        request.filename(),
                        request.contentType(),
                        request.fileSize()));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<AttachmentResponse> complete(
            @AuthenticationPrincipal User principal, @PathVariable Long id) {
        return ApiResponse.success(attachmentService.complete(principal.getId(), id));
    }

    /** 본문 이미지의 조회 URL을 한 번에 발급한다. 단건 엔드포인트를 두면 이미지 수만큼 왕복이 생긴다. */
    @PostMapping("/view-urls")
    public ApiResponse<Map<Long, String>> viewUrls(
            @AuthenticationPrincipal User principal,
            @Valid @RequestBody AttachmentViewUrlsRequest request) {
        return ApiResponse.success(
                attachmentService.viewUrls(principal.getId(), request.attachmentIds()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User principal, @PathVariable Long id) {
        attachmentService.delete(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // 로컬 스토리지 전용 (서명 토큰 인가, permitAll 경로)
    // ------------------------------------------------------------------

    /**
     * 파일 본문을 스트림으로 받는다.
     *
     * <p>{@code Content-Length}를 먼저 보고 즉시 거절하는 것은 <b>빠른 실패</b>일 뿐이다. 헤더는 위조할 수 있으므로
     * 실제 방어는 서비스 계층의 스트림 바이트 카운트가 한다 (PRD NF-34).
     */
    @PutMapping("/{id}/upload")
    public ResponseEntity<Void> upload(
            @PathVariable Long id, @RequestParam String token, HttpServletRequest httpRequest) {
        if (httpRequest.getContentLengthLong() > maxFileSize) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }
        try (InputStream body = httpRequest.getInputStream()) {
            attachmentService.receiveLocalUpload(id, token, body);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 파일을 스트림으로 반환한다. 브라우저 {@code <img>}가 직접 호출한다.
     *
     * <p>{@code X-Content-Type-Options: nosniff}로 MIME 스니핑을 막는다. 화이트리스트를 통과한
     * {@code contentType}만 저장하지만, 내용이 위장된 파일을 브라우저가 다른 타입으로 해석하는 경로를 닫아 둔다.
     */
    @GetMapping("/{id}/raw")
    public ResponseEntity<InputStreamResource> raw(
            @PathVariable Long id, @RequestParam String token) {
        AttachmentService.LocalFile file = attachmentService.openLocalFile(id, token);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.stream()));
    }
}
