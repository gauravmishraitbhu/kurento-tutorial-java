/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
package org.kurento.tutorial.one2manycall;

import java.io.IOException;
import java.util.Map;

import org.kurento.client.MediaPipeline;
import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Protocol handler for 1 to N video call communication.
 * 
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.0.0
 */
public class CallHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory
			.getLogger(CallHandler.class);
	private static final Gson gson = new GsonBuilder().create();

	
	private Map<String , BroadcastRoom> activeRooms = Maps.newHashMap();
	
	@Autowired
	private KurentoClient kurento;


	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message)
			throws Exception {
		JsonObject jsonMessage = gson.fromJson(message.getPayload(),
				JsonObject.class);
		log.debug("Incoming message from session '{}': {}", session.getId(),
				jsonMessage);
		
		
		String roomName = jsonMessage.getAsJsonPrimitive("room").getAsString();
		if(roomName == null || roomName ==""){
			
			log.error("****check room name in incoming message got null.");
		}
		
		if(!activeRooms.containsKey(roomName)){
			//create a new pipeline.
			//create a room object
			MediaPipeline roomPipeline = kurento.createMediaPipeline();
			log.debug("Creating a new room object name=="+roomName);
			BroadcastRoom roomObject = new BroadcastRoom(roomName , roomPipeline);
			activeRooms.put(roomName, roomObject);
			
		}
		
		switch (jsonMessage.get("id").getAsString()) {
		case "master":
				master(session, jsonMessage);
			
			break;
		case "play":
			PlayHandler player = new PlayHandler(kurento);
			player.handleTextMessage(session, message);
			break;
		case "viewer":
				viewer(session, jsonMessage);
			break;
		case "stop":
			stop(session ,roomName);
			break;
		default:
			break;
		}
	}

	private synchronized void master(WebSocketSession session,
			JsonObject jsonMessage) throws IOException {
		
		String roomName = jsonMessage.getAsJsonPrimitive("room").getAsString();
		BroadcastRoom roomObject = activeRooms.get(roomName);
		
		roomObject.addBroadcaster(session, jsonMessage);
	}

	private synchronized void viewer(WebSocketSession session,
			JsonObject jsonMessage) throws IOException {
		String roomName = jsonMessage.getAsJsonPrimitive("room").getAsString();
		BroadcastRoom roomObject = activeRooms.get(roomName);
		
		roomObject.addViewer(session, jsonMessage);
	}

	private synchronized void stop(WebSocketSession session , String roomName ) throws IOException {
		
		if(roomName != null){
			activeRooms.get(roomName).stopClient(session);
		}else{
			//try in all rooms
			for (BroadcastRoom room : activeRooms.values()){
				room.stopClient(session);
			}
		}
		
		
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session,
			CloseStatus status) throws Exception {
		stop(session , null);
	}

}
