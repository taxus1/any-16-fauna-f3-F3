package com.somepro.application.obs;

import com.somepro.common.exception.BizException;
import com.somepro.domain.obs.model.WildlifeObs;
import com.somepro.domain.obs.repository.WildlifeObsRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.domain.site.model.MonitorSite;
import com.somepro.domain.site.repository.MonitorSiteRepository;
import com.somepro.domain.species.model.Species;
import com.somepro.domain.species.repository.SpeciesRepository;
import com.somepro.domain.task.model.PatrolTask;
import com.somepro.domain.task.repository.PatrolTaskRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 野生动物观测应用层：编排观测用例（当场录入、修改、查看、作废、条件分页）。
 *
 * 录入两道前置，缺一不可：
 * - 任务得处在「执行中」（IN_PROGRESS）：还没开工（PENDING）、已完成（DONE）、已取消（CANCELLED）
 *   的任务都不再往底下录；
 * - 物种得在名录里且处在启用状态（ENABLED）：名录里没有的编码、停用（DISABLED）的物种都录不进来，
 *   不拿别的编码糊弄。
 * 另外：观测上留一份「当时的保护级别」，照着名录里该物种当前写着的级别抄一份进来
 * （protectionLevel 快照）。这份快照以后不跟着名录走 —— 名录后来把级别调高调低，
 * 老观测照旧是当初那份；只有改挂另一物种时才重新抄一份。
 */
@Service
public class WildlifeObsAppService {

    private final WildlifeObsRepository obsRepository;
    private final PatrolTaskRepository taskRepository;
    private final MonitorSiteRepository siteRepository;
    private final SpeciesRepository speciesRepository;

    public WildlifeObsAppService(WildlifeObsRepository obsRepository,
                                 PatrolTaskRepository taskRepository,
                                 MonitorSiteRepository siteRepository,
                                 SpeciesRepository speciesRepository) {
        this.obsRepository = obsRepository;
        this.taskRepository = taskRepository;
        this.siteRepository = siteRepository;
        this.speciesRepository = speciesRepository;
    }

    /** 当场录入一条观测：编号由仓储层按 WO-YYYY-NNNNNN 生成，健康状态不传默认正常。 */
    public Mono<WildlifeObs> record(Long taskId, Long siteId, String speciesCode, Integer individualCount,
                                    String healthStatus, LocalDateTime observedAt, String recorder) {
        return requireRunningTask(taskId)
                .then(requireExistingSite(siteId))
                .then(requireEnabledSpecies(speciesCode))
                .flatMap(species -> {
                    WildlifeObs obs = WildlifeObs.record(taskId, siteId, species.getSpeciesCode(),
                            species.getProtectionLevel(), individualCount, healthStatus, observedAt, recorder);
                    return obsRepository.create(obs);
                });
    }

