package com.somepro.interfaces.rest.obs.converter;

import com.somepro.domain.obs.model.WildlifeObs;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.obs.vo.WildlifeObsVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * WildlifeObs（领域）→ WildlifeObsVO（对外）转换器（用户接口层）。
 */
public final class WildlifeObsVoConverter {

    private WildlifeObsVoConverter() {
    }

    public static WildlifeObsVO toVo(WildlifeObs obs) {
        return new WildlifeObsVO(obs.getId(), obs.getObsNo(), obs.getTaskId(), obs.getSiteId(),
                obs.getSpeciesCode(), obs.getProtectionLevel(), obs.getIndividualCount(),
                obs.getHealthStatus(), obs.getObservedAt(), obs.getRecorder(), obs.getCreateTime());
    }

    public static PageVO<WildlifeObsVO> toPageVo(PageResult<WildlifeObs> page) {
        List<WildlifeObsVO> content = page.content().stream()
                .map(WildlifeObsVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
