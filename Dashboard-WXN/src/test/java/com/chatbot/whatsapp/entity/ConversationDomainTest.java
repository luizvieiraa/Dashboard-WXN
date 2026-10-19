package com.chatbot.whatsapp.entity;

import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.exception.InvalidConversationStateException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConversationDomainTest {
    @Test
    void assumeAndCloseMaintainAttendanceAndConversationTogether() {
        Conversation conversation = Conversation.builder().status(ConversationStatus.WAITING_HUMAN).build();
        Attendant attendant = new Attendant(" Carlos ");
        conversation.assume(attendant);

        assertThat(conversation.getAttendance().getConversation()).isSameAs(conversation);
        assertThat(conversation.getAttendance().getAttendant()).isSameAs(attendant);
        assertThat(conversation.getAssignedTo()).isEqualTo("Carlos");
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.HUMAN_ACTIVE);
        conversation.close();
        assertThat(conversation.getAttendance().getClosedAt()).isEqualTo(conversation.getClosedAt());
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.CLOSED);
        assertThatThrownBy(() -> conversation.recordInteraction("REOPEN"))
                .isInstanceOf(InvalidConversationStateException.class);
        assertThatThrownBy(conversation::close).isInstanceOf(InvalidConversationStateException.class);
    }

    @Test
    void cannotAssumeOutsideQueueOrBypassHumanAssignment() {
        Conversation conversation = Conversation.builder().status(ConversationStatus.BOT_ACTIVE).build();
        assertThatThrownBy(() -> conversation.assume(new Attendant("Carlos")))
                .isInstanceOf(InvalidConversationStateException.class);
        assertThatThrownBy(() -> conversation.changeAutomatedStatus(ConversationStatus.HUMAN_ACTIVE))
                .isInstanceOf(InvalidConversationStateException.class);
        conversation.changeAutomatedStatus(ConversationStatus.WAITING_HUMAN);
        assertThatThrownBy(() -> conversation.closeForInactivity(Instant.now()))
                .isInstanceOf(InvalidConversationStateException.class);
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.WAITING_HUMAN);
    }

    @Test
    void attendantRequiresNonBlankName() {
        assertThatThrownBy(() -> new Attendant(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Attendant(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
