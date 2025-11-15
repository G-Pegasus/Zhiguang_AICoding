package com.tongji.counter.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActionRequest {
    @NotBlank
    private String entityType; // 如: knowpost
    @NotBlank
    private String entityId;   // 内容ID
}