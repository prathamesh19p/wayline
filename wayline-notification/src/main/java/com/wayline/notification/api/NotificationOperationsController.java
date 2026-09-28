package com.wayline.notification.api;

import com.wayline.notification.application.NotificationService;
import com.wayline.notification.domain.NotificationDelivery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class NotificationOperationsController {
    private final NotificationService notificationService;

    @GetMapping("/dead")
    public ResponseEntity<List<NotificationDelivery>> getDeadDeliveries() {
        return ResponseEntity.ok(notificationService.getDeadDeliveries());
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<NotificationDelivery> replay(@PathVariable Long id) {
        return ResponseEntity.accepted().body(notificationService.replayDeadDelivery(id));
    }
}
