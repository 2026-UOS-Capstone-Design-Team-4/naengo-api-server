package com.naengo.api_server.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PR-7 검증 — 프로필(user_input 전체 교체) + 채팅(plain array, GET/{id}, soft DELETE).
 */
class ProfileChatIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("프로필 — GET 빈 배열 → PATCH 교체 → GET 반영 → 빈 배열로 초기화")
    void profileUserInputReplace() {
        String token = signup("p@b.c", "pp");

        ResponseEntity<String> empty = get("/api/v1/users/me/profile", token);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(empty.getBody()).contains("\"user_input\":[]");

        ResponseEntity<String> patched = patchJson("/api/v1/users/me/profile",
                "{\"user_input\":[\"새우 알레르기\",\"매운맛 선호\"]}", token);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody()).contains("새우 알레르기").contains("매운맛 선호");

        assertThat(get("/api/v1/users/me/profile", token).getBody())
                .contains("새우 알레르기").contains("매운맛 선호");

        // 빈 배열 → 초기화
        ResponseEntity<String> reset = patchJson("/api/v1/users/me/profile",
                "{\"user_input\":[]}", token);
        assertThat(reset.getBody()).contains("\"user_input\":[]");

        // user_input 누락 → 400 VALIDATION_FAILED (필수)
        ResponseEntity<String> bad = patchJson("/api/v1/users/me/profile", "{}", token);
        assertThat(bad.getStatusCode().value()).isEqualTo(400);
        assertThat(bad.getBody()).contains("\"code\":\"VALIDATION_FAILED\"");
    }

    @Test
    @DisplayName("채팅 — 목록(배열/snake) → 메시지(/{id}) → soft DELETE → 목록 제외 → 재삭제 404")
    void chatRoomsAndSoftDelete() {
        String token = signup("c@b.c", "cc");
        long userId = userIdByEmail("c@b.c");

        long roomId = insertRoom(userId, "김치 상담");
        insertMessage(roomId, "user", "김치로 뭐 만들어?");
        insertMessage(roomId, "model", "김치찌개 어때요?");

        // 목록 — plain array + snake_case
        ResponseEntity<String> rooms = get("/api/v1/chat/rooms", token);
        assertThat(rooms.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rooms.getBody())
                .startsWith("[")
                .contains("\"room_id\":" + roomId)
                .contains("\"updated_at\"")
                .contains("김치 상담");

        // 메시지 — /rooms/{id} (suffix 없음), plain array + snake_case
        ResponseEntity<String> msgs = get("/api/v1/chat/rooms/" + roomId, token);
        assertThat(msgs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(msgs.getBody())
                .startsWith("[")
                .contains("\"message_id\"")
                .contains("\"role\":\"user\"")
                .contains("김치찌개 어때요?");

        // soft delete → 200 + 메시지
        ResponseEntity<String> del = client.delete().uri("/api/v1/chat/rooms/" + roomId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(del.getBody()).contains("삭제되었습니다");

        // 목록에서 제외
        assertThat(get("/api/v1/chat/rooms", token).getBody()).doesNotContain("김치 상담");

        // 재삭제 → 404
        ResponseEntity<String> del2 = client.delete().uri("/api/v1/chat/rooms/" + roomId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);
        assertThat(del2.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(del2.getBody()).contains("\"code\":\"CHAT_ROOM_NOT_FOUND\"");
    }

    @Test
    @DisplayName("회원 탈퇴 → 본인 채팅방 전부 soft delete (is_active=false), 메시지 행은 보존")
    void withdrawDeactivatesChatRooms() {
        String token = signup("w@b.c", "ww");
        long userId = userIdByEmail("w@b.c");

        long room1 = insertRoom(userId, "방1");
        long room2 = insertRoom(userId, "방2");
        insertMessage(room1, "user", "안녕");
        insertMessage(room2, "model", "추천드려요");

        // 탈퇴
        ResponseEntity<Void> withdraw = client.delete().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toBodilessEntity();
        assertThat(withdraw.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 채팅방 전부 is_active=false (행 보존, soft delete)
        Number activeRooms = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM chat_rooms WHERE user_id = :id AND is_active = true")
                .setParameter("id", userId).getSingleResult();
        assertThat(activeRooms.longValue()).isZero();

        Number totalRooms = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM chat_rooms WHERE user_id = :id")
                .setParameter("id", userId).getSingleResult();
        assertThat(totalRooms.longValue()).isEqualTo(2L);

        // 메시지 행은 보존 (본문 PII 스크럽/hard delete 는 AI 합의 후 승격)
        Number msgs = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM chat_messages WHERE room_id IN (:r1, :r2)")
                .setParameter("r1", room1).setParameter("r2", room2).getSingleResult();
        assertThat(msgs.longValue()).isEqualTo(2L);
    }

    // ─── 헬퍼 ───────────────────────────────────────────────

    private String signup(String email, String nickname) {
        String body = "{\"email\":\"%s\",\"password\":\"pw12345A\",\"nickname\":\"%s\"}"
                .formatted(email, nickname);
        ResponseEntity<String> r = client.post().uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return AuthCookieIntegrationTest.extractField(r.getBody(), "access_token");
    }

    private long userIdByEmail(String email) {
        Number id = (Number) entityManager.createNativeQuery(
                        "SELECT user_id FROM users WHERE email = :e")
                .setParameter("e", email).getSingleResult();
        return id.longValue();
    }

    private long insertRoom(long userId, String title) {
        return transactionTemplate.execute(s ->
                ((Number) entityManager.createNativeQuery("""
                        INSERT INTO chat_rooms (user_id, title, is_active, created_at, updated_at)
                        VALUES (:u, :t, true, NOW(), NOW()) RETURNING room_id
                        """)
                        .setParameter("u", userId).setParameter("t", title)
                        .getSingleResult()).longValue());
    }

    private void insertMessage(long roomId, String role, String content) {
        transactionTemplate.executeWithoutResult(s ->
                entityManager.createNativeQuery("""
                        INSERT INTO chat_messages (room_id, role, content, created_at)
                        VALUES (:r, :ro, :c, NOW())
                        """)
                        .setParameter("r", roomId).setParameter("ro", role).setParameter("c", content)
                        .executeUpdate());
    }

    private ResponseEntity<String> get(String url, String token) {
        return client.get().uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> patchJson(String url, String body, String token) {
        return client.patch().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .retrieve().toEntity(String.class);
    }
}
