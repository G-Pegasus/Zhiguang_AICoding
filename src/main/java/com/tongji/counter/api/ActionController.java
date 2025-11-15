package com.tongji.counter.api;

import com.tongji.counter.api.dto.ActionRequest;
import com.tongji.counter.service.CounterService;
import com.tongji.auth.token.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/action")
public class ActionController {

    private final CounterService counterService;
    private final JwtService jwtService;

    public ActionController(CounterService counterService, JwtService jwtService) {
        this.counterService = counterService;
        this.jwtService = jwtService;
    }

    @PostMapping("/like")
    public ResponseEntity<Map<String, Object>> like(@Valid @RequestBody ActionRequest req,
                                                    @AuthenticationPrincipal Jwt jwt) {
        long uid = jwtService.extractUserId(jwt);
        boolean changed = counterService.like(req.getEntityType(), req.getEntityId(), uid);
        return ResponseEntity.ok(Map.of(
                "changed", changed,
                "liked", counterService.isLiked(req.getEntityType(), req.getEntityId(), uid)
        ));
    }

    @PostMapping("/unlike")
    public ResponseEntity<Map<String, Object>> unlike(@Valid @RequestBody ActionRequest req,
                                                      @AuthenticationPrincipal Jwt jwt) {
        long uid = jwtService.extractUserId(jwt);
        boolean changed = counterService.unlike(req.getEntityType(), req.getEntityId(), uid);
        return ResponseEntity.ok(Map.of(
                "changed", changed,
                "liked", counterService.isLiked(req.getEntityType(), req.getEntityId(), uid)
        ));
    }

    @PostMapping("/fav")
    public ResponseEntity<Map<String, Object>> fav(@Valid @RequestBody ActionRequest req,
                                                   @AuthenticationPrincipal Jwt jwt) {
        long uid = jwtService.extractUserId(jwt);
        boolean changed = counterService.fav(req.getEntityType(), req.getEntityId(), uid);
        return ResponseEntity.ok(Map.of(
                "changed", changed,
                "faved", counterService.isFaved(req.getEntityType(), req.getEntityId(), uid)
        ));
    }

    @PostMapping("/unfav")
    public ResponseEntity<Map<String, Object>> unfav(@Valid @RequestBody ActionRequest req,
                                                     @AuthenticationPrincipal Jwt jwt) {
        long uid = jwtService.extractUserId(jwt);
        boolean changed = counterService.unfav(req.getEntityType(), req.getEntityId(), uid);
        return ResponseEntity.ok(Map.of(
                "changed", changed,
                "faved", counterService.isFaved(req.getEntityType(), req.getEntityId(), uid)
        ));
    }
}