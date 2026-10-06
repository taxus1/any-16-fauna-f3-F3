package com.somepro.domain.obs.repository;

import com.somepro.domain.obs.model.ObsSummary;
import com.somepro.domain.obs.model.WildlifeObs;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 野生动物观测记录的仓储端口（领域层定义，基础设施层实现）。
 *
 * 读能力「按任务归拢观测账」供巡护任务完成回报时汇总；
 * 观测录入（写侧）与任务执行共用同一个端口、同一张表，两边数字才一致。
 */
public interface WildlifeObsRepository {

    /**
     * 归拢某任务名下的观测账：观测记录总条数，以及其中异常（受伤/死亡/疑似疫病）的条数。
     * 只数在册记录（del_flag=0），与观测录入的可见口径一致。
     */
    Mono<ObsSummary> summarizeByTaskId(Long taskId);

    /**
     * 录入落库；obsNo 由实现侧按 WO-YYYY-NNNNNN（6 位序号）自动生成，并发撞号重试，绝不重号。
     */
    Mono<WildlifeObs> create(WildlifeObs obs);

    /** 按 id 更新观测（编号不改，保护级别等字段随更新写回）。 */
    Mono<WildlifeObs> update(WildlifeObs obs);

    /** 按 id 查在册观测；不存在或已作废（del_flag=1）返回空。 */
    Mono<WildlifeObs> findById(Long id);

    /**
     * 作废：逻辑删除（@TableLogic 置 del_flag=1）。作废后清单里翻不到，底子仍留在表里备查。
     */
    Mono<Void> voidObs(Long id);

    /**
     * 条件分页：任务/点位/物种/健康状态随意拼，全空翻整份在册观测（del_flag=0），每行带观测编号。
     */
    Mono<PageResult<WildlifeObs>> page(int pageNum, int pageSize,
                                       Long taskId, Long siteId, String speciesCode, String healthStatus);
}
