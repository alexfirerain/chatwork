package common;

import org.junit.jupiter.api.Test;

import static common.MessageType.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import static common.Message.fromClientInput;

class MessageBuildingTest {
    private static final String GENERAL_SENDER = "отправитель";
    private static final String GENERAL_RECEIVER = "получатель";
    private static final String GENERAL_MESSAGE = "Текст сообщения";


    @Test
    void reg_message_forms() {
        Message regMessage = fromClientInput("/reg " + GENERAL_SENDER, "");
        assertThat(regMessage.getType(), equalTo(REG_REQUEST));
        assertThat(regMessage.getSender(), equalTo(GENERAL_SENDER));
        assertThat(regMessage.getAddressee(), isEmptyOrNullString());
        assertThat(regMessage.getMessage(), isEmptyOrNullString());

    }

    @Test
    void regular_txt_message_forms() {
        Message aMessage = fromClientInput(GENERAL_MESSAGE, GENERAL_SENDER);
        assertThat(aMessage.getType(), equalTo(TXT_MSG));
        assertThat(aMessage.getSender(), equalTo(GENERAL_SENDER));
        assertThat(aMessage.getAddressee(), isEmptyOrNullString());
        assertThat(aMessage.getMessage(), equalTo(GENERAL_MESSAGE));
    }

    @Test
    void private_message_forms() {
        Message aMessage = fromClientInput("@" + GENERAL_RECEIVER + " " + GENERAL_MESSAGE, GENERAL_SENDER);
        assertThat(aMessage.getType(), equalTo(PRIVATE_MSG));
        assertThat(aMessage.getSender(), equalTo(GENERAL_SENDER));
        assertThat(aMessage.getAddressee(), equalTo(GENERAL_RECEIVER));
        assertThat(aMessage.getMessage(), equalTo(GENERAL_MESSAGE));
    }


    @Test
    void fromServer() {
    }
}