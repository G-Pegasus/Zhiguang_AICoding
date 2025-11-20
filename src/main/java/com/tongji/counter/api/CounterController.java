package com.tongji.counter.api;

import com.tongji.counter.api.dto.CountsResponse;
import com.tongji.counter.schema.CounterSchema;
import com.tongji.counter.service.CounterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 计数读取接口：返回指定实体在给定指标上的汇总计数（SDS）。
 */
@RestController
@RequestMapping("/api/v1/counter")
public class CounterController {

    private final CounterService counterService;

    public CounterController(CounterService counterService) {
        this.counterService = counterService;
    }

    /**
     * 获取实体的计数汇总。
     * @param entityType 实体类型（如 knowpost）
     * @param entityId 实体ID
     * @param metricsStr 指标列表（逗号分隔），为空则返回全部支持指标
     */
    @GetMapping("/{etype}/{eid}")
    public ResponseEntity<CountsResponse> getCounts(@PathVariable("etype") String entityType,
                                                    @PathVariable("eid") String entityId,
                                                    @RequestParam(value = "metrics", required = false) String metricsStr) {
        List<String> metrics;
        if (metricsStr == null || metricsStr.isBlank()) {
            metrics = new ArrayList<>(CounterSchema.SUPPORTED_METRICS); // 未指定指标时返回全部支持的计数
        } else {
            metrics = Arrays.stream(metricsStr.split(","))
                    .map(String::trim)
                    .filter(CounterSchema.SUPPORTED_METRICS::contains) // 过滤未知指标，保证请求安全
                    .toList();
        }

        Map<String, Long> counts = counterService.getCounts(entityType, entityId, metrics);

        return ResponseEntity.ok(new CountsResponse(entityType, entityId, counts));
    }
}