package com.example.todoapp.service.storage;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.todoapp.exception.ApiException;
import com.example.todoapp.exception.ErrorCode;

/**
 * 첨부 업로드·조회 URL에 붙는 단기 서명 토큰 (PRD NF-32).
 *
 * <p>브라우저 {@code <img>} 태그가 조회 URL을 직접 호출하므로 {@code Authorization} 헤더를 실을 수 없다. 또
 * 업로드 URL은 S3 presigned PUT과 형태가 같아야 하는데, presigned PUT에 {@code Authorization} 헤더를
 * 붙이면 서명 검증에 실패한다. 두 문제를 같은 방식으로 푸는 것이 쿼리 서명 토큰이다.
 *
 * <p>구현은 {@code pom.xml}에 이미 있는 jjwt를 재사용한다. {@code javax.crypto.Mac}으로 직접 짜면 만료
 * 처리·변조 탐지·URL-safe 인코딩을 모두 손으로 만들어야 한다.
 *
 * <p><b>키는 {@code jwt.secret}과 반드시 분리한다.</b> 공유하면 스토리지 토큰이 액세스 토큰으로 오인될 여지가 생긴다.
 *
 * <p>검증 실패는 종류를 가리지 않고 전부 {@link ErrorCode#NOT_FOUND}로 응답한다 (CLAUDE.md 절대 규칙 4 —
 * 리소스 존재 여부를 노출하지 않는다).
 */
@Component
public class StorageSignature {

    private static final String CLAIM_PURPOSE = "purpose";

    private final SecretKey key;
    private final long expiryMillis;

    public StorageSignature(
            @Value("${app.storage.signing-secret}") String signingSecret,
            @Value("${app.storage.url-expiry-minutes}") long expiryMinutes) {
        this.key = Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8));
        this.expiryMillis = expiryMinutes * 60_000L;
    }

    public String sign(Long attachmentId, StoragePurpose purpose) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(attachmentId))
                .claim(CLAIM_PURPOSE, purpose.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMillis))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 토큰을 검증한다. 서명 위조, 만료, 대상 불일치, 용도 불일치를 모두 거른다.
     *
     * @throws ApiException 어느 하나라도 어긋나면 {@link ErrorCode#NOT_FOUND}
     */
    public void verify(String token, Long expectedAttachmentId, StoragePurpose expectedPurpose) {
        if (token == null || token.isBlank()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        try {
            var claims =
                    Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

            if (!String.valueOf(expectedAttachmentId).equals(claims.getSubject())) {
                throw new ApiException(ErrorCode.NOT_FOUND);
            }
            if (!expectedPurpose.name().equals(claims.get(CLAIM_PURPOSE, String.class))) {
                throw new ApiException(ErrorCode.NOT_FOUND);
            }
        } catch (JwtException | IllegalArgumentException e) {
            // 만료(ExpiredJwtException)와 서명 오류(SignatureException)가 모두 여기로 온다.
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
