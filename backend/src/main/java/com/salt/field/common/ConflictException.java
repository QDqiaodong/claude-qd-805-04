package com.salt.field.common;

/**
 * 并发冲突：客户端持有的状态（旧页面 / 旧请求 expectedStatus）已不是数据库当前状态。
 * 返回 409 + 可操作原因，并携带最新快照，失败方刷新即可看到最新状态与生效时间；
 * 已完成结果在任何情况下都不会被后到的提交覆盖。
 */
public class ConflictException extends RuntimeException {

    private final transient Object snapshot;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, Object snapshot) {
        super(message);
        this.snapshot = snapshot;
    }

    public Object getSnapshot() {
        return snapshot;
    }
}
