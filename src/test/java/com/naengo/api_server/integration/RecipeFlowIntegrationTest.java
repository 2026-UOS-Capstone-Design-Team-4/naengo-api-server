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

        // 2. alice 가 완성 레시피 제출 → user_recipes (201 + UserRecipeResponse)
        ResponseEntity<String> submit = postJson("/api/v1/user-recipes",
                fullRecipeBody("김치두부찌개"), aliceToken);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String submitBody = submit.getBody();
        long pendingId = Long.parseLong(
                AuthCookieIntegrationTest.extractField(submitBody, "user_recipe_id"));
        assertThat(pendingId).isPositive();
        assertThat(submitBody).contains("\"status\":\"PENDING\"");

        // 3. alice 의 /api/v1/user-recipes → 단순 배열, PENDING 1건
        ResponseEntity<String> myList = get("/api/v1/user-recipes", aliceToken);
        assertThat(myList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(myList.getBody()).contains("\"status\":\"PENDING\"");

        // 3b. 단건 조회 — 본인 소유
        ResponseEntity<String> one = get("/api/v1/user-recipes/" + pendingId, aliceToken);
        assertThat(one.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(one.getBody()).contains("\"user_recipe_id\":" + pendingId);

        // 4. admin 가입 + role 승급 + 재로그인
        signup("admin@b.c", "admin");
        promoteToAdmin("admin@b.c");
        String adminToken = login("admin@b.c");

        // 5. 승인 (단일 PATCH) → recipes INSERT + recipe_stats(0,0) 트리거. 응답은 UserRecipeResponse
        ResponseEntity<String> approve = patchJson(
                "/api/v1/admin/user-recipes/" + pendingId,
                "{\"status\":\"APPROVED\",\"admin_note\":\"양호\"}",
                adminToken);
        assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approve.getBody())
                .contains("\"status\":\"APPROVED\"")
                .contains("\"admin_note\":\"양호\"");
        long recipeId = recipeIdByTitle("김치두부찌개");

        // 6. recipe_stats(recipeId, 0, 0) 자동 INSERT 됐는지 확인 (트리거)
        Number likes = (Number) entityManager.createNativeQuery(
                "SELECT likes_count FROM recipe_stats WHERE recipe_id = :id")
                .setParameter("id", recipeId).getSingleResult();
        assertThat(likes.intValue()).isZero();

        // 7. 공개 목록 → 1건 노출 (커서 envelope: items / next_cursor / has_next)
        ResponseEntity<String> publicList = client.get().uri("/api/v1/recipes?sort=latest&limit=20")
                .retrieve().toEntity(String.class);
        assertThat(publicList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicList.getBody())
                .contains("김치두부찌개")
                .contains("\"items\":")
                .contains("\"has_next\":false")
                .contains("\"is_liked\":false");  // 비로그인 호출 → engagement false

        // 8. bob 가입 + alice 의 레시피에 like (POST /likes → RecipeStatsResponse)
        String bobToken = signup("bob@b.c", "bob");
        ResponseEntity<String> likeRes = postJson("/api/v1/recipes/" + recipeId + "/likes", null, bobToken);
        assertThat(likeRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(likeRes.getBody()).contains("\"likes_count\":1").contains("\"scrap_count\":0");

        // 8b. 이미 좋아요 → 409 ALREADY_LIKED
        ResponseEntity<String> dupLike = postJson("/api/v1/recipes/" + recipeId + "/likes", null, bobToken);
        assertThat(dupLike.getStatusCode().value()).isEqualTo(409);
        assertThat(dupLike.getBody()).contains("\"code\":\"ALREADY_LIKED\"");

        // 9. bob 가 scrap (POST /scraps → RecipeStatsResponse)
        ResponseEntity<String> scrapRes = postJson("/api/v1/recipes/" + recipeId + "/scraps", null, bobToken);
        assertThat(scrapRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrapRes.getBody()).contains("\"scrap_count\":1").contains("\"likes_count\":1");

        // 9b. 본인 스크랩 목록 (경로 이동: /api/v1/users/me/scraps)
        ResponseEntity<String> bobScraps = get("/api/v1/users/me/scraps", bobToken);
        assertThat(bobScraps.getBody())
                .contains("김치두부찌개")
                .contains("\"likes_count\":1")
                .contains("\"is_scrapped\":true")
                .contains("\"is_liked\":true");

        // 10. alice 의 /api/v1/user-recipes → status=APPROVED 로 갱신됨 (pending row 보존)
        ResponseEntity<String> aliceMy = get("/api/v1/user-recipes", aliceToken);
        assertThat(aliceMy.getBody()).contains("\"status\":\"APPROVED\"");

        // 11. 탈퇴 후 alice 닉네임 치환 검증
        ResponseEntity<Void> withdraw = client.delete().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                .retrieve().toBodilessEntity();
        assertThat(withdraw.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> publicAfter = client.get().uri("/api/v1/recipes/" + recipeId)
                .retrieve().toEntity(String.class);
        assertThat(publicAfter.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicAfter.getBody()).contains("\"author_nickname\":\"탈퇴한 사용자\"");

        // 12. bob 의 likes 는 alice 탈퇴와 무관 → likes_count 1 유지
        Number likesAfterWithdraw = (Number) entityManager.createNativeQuery(
                "SELECT likes_count FROM recipe_stats WHERE recipe_id = :id")
                .setParameter("id", recipeId).getSingleResult();
        assertThat(likesAfterWithdraw.intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("승인 시 필수 필드 누락 → 400 PENDING_RECIPE_INCOMPLETE")
    void approveIncompleteFails() {
        String alice = signup("a@b.c", "alice");
        // 최소 필드만 (description / ingredients_raw / servings / cooking_time / difficulty / category 누락)
        String submitBody = postJson("/api/v1/user-recipes",
                "{\"title\":\"미완성\",\"content\":\"본문\"}", alice).getBody();
        long pendingId = Long.parseLong(
                AuthCookieIntegrationTest.extractField(submitBody, "user_recipe_id"));

        signup("admin@b.c", "admin");
        promoteToAdmin("admin@b.c");
        String adminToken = login("admin@b.c");

        ResponseEntity<String> res = patchJson(
                "/api/v1/admin/user-recipes/" + pendingId, "{\"status\":\"APPROVED\"}", adminToken);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody())
                .contains("\"code\":\"PENDING_RECIPE_INCOMPLETE\"")
                .contains("필수 필드");
    }

    @Test
    @DisplayName("좋아요/스크랩 DELETE 분리 — 취소 200, 미적용 취소 409 NOT_*, 미존재 레시피 404")
    void likeScrapDeleteAndConflicts() {
        // 레시피 1건 확보 (submit → approve)
        String alice = signup("alice@b.c", "alice");
        long pendingId = Long.parseLong(AuthCookieIntegrationTest.extractField(
                postJson("/api/v1/user-recipes", fullRecipeBody("좋아요대상"), alice).getBody(),
                "user_recipe_id"));
        signup("admin@b.c", "admin");
        promoteToAdmin("admin@b.c");
        String adminToken = login("admin@b.c");
        patchJson("/api/v1/admin/user-recipes/" + pendingId,
                "{\"status\":\"APPROVED\"}", adminToken);
        long recipeId = recipeIdByTitle("좋아요대상");

        String bob = signup("bob@b.c", "bob");

        // 좋아요 안 한 상태에서 DELETE → 409 NOT_LIKED
        ResponseEntity<String> unlikeMiss = client.delete().uri("/api/v1/recipes/" + recipeId + "/likes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bob)
                .retrieve().toEntity(String.class);
        assertThat(unlikeMiss.getStatusCode().value()).isEqualTo(409);
        assertThat(unlikeMiss.getBody()).contains("\"code\":\"NOT_LIKED\"");

        // 좋아요 추가 → DELETE 취소 200 (likes_count 0 으로 복귀)
        postJson("/api/v1/recipes/" + recipeId + "/likes", null, bob);
        ResponseEntity<String> unlike = client.delete().uri("/api/v1/recipes/" + recipeId + "/likes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bob)
                .retrieve().toEntity(String.class);
        assertThat(unlike.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unlike.getBody()).contains("\"likes_count\":0");

        // 스크랩 안 한 상태 DELETE → 409 NOT_SCRAPPED
        ResponseEntity<String> unscrapMiss = client.delete().uri("/api/v1/recipes/" + recipeId + "/scraps")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bob)
                .retrieve().toEntity(String.class);
        assertThat(unscrapMiss.getStatusCode().value()).isEqualTo(409);
        assertThat(unscrapMiss.getBody()).contains("\"code\":\"NOT_SCRAPPED\"");

        // 미존재 레시피 좋아요 → 404
        ResponseEntity<String> like404 = postJson("/api/v1/recipes/999999/likes", null, bob);
        assertThat(like404.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(like404.getBody()).contains("\"code\":\"RECIPE_NOT_FOUND\"");
    }

    @Test
    @DisplayName("제출 레시피 soft delete → 200 메시지, 목록에서 제외, 재삭제 시 404")
    void softDeleteUserRecipe() {
        String alice = signup("a@b.c", "alice");
        String body = postJson("/api/v1/user-recipes",
                fullRecipeBody("삭제대상"), alice).getBody();
        long id = Long.parseLong(AuthCookieIntegrationTest.extractField(body, "user_recipe_id"));

        // 삭제 → 200 + 메시지
        ResponseEntity<String> del = client.delete().uri("/api/v1/user-recipes/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice)
                .retrieve().toEntity(String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(del.getBody()).contains("삭제되었습니다");

        // 목록에서 제외 (is_active=false)
        ResponseEntity<String> list = get("/api/v1/user-recipes", alice);
        assertThat(list.getBody()).doesNotContain("삭제대상");

        // 단건 조회 → 404
        ResponseEntity<String> one = get("/api/v1/user-recipes/" + id, alice);
        assertThat(one.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(one.getBody()).contains("\"code\":\"PENDING_RECIPE_NOT_FOUND\"");

        // 이미 삭제된 것 재삭제 → 404
        ResponseEntity<String> del2 = client.delete().uri("/api/v1/user-recipes/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice)
                .retrieve().toEntity(String.class);
        assertThat(del2.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("admin PATCH 부분 수정 + 승인 + video_url 중복 조회")
    void adminPatchEditAndVideoUrlLookup() {
        // 미완성 제출 (description/ingredients/... 누락)
        String alice = signup("alice@b.c", "alice");
        long pendingId = Long.parseLong(AuthCookieIntegrationTest.extractField(
                postJson("/api/v1/user-recipes",
                        "{\"title\":\"보정대상\",\"content\":\"본문\"}", alice).getBody(),
                "user_recipe_id"));

        signup("admin@b.c", "admin");
        promoteToAdmin("admin@b.c");
        String adminToken = login("admin@b.c");

        // 1) 콘텐츠만 부분 수정 (status 미포함) → 200, status 는 PENDING 유지
        ResponseEntity<String> edited = patchJson(
                "/api/v1/admin/user-recipes/" + pendingId,
                "{\"description\":\"보정된 설명\",\"admin_note\":\"검토중\"}", adminToken);
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody())
                .contains("\"status\":\"PENDING\"")
                .contains("\"description\":\"보정된 설명\"")
                .contains("\"admin_note\":\"검토중\"");

        // 2) 누락 필드 채우면서 동시에 APPROVED → 승격 200
        String fill = """
                {"status":"APPROVED",
                 "ingredients":[{"name":"김치","amount":"200","unit":"g","type":"메인","note":null}],
                 "ingredients_raw":"김치 200g","instructions":["볶다","끓이다"],
                 "servings":2.0,"cooking_time":20,"difficulty":"easy","category":["한식"],
                 "video_url":"https://youtu.be/abcdefghijk"}
                """;
        ResponseEntity<String> approved = patchJson(
                "/api/v1/admin/user-recipes/" + pendingId, fill, adminToken);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody()).contains("\"status\":\"APPROVED\"");

        // 3) video_url 로 중복 조회 → 승격된 recipe 반환
        ResponseEntity<String> byVideo = client.get()
                .uri("/api/v1/admin/recipes?video_url=https://youtu.be/abcdefghijk")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve().toEntity(String.class);
        assertThat(byVideo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byVideo.getBody())
                .contains("\"title\":\"보정대상\"")
                .contains("\"video_url\":\"https://youtu.be/abcdefghijk\"");

        // 4) 없는 video_url → 404 RECIPE_NOT_FOUND
        ResponseEntity<String> miss = client.get()
                .uri("/api/v1/admin/recipes?video_url=https://youtu.be/zzzzzzzzzzz")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve().toEntity(String.class);
        assertThat(miss.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(miss.getBody()).contains("\"code\":\"RECIPE_NOT_FOUND\"");
    }

    @Test
    @DisplayName("USER 토큰으로 admin endpoint → 403 + ErrorResponse(FORBIDDEN)")
    void userTokenRejectedFromAdmin() {
        String token = signup("a@b.c", "alice");
        ResponseEntity<String> res = client.get().uri("/api/v1/admin/user-recipes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).contains("\"code\":\"FORBIDDEN\"");
    }

    // ─── 헬퍼 ───────────────────────────────────────────────

    private String signup(String email, String nickname) {
        String body = "{\"username\":\"%s\",\"password\":\"pw12345A\",\"nickname\":\"%s\"}"
                .formatted(email, nickname);
        ResponseEntity<String> response = postJson("/api/v1/auth/signup", body, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return AuthCookieIntegrationTest.extractField(response.getBody(), "access_token");
    }

    private String login(String email) {
        String body = "{\"username\":\"%s\",\"password\":\"pw12345A\"}".formatted(email);
        ResponseEntity<String> response = postJson("/api/v1/auth/login", body, null);
        return AuthCookieIntegrationTest.extractField(response.getBody(), "access_token");
    }

    private void promoteToAdmin(String email) {
        transactionTemplate.executeWithoutResult(s ->
                entityManager.createNativeQuery("UPDATE users SET role='ADMIN' WHERE username = :e")
                        .setParameter("e", email).executeUpdate());
    }

    private String fullRecipeBody(String title) {
        return ("""
                {
                  "title":"%s",
                  "description":"칼칼하고 깊은 맛",
                  "content":"본문",
                  "ingredients":[{"name":"김치","amount":"200","unit":"g","type":"메인","note":null}],
                  "ingredients_raw":"김치 200g",
                  "instructions":["볶다","끓이다"],
                  "servings":2.0,
                  "cooking_time":20,
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

    private ResponseEntity<String> patchJson(String url, String body, String token) {
        return client.patch().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .retrieve().toEntity(String.class);
    }

    /** PATCH 응답엔 recipe_id 가 없으므로 승격된 recipes 의 PK 를 제목으로 역조회. */
    private long recipeIdByTitle(String title) {
        Number id = (Number) entityManager.createNativeQuery(
                        "SELECT recipe_id FROM recipes WHERE title = :t ORDER BY recipe_id DESC LIMIT 1")
                .setParameter("t", title).getSingleResult();
        return id.longValue();
    }
}
