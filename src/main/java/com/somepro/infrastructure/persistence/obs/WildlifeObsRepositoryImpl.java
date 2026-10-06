package com.somepro.infrastructure.persistence.obs;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.obs.model.ObsSummary;
import com.somepro.domain.obs.model.WildlifeObs;
import com.somepro.domain.obs.repository.WildlifeObsRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import com.somepro.infrastructure.persistence.obs.converter.WildlifeObsPoConverter;
import com.somepro.infrastructure.persistence.obs.po.WildlifeObsPO;
import com.somepro.infrastructure.persistence.support.BizNoGenerator;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 野生动物观测记录仓储适配器（基础设施层）。
 *
 * - 编号分配：obsNo 一律由这里按 WO-YYYY-NNNNNN 生成（序号 6 位，并发撞号由
 *   {@link BizNoGenerator} 重试，唯一索引兜底，绝不重号），作废占用的号也不复用；
 * - 数账口径：selectCount 走 @TableLogic 自动拼 del_flag=0，与观测清单的可见口径一致；
 *   异常按 {@link ObsSummary#ABNORMAL_HEALTH}（受伤/死亡/疑似疫病）数；
 * - 作废：deleteById 被 @TableLogic 改写为 UPDATE SET del_flag=1，清单翻不到、底子留表。
 */
@Repository
public class WildlifeObsRepositoryImpl implements WildlifeObsRepository {

    /** 编号前缀：WO-年-（如 WO-2026-） */
    private static final String NO_PREFIX = "WO-";

    /** 观测编号序号宽度：6 位（WO-2026-000001） */
    private static final int SEQ_WIDTH = 6;

    private final WildlifeObsMapper obsMapper;

    public WildlifeObsRepositoryImpl(WildlifeObsMapper obsMapper) {
        this.obsMapper = obsMapper;
    }

    @Override
    public Mono<ObsSummary> summarizeByTaskId(Long taskId) {
        return blocking(() -> {
            long obsCount = obsMapper.selectCount(Wrappers.<WildlifeObsPO>lambdaQuery()
                    .eq(WildlifeObsPO::getTaskId, taskId));
            long abnormalCount = obsMapper.selectCount(Wrappers.<WildlifeObsPO>lambdaQuery()
                    .eq(WildlifeObsPO::getTaskId, taskId)
                    .in(WildlifeObsPO::getHealthStatus, ObsSummary.ABNORMAL_HEALTH));
            return new ObsSummary(Math.toIntExact(obsCount), Math.toIntExact(abnormalCount));
        });
    }

    @Override
    public Mono<WildlifeObs> create(WildlifeObs obs) {
        return blocking(() -> {
            String prefix = NO_PREFIX + LocalDate.now().getYear() + "-";
            return BizNoGenerator.insertWithRetry(
                    () -> obsMapper.selectMaxSeq(prefix, prefix.length() + 1),
                    prefix,
                    no -> doInsert(obs, no),
                    SEQ_WIDTH);
        });
    }

    @Override
    public Mono<WildlifeObs> update(WildlifeObs obs) {
        return blocking(() -> {
            WildlifeObsPO po = WildlifeObsPoConverter.toPo(obs);
            obsMapper.updateById(po);
            return WildlifeObsPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<WildlifeObs> findById(Long id) {
        return blocking(() -> {
            WildlifeObsPO po = obsMapper.selectById(id);
            return po == null ? null : WildlifeObsPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<Void> voidObs(Long id) {
        return blocking(() -> {
            // @TableLogic：DELETE 改写为 UPDATE t_wildlife_obs SET del_flag = 1 WHERE id = ? AND del_flag = 0
            obsMapper.deleteById(id);
            return Boolean.TRUE;
        }).then();
    }

    @Override
    public Mono<PageResult<WildlifeObs>> page(int pageNum, int pageSize,
                                              Long taskId, Long siteId, String speciesCode,
                                              String healthStatus) {
        return this.<PageResult<WildlifeObs>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<WildlifeObsPO> wrapper = Wrappers.<WildlifeObsPO>lambdaQuery()
                        .eq(taskId != null, WildlifeObsPO::getTaskId, taskId)
                        .eq(siteId != null, WildlifeObsPO::getSiteId, siteId)
                        .eq(hasText(speciesCode), WildlifeObsPO::getSpeciesCode, speciesCode)
                        .eq(hasText(healthStatus), WildlifeObsPO::getHealthStatus, healthStatus)
                        .orderByAsc(WildlifeObsPO::getId);
                List<WildlifeObsPO> rows = obsMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<WildlifeObs> content = rows.stream()
                        .map(WildlifeObsPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }

    private WildlifeObs doInsert(WildlifeObs obs, String obsNo) {
        obs.setObsNo(obsNo);
        WildlifeObsPO po = WildlifeObsPoConverter.toPo(obs);
        po.setId(IdUtil.getSnowflakeNextId());
        obsMapper.insert(po);
        return WildlifeObsPoConverter.toDomain(po);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 阻塞 DB 调用 → 响应式链路的桥接器：先取 Reactor Context 里的操作人，
     * 再切到 boundedElastic 执行 JDBC，操作人放进 AuditContextHolder 供审计填充。
     */
    private <T> Mono<T> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(ctx -> {
            String operator = ReactiveOperatorContext.getOperator(ctx);
            return Mono.fromCallable(() -> {
                AuditContextHolder.setOperator(operator);
                try {
                    return supplier.get();
                } finally {
                    AuditContextHolder.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }
}
