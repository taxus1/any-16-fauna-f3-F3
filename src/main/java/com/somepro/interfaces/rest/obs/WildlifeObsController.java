package com.somepro.interfaces.rest.obs;

import com.somepro.application.obs.WildlifeObsAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.obs.converter.WildlifeObsVoConverter;
import com.somepro.interfaces.rest.obs.vo.ObsCreateRequest;
import com.somepro.interfaces.rest.obs.vo.ObsUpdateRequest;
import com.somepro.interfaces.rest.obs.vo.WildlifeObsVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 野生动物观测接口（用户接口层）：只做协议适配与 VO 转换，业务编排交给应用层。
 *
 * 提供录入、修改、查看、作废（DELETE，逻辑删除，底子留库）与条件分页；
 * 分页条件 任务/点位/物种/健康状态 随意拼，全空翻整份在册观测，每行带观测编号。
 */
@RestController
@RequestMapping("/api/observations")
public class WildlifeObsController {

    private final WildlifeObsAppService obsAppService;

    public WildlifeObsController(WildlifeObsAppService obsAppService) {
        this.obsAppService = obsAppService;
    }

    /** 当场录入：编号服务端生成（WO-YYYY-NNNNNN），健康状态不传默认 NORMAL，保护级别照名录抄快照。 */
    @PostMapping
    public Mono<Result<WildlifeObsVO>> record(@Valid @RequestBody ObsCreateRequest req) {
        return obsAppService.record(req.taskId(), req.siteId(), req.speciesCode(),
                        req.individualCount(), req.healthStatus(), req.observedAt(), req.recorder())
                .map(WildlifeObsVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<WildlifeObsVO>> detail(@PathVariable Long id) {
        return obsAppService.detail(id)
                .map(WildlifeObsVoConverter::toVo)
                .map(Result::ok);
    }

    /** 修改：传啥改啥；换任务要在执行中、换物种要启用并重抄快照；已作废的改不动。 */
    @PutMapping("/{id}")
    public Mono<Result<WildlifeObsVO>> revise(@PathVariable Long id,
                                              @Valid @RequestBody ObsUpdateRequest req) {
        return obsAppService.revise(id, req.taskId(), req.siteId(), req.speciesCode(),
                        req.individualCount(), req.healthStatus(), req.observedAt(), req.recorder())
                .map(WildlifeObsVoConverter::toVo)
                .map(Result::ok);
    }

    /** 作废（逻辑删除）：清单翻不到，底子留库备查；重复作废按不存在处理。 */
    @DeleteMapping("/{id}")
    public Mono<Result<Void>> voidObs(@PathVariable Long id) {
        return obsAppService.voidObs(id)
                .then(Mono.just(Result.ok()));
    }

    /** 条件分页：taskId/siteId/speciesCode/healthStatus 随意拼，全空翻整份在册观测。 */
    @GetMapping({"", "/list"})
    public Mono<Result<PageVO<WildlifeObsVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String speciesCode,
            @RequestParam(required = false) String healthStatus) {
        return obsAppService.pageObs(pageNum, pageSize, taskId, siteId, speciesCode, healthStatus)
                .map(WildlifeObsVoConverter::toPageVo)
                .map(Result::ok);
    }
}
