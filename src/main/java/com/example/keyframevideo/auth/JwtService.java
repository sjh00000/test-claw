package com.example.keyframevideo.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long ACCESS_TOKEN_SECONDS = 24 * 60 * 60;

    private final ObjectMapper objectMapper;

    @Value("${auth.jwt.secret:keyframe-video-studio-jwt-secret}")
    private String secret;

    public String createAccessToken(Long userId, String username) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", userId);
            payload.put("username", username);
            payload.put("exp", Instant.now().plusSeconds(ACCESS_TOKEN_SECONDS).getEpochSecond());

            String headerPart = encode(objectMapper.writeValueAsBytes(header));
            String payloadPart = encode(objectMapper.writeValueAsBytes(payload));
            String unsignedToken = headerPart + "." + payloadPart;
            return unsignedToken + "." + sign(unsignedToken);
        } catch (Exception ex) {
            throw new IllegalStateException("accessToken 生成失败", ex);
        }
    }

    public JwtClaims parseAccessToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String unsignedToken = parts[0] + "." + parts[1];
            if (!sign(unsignedToken).equals(parts[2])) {
                return null;
            }
            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    new TypeReference<Map<String, Object>>() {
                    });
            Long userId = toLong(payload.get("userId"));
            Long exp = toLong(payload.get("exp"));
            if (userId == null || exp == null || exp < Instant.now().getEpochSecond()) {
                return null;
            }
            return new JwtClaims(userId, String.valueOf(payload.get("username")), exp);
        } catch (Exception ex) {
            return null;
        }
    }

    private String sign(String unsignedToken) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return encode(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    public record JwtClaims(Long userId, String username, Long expiresAt) {
    }
}
