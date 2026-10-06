package com.somepro.infrastructure.persistence.support;

import com.somepro.common.exception.BizException;
import org.springframework.dao.DuplicateKeyException;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 业务编号生成器（基础设施层）：「取号 → 落库」一体化重试。
 *
 * 背景：编号按 ST-2026-0001 / MP-2026-0001 / SP-0001 式生成，序号取当前最大值 +1。
 * 并发下两个请求可能取到同一个号，数据库唯一索引会拦住后到的那个（DuplicateKeyException）。
 * 这里撞号后重新取号再试，保证：
 * - 一个号只落一份（唯一索引兜底）；
 * - 并发登记都能各自拿到新号落库；
 * - 底层冲突不甩给调用方，重试耗尽也只抛业务异常。
 */
public final class BizNoGenerator {

    /**
     * 撞号重试上限。最坏情况下第 N 个并发请求要重试 N 次（每轮只放行一个），
     * 上限取 64，远超批量并发登记的场景；耗尽也只抛业务异常，不甩底层错。
     */
    private static final int MAX_ATTEMPTS = 64;

    /** 序号位数：不足 4 位零填充（超过 4 位自然扩展，不截断） */
    private static final int SEQ_WIDTH = 4;

    private BizNoGenerator() {
    }

    /**
     * 取号并落库，撞号自动重取。
     *
     * @param maxSeqQuery 查当前号段内最大序号（无记录时返回 null）
     * @param prefix      编号前缀（如 "ST-2026-"、"SP-"）
     * @param insertWithNo 用给定编号执行落库；撞唯一索引时应让 DuplicateKeyException 抛出来
     * @param <T>         落库后的返回类型
     */
    public static <T> T insertWithRetry(Supplier<Long> maxSeqQuery, String prefix,
                                        Function<String, T> insertWithNo) {
        return insertWithRetry(maxSeqQuery, prefix, insertWithNo, SEQ_WIDTH);
    }

    /**
     * 取号并落库，撞号自动重取，序号宽度可指定。
     *
     * @param seqWidth 序号零填充宽度（观测编号 WO-YYYY-000001 用 6 位；站/点/任务号用默认 4 位）
     */
    public static <T> T insertWithRetry(Supplier<Long> maxSeqQuery, String prefix,
                                        Function<String, T> insertWithNo, int seqWidth) {
        for (int attempt = 1; ; attempt++) {
            Long maxSeq = maxSeqQuery.get();
            long next = (maxSeq == null ? 0 : maxSeq) + 1;
            String no = prefix + String.format("%0" + seqWidth + "d", next);
            try {
                return insertWithNo.apply(no);
            } catch (DuplicateKeyException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BizException("业务编号生成冲突，请重试");
                }
                // 并发撞号：重新取最大序号再试
            }
        }
    }
}
