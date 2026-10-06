package com.somepro.domain.obs.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 野生动物观测记录聚合根（领域层）：巡护现场看到野生动物时当场记下的一条账。
 *
 * 业务规则：
 * - 一条观测一条记录，编号 obsNo 形如 WO-2026-000001，全局唯一，由仓储层落库时分配；
 * - 记挂在哪条巡护任务下（taskId）、在哪个点看到（siteId）、物种编码（speciesCode）、
 *   个体数量（individualCount）、健康状态（healthStatus）、观测时刻（observedAt）、记录人（recorder）；
 * - 健康状态 NORMAL 正常 / INJURED 受伤 / DEAD 死亡 / SUSPECT 疑似疫病，新记的默认 NORMAL；
 * - 个体数量必须是正数，写 0 或负数一律不收；
 * - 保护级别 protectionLevel 是「观测当时」从物种名录抄来的快照，抄进来就不再跟着名录变
 *   （名录后来把级别调高调低，老观测照旧是当初那份），只有改挂另一物种时才重新抄一份；
 * - 录观测的两道前置（任务得在执行中、物种得在名录且启用）要查任务/物种仓储，由应用层编排；
 * - 录岔了的可以作废：作废即逻辑删除（del_flag=1），清单翻不到，底子仍留在库里备查，
 *   作废记录不允许再改。
 */
@Getter
@Setter
public class WildlifeObs extends BaseEntity {

    /** 健康状态：正常 */
    public static final String HEALTH_NORMAL = "NORMAL";
    /** 健康状态：受伤 */
    public static final String HEALTH_INJURED = "INJURED";
    /** 健康状态：死亡 */
    public static final String HEALTH_DEAD = "DEAD";
    /** 健康状态：疑似疫病 */
    public static final String HEALTH_SUSPECT = "SUSPECT";

    /** 健康状态合法取值（异常口径见 {@link ObsSummary#ABNORMAL_HEALTH}）。 */
    private static final Set<String> HEALTH_STATUSES =
            Set.of(HEALTH_NORMAL, HEALTH_INJURED, HEALTH_DEAD, HEALTH_SUSPECT);

    private Long id;

    /** 观测编号（如 WO-2026-000001），全局唯一 */
    private String obsNo;

    /** 挂在哪条巡护任务下（t_patrol_task.id） */
    private Long taskId;

    /** 在哪个监测点看到（t_monitor_site.id） */
    private Long siteId;

    /** 物种编码（t_species.species_code） */
    private String speciesCode;

    /** 观测当时的保护级别快照（从物种名录抄一份，之后不随名录变） */
    private String protectionLevel;

    /** 个体数量（正数） */
    private Integer individualCount;

    /** 健康状态：NORMAL / INJURED / DEAD / SUSPECT，默认 NORMAL */
    private String healthStatus;

    /** 观测时刻 */
    private LocalDateTime observedAt;

    /** 记录人 */
    private String recorder;

    /**
     * 工厂方法：当场录入一条观测。
     *
     * @param protectionLevel 物种名录里该物种当前写着的保护级别（由应用层查实后传入，抄作快照）
     */
    public static WildlifeObs record(Long taskId, Long siteId, String speciesCode, String protectionLevel,
                                     Integer individualCount, String healthStatus,
                                     LocalDateTime observedAt, String recorder) {
        WildlifeObs obs = new WildlifeObs();
        obs.attachTask(taskId);
        obs.attachSite(siteId);
        obs.attachSpecies(speciesCode, protectionLevel);
        obs.changeIndividualCount(individualCount);
        obs.changeHealthStatus(healthStatus == null || healthStatus.isBlank() ? HEALTH_NORMAL : healthStatus);
        obs.changeObservedAt(observedAt);
        obs.changeRecorder(recorder);
        return obs;
    }

    public void attachTask(Long taskId) {
        if (taskId == null) {
            throw new BizException("巡护任务不能为空");
        }
        this.taskId = taskId;
    }

    public void attachSite(Long siteId) {
        if (siteId == null) {
            throw new BizException("监测点不能为空");
        }
        this.siteId = siteId;
    }

    /**
     * 挂上物种并抄一份当时的保护级别快照。
     * 级别由应用层从启用中的名录记录查实后传入，领域对象只保证快照不空。
     */
    public void attachSpecies(String speciesCode, String protectionLevel) {
        if (speciesCode == null || speciesCode.isBlank()) {
            throw new BizException("物种编码不能为空");
        }
        if (protectionLevel == null || protectionLevel.isBlank()) {
            throw new BizException("保护级别快照不能为空");
        }
        this.speciesCode = speciesCode.trim();
        this.protectionLevel = protectionLevel.trim();
    }

    public void changeIndividualCount(Integer individualCount) {
        if (individualCount == null || individualCount <= 0) {
            throw new BizException("个体数量必须为正数");
        }
        this.individualCount = individualCount;
    }

    public void changeHealthStatus(String healthStatus) {
        if (healthStatus == null || !HEALTH_STATUSES.contains(healthStatus)) {
            throw new BizException("健康状态非法，仅支持 NORMAL/INJURED/DEAD/SUSPECT");
        }
        this.healthStatus = healthStatus;
    }

    public void changeObservedAt(LocalDateTime observedAt) {
        if (observedAt == null) {
            throw new BizException("观测时刻不能为空");
        }
        this.observedAt = observedAt;
    }

    public void changeRecorder(String recorder) {
        if (recorder == null || recorder.isBlank()) {
            throw new BizException("记录人不能为空");
        }
        this.recorder = recorder.trim();
    }

    /**
     * 改观测：传入的字段才改（null 表示不动）。作废记录不允许再改。
     * 改挂任务/点/物种的前置把关（任务执行中、点存在、物种启用）由应用层编排：
     * - 换物种（speciesCode 非空）时，protectionLevel 传名录里新物种当前的级别，重新抄一份快照；
     * - 不换物种时 protectionLevel 传 null，老快照原样保留，不跟着名录后来的调整变。
     */
    public void revise(Long taskId, Long siteId, String speciesCode, String protectionLevel,
                       Integer individualCount, String healthStatus,
                       LocalDateTime observedAt, String recorder) {
        if (isVoided()) {
            throw new BizException("观测记录已作废，不能再修改");
        }
        if (taskId != null) {
            attachTask(taskId);
        }
        if (siteId != null) {
            attachSite(siteId);
        }
        if (speciesCode != null) {
            attachSpecies(speciesCode, protectionLevel);
        }
        if (individualCount != null) {
            changeIndividualCount(individualCount);
        }
        if (healthStatus != null) {
            changeHealthStatus(healthStatus);
        }
        if (observedAt != null) {
            changeObservedAt(observedAt);
        }
        if (recorder != null) {
            changeRecorder(recorder);
        }
    }

    /** 是否已作废（逻辑删除，del_flag=1）。作废记录清单翻不到、底子留库备查、不再改动。 */
    public boolean isVoided() {
        return getDelFlag() != null && getDelFlag() == 1;
    }
}
