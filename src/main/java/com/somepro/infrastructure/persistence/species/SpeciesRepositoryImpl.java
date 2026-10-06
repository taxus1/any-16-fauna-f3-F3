package com.somepro.infrastructure.persistence.species;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.domain.species.model.Species;
import com.somepro.domain.species.repository.SpeciesRepository;
import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import com.somepro.infrastructure.persistence.species.converter.SpeciesPoConverter;
import com.somepro.infrastructure.persistence.species.po.SpeciesPO;
import com.somepro.infrastructure.persistence.support.BizNoGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 物种名录仓储适配器（基础设施层）。
 *
 * 编码分配：speciesCode 为空时按 SP-NNNN 生成（并发撞码由 {@link BizNoGenerator} 重试）；
 * 指定编码时撞唯一索引转业务异常，一个编码只归一个物种。
 */
@Repository
public class SpeciesRepositoryImpl implements SpeciesRepository {

    /** 编码前缀：SP-（无年份段，如 SP-0001） */
    private static final String CODE_PREFIX = "SP-";

    private final SpeciesMapper speciesMapper;

    public SpeciesRepositoryImpl(SpeciesMapper speciesMapper) {
        this.speciesMapper = speciesMapper;
    }

    @Override
    public Mono<Species> create(Species species) {
        return blocking(() -> {
            if (species.getSpeciesCode() != null && !species.getSpeciesCode().isBlank()) {
                try {
                    return doInsert(species, species.getSpeciesCode().trim());
                } catch (DuplicateKeyException e) {
                    throw new BizException("物种编码已存在：" + species.getSpeciesCode());
                }
            }
            return BizNoGenerator.insertWithRetry(
                    () -> speciesMapper.selectMaxSeq(CODE_PREFIX, CODE_PREFIX.length() + 1),
                    CODE_PREFIX,
                    code -> doInsert(species, code));
        });
    }

    @Override
    public Mono<Species> update(Species species) {
        return blocking(() -> {
            SpeciesPO po = SpeciesPoConverter.toPo(species);
            speciesMapper.updateById(po);
            return SpeciesPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<Species> findById(Long id) {
        return blocking(() -> {
            SpeciesPO po = speciesMapper.selectById(id);
            return po == null ? null : SpeciesPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<Species> findByCode(String speciesCode) {
        return blocking(() -> {
            SpeciesPO po = speciesMapper.selectOne(Wrappers.<SpeciesPO>lambdaQuery()
                    .eq(SpeciesPO::getSpeciesCode, speciesCode));
            return po == null ? null : SpeciesPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<Species>> page(int pageNum, int pageSize,
                                          String name, String protectionLevel, String status) {
        return this.<PageResult<Species>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<SpeciesPO> wrapper = Wrappers.<SpeciesPO>lambdaQuery()
                        .like(hasText(name), SpeciesPO::getName, name)
                        .eq(hasText(protectionLevel), SpeciesPO::getProtectionLevel, protectionLevel)
                        .eq(hasText(status), SpeciesPO::getStatus, status)
                        .orderByAsc(SpeciesPO::getId);
                List<SpeciesPO> rows = speciesMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<Species> content = rows.stream()
                        .map(SpeciesPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }

    private Species doInsert(Species species, String speciesCode) {
        species.setSpeciesCode(speciesCode);
        SpeciesPO po = SpeciesPoConverter.toPo(species);
        po.setId(IdUtil.getSnowflakeNextId());
        speciesMapper.insert(po);
        return SpeciesPoConverter.toDomain(po);
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
