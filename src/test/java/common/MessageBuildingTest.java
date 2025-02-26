package common;

import org.junit.jupiter.api.Test;
import org.swetophor.chatwork.common.Message;

import static org.swetophor.chatwork.common.MessageType.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import static org.swetophor.chatwork.common.Message.fromClientInput;

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
        String inputText = "@%s %s".formatted(GENERAL_RECEIVER, GENERAL_MESSAGE);
        Message aMessage = fromClientInput(inputText, GENERAL_SENDER);
        assertThat(aMessage.getType(), equalTo(PRIVATE_MSG));
        assertThat(aMessage.getSender(), equalTo(GENERAL_SENDER));
        assertThat(aMessage.getAddressee(), equalTo(GENERAL_RECEIVER));
        assertThat(aMessage.getMessage(), equalTo(GENERAL_MESSAGE));
    }


    @Test
    void personal_message_fromServer() {
        Message aServerMessage = Message.fromServer(GENERAL_MESSAGE, GENERAL_RECEIVER);
        assertThat(aServerMessage.getType(), equalTo(SERVER_MSG));
        assertThat(aServerMessage.getSender(), isEmptyOrNullString());
        assertThat(aServerMessage.getAddressee(), equalTo(GENERAL_RECEIVER));
        assertThat(aServerMessage.getMessage(), equalTo(GENERAL_MESSAGE));
    }

    @Test
    void global_message_fromServer() {
        Message aServerMessage = Message.fromServer(GENERAL_MESSAGE);
        assertThat(aServerMessage.getType(), equalTo(SERVER_MSG));
        assertThat(aServerMessage.getSender(), isEmptyOrNullString());
        assertThat(aServerMessage.getAddressee(), isEmptyOrNullString());
        assertThat(aServerMessage.getMessage(), equalTo(GENERAL_MESSAGE));
    }

    @Test
    void personalized_message_fromServer() {
        Message aServerMessage = Message.fromServer(GENERAL_MESSAGE);
        aServerMessage = aServerMessage.setAddressee(GENERAL_RECEIVER);
        assertThat(aServerMessage.getType(), equalTo(SERVER_MSG));
        assertThat(aServerMessage.getSender(), isEmptyOrNullString());
        assertThat(aServerMessage.getAddressee(), equalTo(GENERAL_RECEIVER));
        assertThat(aServerMessage.getMessage(), equalTo(GENERAL_MESSAGE));
    }
    


}