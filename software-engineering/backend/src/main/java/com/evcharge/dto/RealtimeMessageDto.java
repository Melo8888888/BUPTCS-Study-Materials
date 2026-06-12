package com.evcharge.dto;

import java.time.LocalDateTime;

public record RealtimeMessageDto(
        String type,
        LocalDateTime publishedAt,
        SystemSnapshot snapshot
) {}
