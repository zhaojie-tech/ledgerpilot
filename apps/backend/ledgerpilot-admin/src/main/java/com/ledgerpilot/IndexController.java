package com.ledgerpilot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 根路径索引（外部用户直接访问 :8080/ 时给出可用入口）。
 */
@RestController
public class IndexController {

    @GetMapping("/")
    public Map<String, Object> index() {
        return Map.of(
                "service", "LedgerPilot Backend",
                "status", "up",
                "endpoints", Map.of(
                        "login", "POST /api/auth/login",
                        "vouchers", "GET /api/voucher/page（需 Bearer token）",
                        "subjects", "GET /api/subject/tree（需 Bearer token）",
                        "aiIntegration", "GET /api/nlp/ping（需 Bearer token，联调 AI 服务）",
                        "health", "GET /actuator/health"
                ),
                "quickstart", "POST /api/auth/login {\"username\":\"admin\",\"password\":\"123456\"}"
        );
    }
}
