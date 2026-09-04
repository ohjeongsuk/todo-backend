package com.example.todoapp.service.storage;

/**
 * 서명 토큰의 용도. 토큰 클레임에 담아 교차 사용을 막는다.
 *
 * <p>용도를 구분하지 않으면 조회용으로 발급한 토큰으로 업로드 엔드포인트를 호출할 수 있다. 조회 URL은 브라우저
 * {@code <img>} 태그에 그대로 노출되므로 이 구분이 없으면 노출된 토큰이 곧 쓰기 권한이 된다.
 */
public enum StoragePurpose {
    UPLOAD,
    VIEW
}
