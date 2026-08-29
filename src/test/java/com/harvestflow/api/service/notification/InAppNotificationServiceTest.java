package com.harvestflow.api.service.notification;

import com.harvestflow.api.domain.entity.Notification;
import com.harvestflow.api.domain.enums.NotificationCategory;
import com.harvestflow.api.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InAppNotificationServiceTest {

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void notifyPersistsAReadableNotification() {
        UUID relatedId = UUID.randomUUID();

        notificationService.notify("plot.tester", NotificationCategory.IMAGE_VERIFICATION,
                "Photo flagged for review", "Your uploaded photo needs manual review.", "PRODUCE_IMAGE", relatedId);

        List<Notification> mine = notificationRepository.findByRecipientUsernameOrderByCreatedAtDesc("plot.tester");
        assertThat(mine).hasSize(1);
        Notification saved = mine.get(0);
        assertThat(saved.getCategory()).isEqualTo(NotificationCategory.IMAGE_VERIFICATION);
        assertThat(saved.getRelatedEntityId()).isEqualTo(relatedId);
        assertThat(saved.getReadAt()).isNull();
    }

    @Test
    void unreadCountOnlyCountsThatRecipientsUnreadNotifications() {
        notificationService.notify("alice", NotificationCategory.GENERAL, "Hi", "msg", null, null);
        notificationService.notify("alice", NotificationCategory.GENERAL, "Hi again", "msg2", null, null);
        notificationService.notify("bob", NotificationCategory.GENERAL, "Hi bob", "msg3", null, null);

        assertThat(notificationRepository.countByRecipientUsernameAndReadAtIsNull("alice")).isEqualTo(2);
        assertThat(notificationRepository.countByRecipientUsernameAndReadAtIsNull("bob")).isEqualTo(1);
    }
}
