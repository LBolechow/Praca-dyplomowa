package pl.lukbol.dyplom.controllers;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.lukbol.dyplom.classes.Conversation;
import pl.lukbol.dyplom.classes.Message;
import pl.lukbol.dyplom.classes.User;
import pl.lukbol.dyplom.repositories.ConversationRepository;
import pl.lukbol.dyplom.repositories.MessageRepository;
import pl.lukbol.dyplom.repositories.RoleRepository;
import pl.lukbol.dyplom.repositories.UserRepository;
import pl.lukbol.dyplom.services.MessageService;
import pl.lukbol.dyplom.services.UserService;

import java.util.List;
import java.util.ArrayList;

@RestController
public class ChatController {

    @Autowired
    private MessageService messageService;

    private UserRepository userRepository;
    @Autowired
    private UserController userController;

    private  MessageRepository messageRepository;

    private ConversationRepository conversationRepository;

    public ChatController(UserRepository userRepository, ConversationRepository conversationRepository, MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }
    @MessageMapping("/sendToConversation/{conversationId}")
    @SendTo("/topic/employees")
    @Transactional
    public Message sendMessageToClient(@DestinationVariable Long conversationId, Message message) {
        // Uzyskaj dostęp do konwersacji na podstawie ID
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation != null) {
            // Użyj Twojej usługi do wysłania wiadomości do klienta
            messageService.sendMessage(message.getSender(), conversation, message.getContent(), message.getMessageDate());

            return message;
        } else {
            // Dodaj loga, aby sprawdzić, czy konwersacja nie istnieje
            System.out.println("Konwersacja o ID " + conversationId + " nie istnieje.");
            return null;
        }
    }
    @MessageMapping("/sendToEmployees")
    @SendTo("/topic/employees")
    @Transactional
    public Message sendMessageToEmployees(Message message) {
       String clientEmail = message.getSender().getEmail();

       User client = userRepository.findByEmail(clientEmail);

        // Get all conversations associated with the client
        List<Conversation> conversations = conversationRepository.findConversationByClient_Id(client.getId());

        // If the client doesn't have any conversations, create a new one
        if (conversations == null || conversations.isEmpty()) {
            conversations = new ArrayList<>(); // Initialize the conversations list

            // Create a new conversation
            Conversation conversation = new Conversation();
            conversation.setClient(client);
            conversation.setName(client.getName());
            // Set any other conversation properties as needed
            // Save the conversation to associate it with the client
            conversation = conversationRepository.save(conversation);
            conversations.add(conversation); // Add the conversation to the list of client's conversations
            client.setConversations(conversations); // Update the user's conversations
            userRepository.save(client); // Save the updated user
        }

        // Use your MessageService to send the message
        // You can loop through conversations and send the message to each one
        for (Conversation conversation : conversations) {
            messageService.sendMessage(message.getSender(), conversation, message.getContent(), message.getMessageDate());
        }
        return message;
    }
    @GetMapping("/api/conversation")
    public ResponseEntity<List<Message>> getClientConversation(Authentication authentication) {
        // Get the authenticated user's details
        User user = userRepository.findByEmail(userController.checkmail(authentication.getPrincipal()));

        List<Conversation> conversations = conversationRepository.findConversationByClient_Id(user.getId());
        if (!conversations.isEmpty()) {
            List<Message> messages = messageRepository.findByConversation(conversations.get(0));
            if (!messages.isEmpty()) {
                return ResponseEntity.ok(messages);
            } else {
                return ResponseEntity.notFound().build();
            }
        }
        else
        {
            return ResponseEntity.notFound().build();
        }
    }
    @GetMapping("/api/get_conversations")
    public List<Conversation> getAllConversations() {
        // Pobierz wszystkie konwersacje z bazy danych
        List<Conversation> conversations = conversationRepository.findAll();

        // Możesz przeprowadzić dodatkowe operacje na danych konwersacji, jeśli to konieczne

        return conversations;
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<Message>> getMessagesForConversation(@PathVariable Long conversationId) {
        // Pobierz konwersację na podstawie ID
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);

        if (conversation != null) {
            List<Message> messages = messageRepository.findByConversation(conversation);
            return ResponseEntity.ok(messages);
        } else {
            return ResponseEntity.notFound().build();
        }
    }


}