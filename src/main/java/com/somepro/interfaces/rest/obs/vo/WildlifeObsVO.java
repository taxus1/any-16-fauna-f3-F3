package com.somepro.interfaces.rest.obs.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 野生动物观测对外返回对象（VO，用户接口层）—— 不可变 record。观测编号 obsNo 必带。
 *
 * protectionLevel 是观测当时从物种名录抄来的快照，与名录当前级别解耦，对账时以这份为准。
 */
public record WildlifeObsVO(Long id, String obsNo, Long taskId, Long siteId, String speciesCode,
                            String protectionLevel, Integer individualCount, String healthStatus,
                            LocalDateTime observedAt, String recorder,
                            LocalDateTime createTime) implements Serializable {
}
