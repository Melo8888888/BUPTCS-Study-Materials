package com.evcharge.controller;

import com.evcharge.dto.RealtimeMessageDto;
import com.evcharge.service.StationService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class StationWebSocketController {
    private final StationService stationService;

    public StationWebSocketController(StationService stationService) {
        this.stationService = stationService;
    }

    @MessageMapping("/station/snapshot")
    @SendTo("/topic/station")
    public RealtimeMessageDto snapshot() {
        return new RealtimeMessageDto("SNAPSHOT", LocalDateTime.now(), stationService.snapshot());
    }
}
