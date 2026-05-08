package com.naengo.api_server.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 레시피 풀 E2E:
 *   alice signup → recipe submit (pending) → admin approve (recipes 로 이동, stats 트리거 검증) →
 *   bob signup → bob 가 like + scrap → counter 트리거 검증 → bob 의 /scraps/my 노출 →
 *   alice 의 /recipes/my 에 APPROVED 상태로 노출 → alice 탈퇴 → 응답에 닉네임 치환 / 카운터 보존
 */
class RecipeFlowIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("E2E: 사용자 제출 → 관리자 승인 → 좋아요/스크랩 → 탈퇴 후 닉네임 치환")
    void fullRecipeLifecycle() {
        // 1. alice 가입
        String aliceToken = signup("alice@b.c", "alice");

        // 2. alice 가 완성 레시피 제출 → pending_recipes
        String submitBody = postJson("/api/recipes", fullRecipeBody("김치두부찌개"), aliceToken).getBody();
        long pendingId = Long.parseLong(AuthCookieIntegrationTest.extractField(submitBody, "pendingRecipeId"));
        assertThat(pendingId).isPositive();

        // 3. alice 의 /api/recipes/my → PENDING 1건
        ResponseEntity<String> myList = get("/api/recipes/my", aliceToken);
        assertThat(myList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(myList.getBody()).contains("\"status\":\"PENDING\"");

        // 4. admin 가입 + role 승급 + 재로그인
        signup("admin@b.c", "admin");
        promoteToAdmin("admin@b.c");
        String adminToken = login("admin@b.c");

        // 5. 승인 → recipes INSERT + recipe_stats(0,0) 자동 생성 트리거 검증
        ResponseEntity<String> approve = postJson(
                "/api/admin/pending-recipes/" + pendingId + "/approve",
                "{\"adminNote\":\"양호\"}",
                adminToken);
        assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approve.getBody()).contains("\"status\":\"APPROVED\"");
        long recipeId = Long.parseLong(AuthCookieIntegrationTest.extractField(approve.getBody(), "recipeId"));

        // 6. recipe_stats(recipeId, 0, 0) 자동 INSERT 됐는지 확인 (트리거)
        Number likes = (Number) entityManager.createNativeQuery(
                "SELECT likes_count FROM recipe_stats WHERE recipe_id = :id")
                .setParameter("id", recipeId).getSingleResult();
        assertThat(likes.intValue()).isZero();

        // 7. 공개 목록 → 1건 노출
        ResponseEntity<String> publicList = client.get().uri("/api/recipes")
                .retrieve().toEntity(String.class);
        assertThat(publicList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicList.getBody()).contains("김치두부찌개");

        // 8. bob 가입 + alice 의 레시피에 like → 트리거가 likes_count++
        String bobToken = signup("bob@b.c", "bob");
        ResponseEntity<String> likeRes = postJson("/api/recipes/" + recipeId + "/like", null, bobToken);
        assertThat(likeRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(likeRes.getBody()).contains("\"liked\":true").contains("\"likesCount\":1");

        // 9. bob 가 scrap → /scraps/my 노출
        ResponseEntity<String> scrapRes = postJson("/api/recipes/" + recipeId + "/scrap", null, bobToken);
        assertThat(scrapRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrapRes.getBody()).contains("\"scrapped\":true").contains("\"scrapCount\":1");

        ResponseEntity<String> bobScraps = get("/api/scraps/my", bobToken);
        assertThat(bobScraps.getBody()).contains("김치두부찌개").contains("\"likesCount\":1");

        // 10. alice 의 /api/recipes/my → status=APPROVED 로 갱신됨 (pending row 보존)
        ResponseEntity<String> aliceMy = get("/api/recipes/my", aliceToken);
        assertThat(aliceMy.getBody()).contains("\"status\":\"APPROVED\"");

        // 11. 탈퇴 후 alice 닉네임 치환 검증
        ResponseEntity<Void> withdraw = client.delete().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                .retrieve().toBodilessEntity();
        assertThat(withdraw.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> publicAfter = client.get().uri("/api/recipes/" + recipeId)
                .retrieve().toEntity(String.class);
        assertThat(publicAfter.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicAfter.getBody()).contains("\"authorNickname\":\"탈퇴한 사용자\"");

        // 12. bob 의 likes 는 alice 탈퇴와 무관 → likes_count 1 유지
        Number likesAfterWithdraw = (Number) entityManager.createNativeQuery(
                "SELECT likes_count FROM recipe_stats WHERE recipe_id = :id")
                .setParameter("id", recipeId).getSingleResult();
        assertThat(likesAfterWithdraw.intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("승인 시 필수 필드 누락 → 422 PENDING_RECIPE_INCOMPLETE")
    void approveIncompleteFails() {
        String alice = signup("a@b.c", "alice");
        // 최소 필드만 (description / ingredients_raw / servings / cooking_time / difficulty / category 누락)
        String submitBody = postJson("/api/recipes",
                "{\"title\":\"미완성\",\"content\":\"본문\"}", alice).getBody();
        long pendingId = Long.parseLong(AuthCookieIntegrationTest.extractField(submitBody, "pendingRecipeId"));

        signup("admin@b.c", "admin");
        promoteToAdmin("admin@b.c");
        String adminToken = login("admin@b.c");

        ResponseEntity<String> res = postJson(
                "/api/admin/pending-recipes/" + pendingId + "/approve", "{}", adminToken);
        // Spring 6 가 UNPROCESSABLE_ENTITY → UNPROCESSABLE_CONTENT 이름 변경. status code 로 비교.
        assertThat(res.getStatusCode().value()).isEqualTo(422);
        assertThat(res.getBody()).contains("필수 필드");
    }

    @Test
    @DisplayName("USER 토큰으로 admin endpoint → 403 + ApiResponse")
    void userTokenRejectedFromAdmin() {
        String token = signup("a@b.c", "alice");
        ResponseEntity<String> res = client.get().uri("/api/admin/pending-recipes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).contains("\"success\":false");
    }

    // ─── 헬퍼 ───────────────────────────────────────────────

    private String signup(String email, String nickname) {
        String body = "{\"email\":\"%s\",\"password\":\"pw12345A\",\"nickname\":\"%s\"}"
                .formatted(email, nickname);
        ResponseEntity<String> response = postJson("/api/auth/signup", body, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return AuthCookieIntegrationTest.extractField(response.getBody(), "accessToken");
    }

    private String login(String email) {
        String body = "{\"email\":\"%s\",\"password\":\"pw12345A\"}".formatted(email);
        ResponseEntity<String> response = postJson("/api/auth/login", body, null);
        return AuthCookieIntegrationTest.extractField(response.getBody(), "accessToken");
    }

    private void promoteToAdmin(String email) {
        transactionTemplate.executeWithoutResult(s ->
                entityManager.createNativeQuery("UPDATE users SET role='ADMIN' WHERE email = :e")
                        .setParameter("e", email).executeUpdate());
    }

    private String fullRecipeBody(String title) {
        return ("""
                {
                  "title":"%s",
                  "description":"칼칼하고 깊은 맛",
                  "content":"본문",
                  "ingredients":[{"name":"김치","amount":"200","unit":"g","type":"메인","note":null}],
                  "ingredientsRaw":"김치 200g",
                  "instructions":["볶다","끓이다"],
                  "servings":2.0,
                  "cookingTime":20,
                  "difficulty":"easy",
                  "category":["한식"]
                }
                """).formatted(title);
    }

    private ResponseEntity<String> get(String url, String token) {
        return client.get().uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> postJson(String url, String body, String token) {
        var spec = client.post().uri(url);
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON);
        }
        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body == null) {
            return spec.retrieve().toEntity(String.class);
        }
        return spec.body(body).retrieve().toEntity(String.class);
    }
}
