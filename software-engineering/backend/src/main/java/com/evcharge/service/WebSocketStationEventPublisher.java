package com.evcharge.service;

import com.evcharge.dto.RealtimeMessageDto;
import com.evcharge.dto.SystemSnapshot;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class WebSocketStationEventPublisher implements StationEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketStationEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(String type, SystemSnapshot snapshot) {
        messagingTemplate.convertAndSend("/topic/station", new RealtimeMessageDto(type, LocalDateTime.now(), snapshot));
    }
}
