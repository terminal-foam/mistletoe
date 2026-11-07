package dev.xingguang.terminalblade.combat.execution;

/**
 * Boss 阶段锁血查询结果。用三态而不是 boolean，避免把“没有适配器”误当成“未锁血”。
 */
public enum PhaseLockStatus {
    LOCKED,
    UNLOCKED,
    UNKNOWN
}
