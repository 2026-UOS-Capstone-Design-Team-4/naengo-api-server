package com.naengo.api_server.domain.user.service;

import com.naengo.api_server.domain.like.repository.LikeRepository;
import com.naengo.api_server.domain.recipe.repository.PendingRecipeRepository;
import com.naengo.api_server.domain.scrap.repository.ScrapRepository;
import com.naengo.api_server.domain.user.dto.PasswordChangeRequest;
import com.naengo.api_server.domain.user.dto.UserMeResponse;
import com.naengo.api_server.domain.user.dto.UserPreferencesResponse;
import com.naengo.api_server.domain.user.dto.UserPreferencesUpdateRequest;
import com.naengo.api_server.domain.user.dto.UserUpdateRequest;
import com.naengo.api_server.domain.user.entity.AuthProvider;
import com.naengo.api_server.domain.user.entity.User;
import com.naengo.api_server.domain.user.entity.UserProfile;
import com.naengo.api_server.domain.user.repository.UserProfileRepository;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 도메인 서비스: 본인 정보 조회 / 닉네임 수정 / 비밀번호 변경 /
 * 선호도(`user_profiles`) 조회·수정 / 회원 탈퇴(익명화).
 *
 * <p>탈퇴 익명화 (`docs/spec/user-withdraw.md`):
 * <ul>
 *   <li>{@code users} 행 보존 + PII nullify + 닉네임 꼬리표 + flag 토글 + deleted_at</li>
 *   <li>{@code scraps} / {@code likes} 삭제 → DB 트리거가 recipe_stats 카운터 자동 감소</li>
 *   <li>{@code pending_recipes} / {@code user_profiles} 삭제 (PII 가능성)</li>
 *   <li>{@code recipes} 보존 (응답 시점에 닉네임 치환)</li>
 *   <li>{@code chat_*} 는 AI 서버 합의 전까지 보류</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UserMeService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final LikeRepository likeRepository;
    private final ScrapRepository scrapRepository;
    private final PendingRecipeRepository pendingRecipeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        return UserMeResponse.from(loadActiveUser(userId));
    }

    @Transactional
    public UserMeResponse updateMe(Long userId, UserUpdateRequest request) {
        User user = loadActiveUser(userId);

        if (!user.getNickname().equals(request.nickname())
                && userRepository.existsByNickname(request.nickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        user.changeNickname(request.nickname());
        return UserMeResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = loadActiveUser(userId);

        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new CustomException(ErrorCode.SOCIAL_PASSWORD_NOT_ALLOWED);
        }

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    /** SPEC-20260504-04 — 선호도 조회. row 가 없으면 빈 default. */
    @Transactional(readOnly = true)
    public UserPreferencesResponse getPreferences(Long userId) {
        loadActiveUser(userId);
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> UserProfile.empty(userId));
        return UserPreferencesResponse.from(profile);
    }

    /** SPEC-20260504-05 — 선호도 갱신 (직접 입력 영역만). row 없으면 INSERT. */
    @Transactional
    public UserPreferencesResponse updatePreferences(Long userId, UserPreferencesUpdateRequest request) {
        loadActiveUser(userId);
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> userProfileRepository.save(UserProfile.empty(userId)));

        profile.updateUserEditable(
                request.userInput(),
                request.cookingSkill(),
                request.preferredCookingTime(),
                request.servingSize()
        );
        return UserPreferencesResponse.from(profile);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getDeletedAt() != null) {
            throw new CustomException(ErrorCode.ALREADY_WITHDRAWN);
        }

        // 1) 부속 데이터 삭제 — DB 트리거가 recipe_stats 카운터 자동 감소
        likeRepository.deleteAllByUserId(userId);
        scrapRepository.deleteAllByUserId(userId);
        pendingRecipeRepository.deleteAllByUserId(userId);
        userProfileRepository.deleteAllByUserId(userId);

        // 2) users 행 익명화 — 같은 트랜잭션
        user.anonymize();

        // 트리거 / 익명화 모두 즉시 반영되도록 flush
        entityManager.flush();
    }

    private User loadActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getDeletedAt() != null) {
            // 탈퇴된 사용자가 살아있는 토큰으로 호출한 비정상 케이스
            throw new CustomException(ErrorCode.ALREADY_WITHDRAWN);
        }
        return user;
    }
}