    /**
     * 修改观测：传啥改啥（null 表示不动）。
     * - 换任务（taskId 非空且不同于原任务）：新任务同样得在执行中；
     * - 换点位（siteId 非空）：新点得存在；
     * - 换物种（speciesCode 非空且不同于原编码）：新物种得在名录且启用，并按其当前级别重抄一份快照；
     *   仍是原物种的，老快照原样保留，不跟着名录后来的调整变；
     * - 已作废的记录改不动（仓储查不出来，直接报不存在）。
     */
    public Mono<WildlifeObs> revise(Long id, Long taskId, Long siteId, String speciesCode,
                                    Integer individualCount, String healthStatus,
                                    LocalDateTime observedAt, String recorder) {
        return obsRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("观测记录不存在或已作废")))
                .flatMap(obs -> {
                    Mono<Void> taskCheck = (taskId != null && !taskId.equals(obs.getTaskId()))
                            ? requireRunningTask(taskId).then()
                            : Mono.empty();
                    Mono<Void> siteCheck = (siteId != null && !siteId.equals(obs.getSiteId()))
                            ? requireExistingSite(siteId).then()
                            : Mono.empty();
                    Mono<Optional<Species>> speciesResolve = resolveSpeciesForRevise(obs, speciesCode);
                    return Mono.when(taskCheck, siteCheck)
                            .then(speciesResolve)
                            .flatMap(speciesOpt -> {
                                // 空 Optional 表示不换物种：快照原样保留（attachSpecies 不被触发）
                                Species species = speciesOpt.orElse(null);
                                String newSpeciesCode = species == null ? null : species.getSpeciesCode();
                                String newLevel = species == null ? null : species.getProtectionLevel();
                                obs.revise(taskId, siteId, newSpeciesCode, newLevel,
                                        individualCount, healthStatus, observedAt, recorder);
                                return obsRepository.update(obs);
                            });
                });
    }

    /** 查看一条在册观测；不存在或已作废报不存在。 */
    public Mono<WildlifeObs> detail(Long id) {
        return obsRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("观测记录不存在或已作废")));
    }

    /** 作废：清单里翻不到，底子仍留在库里备查；重复作废按不存在处理。 */
    public Mono<Void> voidObs(Long id) {
        return obsRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("观测记录不存在或已作废")))
                .flatMap(obs -> obsRepository.voidObs(obs.getId()));
    }

    /** 条件分页：任务/点位/物种/健康状态随意拼，全空翻整份在册观测，每行带观测编号。 */
    public Mono<PageResult<WildlifeObs>> pageObs(int pageNum, int pageSize,
                                                 Long taskId, Long siteId,
                                                 String speciesCode, String healthStatus) {
        return obsRepository.page(pageNum, pageSize, taskId, siteId,
                trimToNull(speciesCode), trimToNull(healthStatus));
    }

    /**
     * 修改时解析物种：
     * - 没传编码（null）：不换物种，返回 null（领域 revise 收到 null 不动物种与快照）；
     * - 传的编码和原编码一致：视作不换物种，返回 null —— 老快照不随名录后续调整刷新；
     * - 传的是别的编码：必须在名录且启用，返回该物种（携带当前级别用于重抄快照）。
     */
    private Mono<Optional<Species>> resolveSpeciesForRevise(WildlifeObs obs, String speciesCode) {
        String code = trimToNull(speciesCode);
        if (code == null || code.equals(obs.getSpeciesCode())) {
            return Mono.just(Optional.empty());
        }
        return requireEnabledSpecies(code).map(Optional::of);
    }

    /** 任务必须存在且处在执行中：没开工/已完成/已取消的任务都不能再录观测。 */
    private Mono<PatrolTask> requireRunningTask(Long taskId) {
        if (taskId == null) {
            return Mono.error(new BizException("巡护任务不能为空"));
        }
        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new BizException("巡护任务不存在")))
                .flatMap(task -> {
                    if (!PatrolTask.STATUS_IN_PROGRESS.equals(task.getStatus())) {
                        return Mono.error(new BizException("只有执行中的巡护任务才能录观测"
                                + "（待执行/已完成/已取消的任务不收）"));
                    }
                    return Mono.just(task);
                });
    }

    /** 点位必须存在（在名册、未撤点）；点停测不影响已挂观测的记录。 */
    private Mono<MonitorSite> requireExistingSite(Long siteId) {
        if (siteId == null) {
            return Mono.error(new BizException("监测点不能为空"));
        }
        return siteRepository.findById(siteId)
                .switchIfEmpty(Mono.error(new BizException("监测点不存在")));
    }

    /** 物种必须在名录里（未删除）且启用：查不到或已停用都录不进来。 */
    private Mono<Species> requireEnabledSpecies(String speciesCode) {
        String code = trimToNull(speciesCode);
        if (code == null) {
            return Mono.error(new BizException("物种编码不能为空"));
        }
        return speciesRepository.findByCode(code)
                .switchIfEmpty(Mono.error(new BizException("物种不在名录中，不能录观测：" + code)))
                .flatMap(species -> {
                    if (Species.STATUS_DISABLED.equals(species.getStatus())) {
                        return Mono.error(new BizException("物种已在名录停用，不能再录观测：" + code));
                    }
                    return Mono.just(species);
                });
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
