package org.kurento.tutorial.one2manycall;

import java.io.IOException;

import org.kurento.client.EndOfStreamEvent;
import org.kurento.client.EventListener;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class PlayHandler{

	private static final Logger log = LoggerFactory
			.getLogger(CallHandler.class);
	private static final Gson gson = new GsonBuilder().create();


	@Autowired
	private KurentoClient kurento;

	private MediaPipeline pipeline;

	private PlayerEndpoint player;
	private WebRtcEndpoint webRtc;

	public PlayHandler(KurentoClient kurento) {
		this.kurento = kurento;
		// Media pipeline
		pipeline = kurento.createMediaPipeline();
		player = new PlayerEndpoint.Builder(pipeline, "file:///tmp/webrtc.webm").build();
	}

	public void handleTextMessage(final WebSocketSession session, TextMessage message){

		JsonObject jsonMessage = gson.fromJson(message.getPayload(),
				JsonObject.class);
		log.debug("Incoming message from session '{}': {}", session.getId(),
				jsonMessage);

		switch (jsonMessage.get("id").getAsString()) {
		case "play":
			webRtc = new WebRtcEndpoint.Builder(pipeline).build();
			player.connect(webRtc);

			player.addEndOfStreamListener(new EventListener<EndOfStreamEvent>() {

				@Override
				public void onEvent(EndOfStreamEvent event) {
					// TODO Auto-generated method stub
					sendPlayEnd(session);
				}
			});
			
			
			String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
			String sdpAnswer = webRtc.processOffer(sdpOffer);
			JsonObject response = new JsonObject();
			response.addProperty("id", "playResponse");
			response.addProperty("response", "accepted");
			response.addProperty("sdpAnswer", sdpAnswer);
			try {
				session.sendMessage(new TextMessage(response.toString()));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			player.play();
			break;
		}

	}


	public void sendPlayEnd(WebSocketSession session) {
		try {
			JsonObject response = new JsonObject();
			response.addProperty("id", "playEnd");
			session.sendMessage(new TextMessage(response.toString()));
		} catch (IOException e) {
			log.error("Error sending playEndOfStream message", e);
		}
	}

	public String generateSdpAnswer(String sdpOffer) {
		return webRtc.processOffer(sdpOffer);
	}
}
