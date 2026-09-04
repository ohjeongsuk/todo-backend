package com.example.todoapp.domain;

/**
 * 첨부 파일의 실제 저장 위치.
 *
 * <p>레코드마다 보관하는 이유는 로컬에서 만든 데이터와 S3 전환 이후 데이터가 한 테이블에 섞여도 각각 올바른 방식으로 조회하기 위함이다.
 * 전환 시점에 기존 행을 마이그레이션하지 않아도 된다.
 */
public enum StorageType {

    /** 로컬 디스크. {@code app.storage.local.base-dir} 하위에 저장한다. */
    LOCAL,

    /** Amazon S3 버킷. */
    S3
}
