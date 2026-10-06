package com.somepro.interfaces.rest.obs.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 当场录入观测请求（VO，用户接口层）。
 *
 * 前置校验由应用层把关：任务必须在执行中、物种必须在名录且启用；
 * 保护级别不用前端给，由服务端照名录当前级别抄一份快照。
 * healthStatus 可空（默认 NORMAL）；个体数量必须为正数，0/负数一律不收。
 */
public record ObsCreateRequest(
        @NotNull(message = "巡护任务不能为空") Long taskId,
        @NotNull(message = "监测点不能为空") Long siteId,
        @NotBlank(message = "物种编码不能为空") String speciesCode,
        @NotNull(message = "个体数量不能为空") @Positive(message = "个体数量必须为正数") Integer individualCount,
        String healthStatus,
        @NotNull(message = "观测时刻不能为空")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime observedAt,
        @NotBlank(message = "记录人不能为空") String recorder) implements Serializable {
}
