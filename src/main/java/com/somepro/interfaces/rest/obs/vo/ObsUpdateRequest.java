package com.somepro.interfaces.rest.obs.vo;

import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 修改观测请求（VO，用户接口层）：所有字段都可空，传啥改啥（null/缺省表示不动）。
 *
 * 换任务要重新过「执行中」校验，换物种要重新过「在名录且启用」校验并重抄保护级别快照；
 * 字段一旦给了就得合法：物种编码给了不能是空白（要改就得是名录里的真编码，空白在应用层拦），
 * 个体数量给了就得是正数（0/负数由 @Positive 拦下）。
 */
public record ObsUpdateRequest(
        Long taskId,
        Long siteId,
        String speciesCode,
        @Positive(message = "个体数量必须为正数") Integer individualCount,
        String healthStatus,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime observedAt,
        String recorder) implements Serializable {
}
