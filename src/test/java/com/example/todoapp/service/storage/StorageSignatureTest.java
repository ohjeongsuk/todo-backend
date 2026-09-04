package com.example.todoapp.service.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.todoapp.exception.ApiException;
import com.example.todoapp.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 서명 토큰 검증 (PRD NF-32). Spring 컨텍스트 없이 순수 단위 테스트로 돈다.
 *
 * <p>검증 실패는 종류를 가리지 않고 전부 {@link ErrorCode#NOT_FOUND}여야 한다. 만료와 위조를 다른 코드로 응답하면
 * 공격자에게 "이 첨부는 존재하지만 토큰이 만료됐다"는 정보를 주게 된다 (CLAUDE.md 절대 규칙 4).
 */
class StorageSignatureTest {

    private static final String SECRET = "unit-test-storage-signing-secret-32bytes-min";
    private static final String OTHER_SECRET = "completely-different-secret-key-32bytes-min";

    private final StorageSignature signature = new StorageSignature(SECRET, 30);

    @Test
    @DisplayName("정상 토큰은 같은 id·용도로 검증을 통과한다")
    void verifiesValidToken() {
        String token = signature.sign(1L, StoragePurpose.UPLOAD);

        assertThat(token).isNotBlank();
        signature.verify(token, 1L, StoragePurpose.UPLOAD);
    }

    @Nested
    @DisplayName("검증 실패는 전부 404다")
    class RejectsInvalid {

        @Test
        @DisplayName("다른 키로 서명한 토큰(위조)")
        void rejectsForgedSignature() {
            String forged = new StorageSignature(OTHER_SECRET, 30).sign(1L, StoragePurpose.UPLOAD);

            assertThatThrownBy(() -> signature.verify(forged, 1L, StoragePurpose.UPLOAD))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("만료된 토큰")
        void rejectsExpiredToken() {
            // 만료 시간을 음수로 주면 발급 즉시 만료된 토큰이 나온다.
            String expired = new StorageSignature(SECRET, -1).sign(1L, StoragePurpose.UPLOAD);

            assertThatThrownBy(() -> signature.verify(expired, 1L, StoragePurpose.UPLOAD))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("다른 첨부 id로 발급된 토큰")
        void rejectsWrongAttachmentId() {
            String token = signature.sign(1L, StoragePurpose.UPLOAD);

            assertThatThrownBy(() -> signature.verify(token, 2L, StoragePurpose.UPLOAD))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("조회용 토큰으로 업로드를 호출하는 교차 사용")
        void rejectsPurposeMismatch() {
            // 조회 URL은 <img> 태그에 그대로 노출되므로, 용도 구분이 없으면 노출된 토큰이 곧 쓰기 권한이 된다.
            String viewToken = signature.sign(1L, StoragePurpose.VIEW);

            assertThatThrownBy(() -> signature.verify(viewToken, 1L, StoragePurpose.UPLOAD))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("토큰이 비었거나 형식이 아닌 경우")
        void rejectsBlankAndMalformed() {
            assertThatThrownBy(() -> signature.verify(null, 1L, StoragePurpose.VIEW))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> signature.verify("   ", 1L, StoragePurpose.VIEW))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> signature.verify("not-a-jwt", 1L, StoragePurpose.VIEW))
                    .isInstanceOf(ApiException.class);
        }
    }
}
