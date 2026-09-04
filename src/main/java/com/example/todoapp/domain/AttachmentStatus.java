package com.example.todoapp.domain;

/**
 * 첨부 파일의 생명주기 상태.
 *
 * <p>{@code PENDING}과 {@code UPLOADED}를 나누는 이유는 정리 배치가 두 경우에 서로 다른 정책을 적용해야 하기 때문이다.
 * {@code PENDING}은 파일이 아직 없으므로 레코드만 지우면 되지만, {@code UPLOADED}는 실제 파일까지 지워야 한다. 상태를
 * 하나로 합치면 "업로드를 시작만 하고 만 레코드"와 "업로드는 끝났지만 사용자가 할 일을 저장하지 않은 레코드"를 구분할 수 없다.
 */
public enum AttachmentStatus {

    /** presign 응답 시점. 레코드만 있고 스토리지에 파일이 없다. */
    PENDING,

    /** complete 성공 시점. 파일은 확정됐으나 아직 할 일에 연결되지 않았다. */
    UPLOADED,

    /** 할 일 저장 시점. 본문 HTML에 실제로 존재하는 첨부다. */
    LINKED
}
