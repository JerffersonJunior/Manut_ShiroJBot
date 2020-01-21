package com.kuuhaku.handlers.api.websocket;

import com.kuuhaku.utils.Helper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

	private SimpMessagingTemplate template;

	@Autowired
	WebSocketController(SimpMessagingTemplate template) {
		this.template = template;
	}

	@MessageMapping("/chat")
	public void onReceivedMessage(String message) {
		Helper.logger(this.getClass()).info("Mensagem recebida: " + message);
		this.template.convertAndSend("/topic/message", message);
	}
}
