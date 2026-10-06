package com.somepro.infrastructure.persistence.obs.converter;

import com.somepro.domain.obs.model.WildlifeObs;
import com.somepro.infrastructure.persistence.obs.po.WildlifeObsPO;

/**
 * WildlifeObsPO（表）↔ WildlifeObs（领域）转换器（基础设施层）。
 */
public final class WildlifeObsPoConverter {

    private WildlifeObsPoConverter() {
    }

    public static WildlifeObsPO toPo(WildlifeObs domain) {
        WildlifeObsPO po = new WildlifeObsPO();
        po.setId(domain.getId());
        po.setObsNo(domain.getObsNo());
        po.setTaskId(domain.getTaskId());
        po.setSiteId(domain.getSiteId());
        po.setSpeciesCode(domain.getSpeciesCode());
        po.setProtectionLevel(domain.getProtectionLevel());
        po.setIndividualCount(domain.getIndividualCount());
        po.setHealthStatus(domain.getHealthStatus());
        po.setObservedAt(domain.getObservedAt());
        po.setRecorder(domain.getRecorder());
        po.setDelFlag(domain.getDelFlag());
        po.setCreateBy(domain.getCreateBy());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateBy(domain.getUpdateBy());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static WildlifeObs toDomain(WildlifeObsPO po) {
        WildlifeObs domain = new WildlifeObs();
        domain.setId(po.getId());
        domain.setObsNo(po.getObsNo());
        domain.setTaskId(po.getTaskId());
        domain.setSiteId(po.getSiteId());
        domain.setSpeciesCode(po.getSpeciesCode());
        domain.setProtectionLevel(po.getProtectionLevel());
        domain.setIndividualCount(po.getIndividualCount());
        domain.setHealthStatus(po.getHealthStatus());
        domain.setObservedAt(po.getObservedAt());
        domain.setRecorder(po.getRecorder());
        domain.setDelFlag(po.getDelFlag());
        domain.setCreateBy(po.getCreateBy());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateBy(po.getUpdateBy());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
