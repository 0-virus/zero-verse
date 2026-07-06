package com.zeroverse.domain.post.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import org.springframework.stereotype.Service;

/**
 * 게시글 접근 제어 서비스.
 * 다음 매트릭스를 구현:
 * | visibility | published | 인증 | 소유 | 결과 |
 * | PUBLIC | Y | any | any | 허용 |
 * | PUBLIC | N | - | 소유자 | 소유자 허용 |
 * | PUBLIC | N | - | 비소유 | 404 또는 403 |
 * | PRIVATE | any | - | 소유자 | 소유자 허용 |
 * | PRIVATE | any | - | 비소유 | 403 |
 * | UNIVERSE | Y | 로그인必 | - | M4a 임시 허용(M5에서 발견자 검증) |
 * | UNIVERSE | Y | 비로그인 | - | 401 AUTH_004 |
 * | UNIVERSE | N | - | 소유자 | 소유자 허용 |
 * | UNIVERSE | N | - | 비소유 | 404 또는 403 |
 */
@Service
public class PostAccessControlService {

    /**
     * 게시글 접근 가능 여부 판정.
     *
     * @param post 게시글
     * @param userId 요청 사용자 ID (null이면 비로그인)
     * @throws BusinessException POST_001(404), POST_002(403), AUTH_004(401)
     */
    public void canView(Post post, Long userId) {
        boolean isOwner = post.isOwner(userId);
        boolean isPublished = post.isPublished();
        Visibility visibility = post.getVisibility();

        // PUBLIC 게시글
        if (visibility == Visibility.PUBLIC) {
            if (isPublished) {
                // 발행 PUBLIC: 누구나 허용
                return;
            } else {
                // 임시 PUBLIC: 작성자만
                if (isOwner) {
                    return;
                }
                throw new BusinessException(ErrorCode.POST_001);
            }
        }

        // PRIVATE 게시글
        if (visibility == Visibility.PRIVATE) {
            if (isOwner) {
                return;
            }
            // 비소유자는 접근 불가
            throw new BusinessException(ErrorCode.POST_002);
        }

        // UNIVERSE 게시글
        if (visibility == Visibility.UNIVERSE) {
            if (isPublished) {
                // 발행 UNIVERSE: M4a 임시로 로그인만 허용
                // TODO[M5]: Universe 발견자 관계 검증으로 교체
                if (userId != null) {
                    return;
                }
                // 비로그인은 401
                throw new BusinessException(ErrorCode.AUTH_004);
            } else {
                // 임시 UNIVERSE: 작성자만
                if (isOwner) {
                    return;
                }
                throw new BusinessException(ErrorCode.POST_001);
            }
        }
    }

    /**
     * 수정/삭제 권한 확인 (작성자만).
     *
     * @param post 게시글
     * @param userId 요청 사용자 ID
     * @throws BusinessException POST_003(403)
     */
    public void canModify(Post post, Long userId) {
        if (!post.isOwner(userId)) {
            throw new BusinessException(ErrorCode.POST_003);
        }
    }
}
