package com.naengo.api_server.domain.user.support;

/**
 * 레시피·스크랩·좋아요 등에서 작성자 닉네임을 노출할 때 쓰는 공통 치환 로직.
 *
 * 탈퇴 사용자는 DB 상 nickname 을 "탈퇴한 사용자_&lt;user_id&gt;" 꼴로 저장하는데
 * (nickname UNIQUE 제약 유지를 위함, 정책: api-server-tasks.md §5),
 * 응답에서는 꼬리표 없이 "탈퇴한 사용자" 로만 보이도록 통일한다.
 */
public final class AuthorDisplayName {

    public static final String WITHDRAWN_PREFIX = "탈퇴한 사용자_";
    public static final String WITHDRAWN_DISPLAY = "탈퇴한 사용자";

    private AuthorDisplayName() {}

    public static String of(String rawNickname) {
        if (rawNickname == null) return null;
        if (rawNickname.startsWith(WITHDRAWN_PREFIX)) return WITHDRAWN_DISPLAY;
        return rawNickname;
    }
}
