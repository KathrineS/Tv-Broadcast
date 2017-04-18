/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: CallHandler.java
 * Purpose	: Application's main Web Sockets handler    
 * Author	: Sergey K
 * Created	: 10/08/2016
 */


package tv.liveu.tvbroadcast;

import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.kurento.client.IceCandidate;
import org.kurento.client.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import redsoft.dsagent.*;


public class CallHandler extends TextWebSocketHandler 
{
	private static final Logger log = LoggerFactory.getLogger(CallHandler.class);
	private static final Gson gson = new GsonBuilder().create();

	private final Ds ds = new Ds(getClass().getSimpleName());

        /* user login activity */
        private final LimitedQueue<JsonObject> recentParticipants = new LimitedQueue<JsonObject>(5);
        
        /* producer login activity */
        private final LimitedQueue<JsonObject> recentProducers = new LimitedQueue<JsonObject>(5);
        
        @Autowired
	private RoomManager roomManager;

	@Autowired
	private UserRegistry registry;

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		try {
			_handleTextMessage(session, message);
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
	

	public void _handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		ds.funcs(2, "CallHandler::handleTextMessage");
		
		final JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
		UserSession user = registry.getBySession(session);

		if (user != null) {
			log.debug("{}: incoming message: {}", user, jsonMessage);
		} else {
			log.debug("Incoming message from new user: {}", jsonMessage);
			user = new UserSession(session);
			registry.register(user);
		}

		ds.print(1, "Recv from '%s': '%s'", user.getName(), jsonMessage.toString());

		switch (jsonMessage.get("id").getAsString()) {
		case "login":
			loginUser(jsonMessage, user);
			break;
		case "joinRoom":
			joinRoom(jsonMessage, user);
			break;
                case "joinProducerRoom":
                    joinProducerRoom(jsonMessage, user);
                    break;
		case "sdpOffer":
			final String senderName = jsonMessage.get("sender").getAsString();
			final UserSession sender = registry.getByName(senderName);
			final String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
                        if (sender.getName() == sender.getRoom().getOwner()) {
                            /* sender if owner of the room, AUDIO only */
                            user.processSdp(sender, sdpOffer, true);
                        } else {
                            user.processSdp(sender, sdpOffer, false);
                        }
			break;
		case "sdpOffer4NextVideo":
			final String sdpOfferPS = jsonMessage.get("sdpOffer").getAsString();
			user.processSdp4NextVideo(sdpOfferPS);
			break;
		case "sdpOffer4LiveRoomVideo":
			final String sdpOfferLRV = jsonMessage.get("sdpOffer").getAsString();
			user.processSdp4LiveRoomVideo(sdpOfferLRV);
			break;
		case "sdpOffer4Live":
			final String senderNameLive = jsonMessage.get("sender").getAsString();
			final UserSession senderLive = registry.getByName(senderNameLive);
			final String sdpOfferLive = jsonMessage.get("sdpOffer").getAsString();
			user.processSdp4Live(senderLive, sdpOfferLive);
			break;
                case "sdpOffer4PlayUri":
                    final String playUriId = jsonMessage.get("sender").getAsString();
                    final String sdpOfferPlay = jsonMessage.get("sdpOffer").getAsString();
                    ProducerRoom producerRoom = (ProducerRoom)user.getRoom();                    
                    user.processSdp4PlayUri(producerRoom.getPlayUri(playUriId), sdpOfferPlay);
                    break;
                case "addPlayUri":
                    addPlayUri(jsonMessage, user);
                    break;
                case "removePlayUri":
                    removePlayUri(jsonMessage, user);
                    break;
		case "connectCentral":
			connectCentral(jsonMessage, user);
			break;
		case "disconnectCentral":
			disconnectCentral(jsonMessage, user);
			break;
                case "publishCentral":
                        publishCentral(jsonMessage, user);
                        break;
                case "unpublishCentral":
                        unpublishCentral(jsonMessage, user);
                        break;                    
		case "selectParticipant":
			selectUser(jsonMessage, user);
			break;
		case "startLive":
			startLive(jsonMessage, user);
			break;
		case "stopLive":
			stopLive(jsonMessage, user);
			break;
		case "leaveRoom":
			leaveRoom(jsonMessage, user);
			break;
                case "leaveProducerRoom":
                    leaveProducerRoom(jsonMessage, user);
                    break;
                case "moveParticipant":
                    moveParticipant(jsonMessage, user);
                    break;
		case "onIceCandidate":
			JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

			if (user != null) {
				IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
						candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
				user.addCandidate(cand, jsonMessage.get("name").getAsString());
			}
			break;
		case "onIceCandidate4NextVideo":
			JsonObject candidate4NextVideo = jsonMessage.get("candidate").getAsJsonObject();

			if (user != null) {
				IceCandidate cand = new IceCandidate(candidate4NextVideo.get("candidate").getAsString(),
						candidate4NextVideo.get("sdpMid").getAsString(), candidate4NextVideo.get("sdpMLineIndex").getAsInt());
				user.getNextVideoWebRtcPeer().addIceCandidate(cand);
			}
			break;
		case "onIceCandidate4LiveRoomVideo":
			JsonObject candidate4LiveRoomVideo = jsonMessage.get("candidate").getAsJsonObject();

			if (user != null) {
				IceCandidate cand = new IceCandidate(candidate4LiveRoomVideo.get("candidate").getAsString(),
						candidate4LiveRoomVideo.get("sdpMid").getAsString(), candidate4LiveRoomVideo.get("sdpMLineIndex").getAsInt());
				user.getLiveRoomWebRtcPeer().addIceCandidate(cand);
			}
			break;
		case "onIceCandidate4Live":
				JsonObject candidate4Live = jsonMessage.get("candidate").getAsJsonObject();

				if (user != null) {
					IceCandidate cand = new IceCandidate(candidate4Live.get("candidate").getAsString(),
							candidate4Live.get("sdpMid").getAsString(), candidate4Live.get("sdpMLineIndex").getAsInt());
					user.addLiveCandidate(cand, jsonMessage.get("name").getAsString());
				}
				break;
		case "onIceCandidate4PlayUri":
				JsonObject candidate4PlayUri = jsonMessage.get("candidate").getAsJsonObject();

				if (user != null) {
					IceCandidate cand = new IceCandidate(candidate4PlayUri.get("candidate").getAsString(),
							candidate4PlayUri.get("sdpMid").getAsString(), candidate4PlayUri.get("sdpMLineIndex").getAsInt());
                                        user.addCandidate4PlayUri(cand, jsonMessage.get("name").getAsString());
				}
				break;
		case "listRooms":
			listRooms(jsonMessage, user);
			break;
                case "recentParticipants":
                    recentParticipants(jsonMessage, user);
                    break;
                case "recentProducers":
                    recentProducers(jsonMessage, user);
                    break;
                case "listProducerRooms":
                    listProducerRooms(jsonMessage, user);
                    break;
		case "addRoom":
			addRoom(jsonMessage, user);
			break;
                case "addProducerRoom":
                    addProducerRoom(jsonMessage, user);
                    break;
		case "removeRoom":
			removeRoom(jsonMessage, user);
			break;
		case "chat":
			chatRoom(jsonMessage, user);
			break;
		case "geo":
			setUserLocation(jsonMessage, user);
			break;
		default:
			break;
		}
		log.debug("Message handled: {}", jsonMessage);
		ds.funce(2, "CallHandler::handleTextMessage");
	}

	
	/**
	 * try to login user
	 * @param params
	 * @param user
	 * @throws IOException
	 */
	private void loginUser(JsonObject params, UserSession user) throws IOException {
		String login = params.get("login").getAsString();
                String role = params.get("role").getAsString();
                String channel = params.get("channel").getAsString();
		log.info("{}: trying to login as {} role {}", user, login, role);

		UserSession u = registry.getByName(login);
		
		if (u != null) {
			log.warn("{}: already logged!", u);
			ds.warning("User '%s' already logged", login);
			JsonObject msg = new JsonObject();
			msg.addProperty("id", "login");
			msg.addProperty("result", "FAIL");
			msg.addProperty("message", "user already logged");
			user.sendMessage(msg);
			return;
		}
		
		if (user.getName() != null) {
			/* already logged */
			JsonObject msg = new JsonObject();
			msg.addProperty("id", "login");
			msg.addProperty("login", user.getName());
			msg.addProperty("result", "FAIL");
			msg.addProperty("message", "already logged as " + user.getName());
			user.sendMessage(msg);			
			return;
		}

		/* success */
		user.setName(login);
                user.setRole(role);
                user.setChannel(channel);
		JsonObject msg = new JsonObject();
		msg.addProperty("id", "login");
		msg.addProperty("login", user.getName());
                msg.addProperty("role", user.getRole());
                msg.addProperty("channel", user.getChannel());
		msg.addProperty("result", "OK");
		msg.addProperty("message", "OK");
		user.sendMessage(msg);
		log.info("{}: LOGGED IN!", user);
                if (user.isProducer()) {
                    updateRecentProducers(user.getName());
                } else {
                    updateRecentParticipants(user.getName());
                }
		return;
	}
	
	
	/**
	 * join News room
	 */
	private void joinRoom(JsonObject params, UserSession user) throws IOException {
		final String roomName = params.get("room").getAsString();
		final String name = params.get("name").getAsString();
		log.info("{}: trying to join room {}", user, roomName);

		Room room = roomManager.getRoom(roomName);
		if (room == null) {
			// room no exits, this is error
			user.sendError("Room " + roomName + " not exists!");
			return;
		}
		/* !!! override user name by name from join room message */
		user.setName(name);
		
		/* set geo coord */
		//user.setGeoX(params.get("geox").getAsFloat());
		//user.setGeoY(params.get("geoy").getAsFloat());
		room.join(user);
						
		/* send update room message 2 all */
		sendUpdateRoom(room);
	}

	/**
	 * join Producer room
	 */
	private void joinProducerRoom(JsonObject params, UserSession user) throws IOException {
		final String roomName = params.get("room").getAsString();
		final String name = params.get("name").getAsString();
		log.info("{}: trying to join producer room {}", user, roomName);

                ProducerRoom room = roomManager.getProducerRoom(name, roomName);
		if (room == null) {
			// room no exits, this is error
			user.sendError("Producer room " + roomName + " not exists!");
			return;
		}
		/* !!! override user name by name from join room message */
		//user.setName(name);
		
		/* set geo coord */
		//user.setGeoX(params.get("geox").getAsFloat());
		//user.setGeoY(params.get("geoy").getAsFloat());
		room.join(user);
						
		/* send update room message */
		sendUpdateProducerRoom(room);
	}

	/**
	 * select user from next video
	 */
	private void selectUser(JsonObject params, UserSession user) throws IOException {
		Room r = user.getRoom();
		
		if (r == null) {
			log.error("{}: null room for next video", user);
			return;
		}
		
		final String name = params.get("name").getAsString();
		if (name == null) {
			log.error("{}: null name 4 next video", user);
			return;
		}
		
		UserSession selectedUser = r.getParticipant(name);
		if (selectedUser == null) {
			log.error("{}: no such user for next video from {}", user, name);
			return;			
		}
		
		if (!r.setSelectedParticipant(selectedUser)) {
			log.error("{}: could not select {} 4 next video", user, selectedUser);
			return;
		}

		JsonObject msg = new JsonObject();
		msg.addProperty("id", "selectedParticipant");
		msg.addProperty("name", r.getSelectedParticipant().getName());
		r.sendMessage(msg);
		
		return;
	}
	
	/**
	 * start LIVE at room
	 */
	private void startLive(JsonObject params, UserSession user) throws IOException {
		Room r = user.getRoom();
		
		if (r == null) {
			log.error("{}: null room for live video", user);
			return;
		}
		
		Room target = roomManager.getRoom("Administrator");
		if (target == null) {
			log.error("{}: null target room for live video", user);
			return;
		}
		
		r.startLive(target);
		return;
	}
	
	
	/*
	 * stop LIVE at room
	 */
	private void stopLive(JsonObject params, UserSession user) throws IOException {
		Room r = user.getRoom();
		
		if (r == null) {
			log.error("{}: null room for live video", user);
			return;
		}

		r.stopLive();
		return;
	}
	
	
        // OLD one, one dedicate Central video on room
	/*
	 * connect 2 central
	 */
	private void connectCentral(JsonObject params, UserSession user) throws IOException {
		Room r = user.getRoom();
		
		if (r == null) {
			log.error("{}: null room for central connnect", user);
			return;
		}
		
		if (r.getLiveParticipant() == null) {
			log.error("{}: null live participant in {} for central connect", user, r);
			return;
		}
		
		r.connectCentral();
	}

	
	/*
	 * disconnect from central
	 */
	private void disconnectCentral(JsonObject params, UserSession user) throws IOException {
		Room r = user.getRoom();
		
		if (r == null) {
			log.error("{}: null room for central connnect", user);
			return;
		}
		
		r.disconnectCentral();
	}

        
        /**
         * send one-way video-audio 2 to Central
         * @param params
         * @param user
         * @throws IOException 
         */
        private void publishCentral(JsonObject params, UserSession producer) throws IOException {
            final String name = params.get("name").getAsString();
            UserSession user = registry.getByName(name);
            if (user == null) {
                log.error("producer {}: no such user: {}", producer, name);
                return;
            }
            log.info("producer {}: sending {} to CENTRAL", producer, user);
            user.connectCentral();
            
            /* notify room participants */
            Room r = user.getRoom();
            r.sendMessage(params);
        }
        
        
        /**
         * stop sending 2 central
         * @param params
         * @param user
         * @throws IOException 
         */
        private void unpublishCentral(JsonObject params, UserSession producer) throws IOException {
            final String name = params.get("name").getAsString();
            UserSession user = registry.getByName(name);
            if (user == null) {
                log.error("producer {}: no such user: {}", producer, name);
                return;
            }
            log.info("producer {}: disconnecting {} from CENTRAL", producer, user);
            user.disconnectCentral();

            /* notify room participants */
            Room r = user.getRoom();
            r.sendMessage(params);
        }
	
        
	/**
	 * send updateRoom message 2 all
	 */
	private void sendUpdateRoom(Room room) {
		JsonObject msg = new JsonObject();
		msg.addProperty("id", "updateRoom");
		msg.addProperty("name", room.getName());
		msg.addProperty("descr", room.getDescr());
		msg.addProperty("color", room.getColorName());
                msg.addProperty("imageurl", room.getImageURL());
		msg.addProperty("nusers", room.getUsersCount());
                msg.addProperty("owner", room.getOwner());
                msg.addProperty("channel", room.getChannel());
			
		log.info("ALL: sending room update message: {}", msg.toString());

		sendMessage2ALL(msg);				
	}
	
	
	/**
	 * send updateRoom message 2 producer room participants
	 */
	private void sendUpdateProducerRoom(ProducerRoom room) {
		JsonObject msg = new JsonObject();
		msg.addProperty("id", "updateProducerRoom");
		msg.addProperty("name", room.getName());
		msg.addProperty("descr", room.getDescr());
		msg.addProperty("color", room.getColorName());
                msg.addProperty("imageurl", room.getImageURL());
		msg.addProperty("nusers", room.getUsersCount());
                msg.addProperty("owner", room.getOwner());
                msg.addProperty("channel", room.getChannel());
			
		log.info("sending producer room update message: {}", msg.toString());
                // send it to current room participants
                room.sendMessage(msg);
                
                // send it to producer if online
                UserSession owner = registry.getByName(room.getOwner());
                if (owner != null) {
                    log.info("sending producer room update message for producer {} : {}", owner, msg.toString());
                    try {
                        owner.sendMessage(msg);
                    } catch (IOException ex) {
                        log.error("could not send message to producer {}: {}", owner, ex.getMessage(), ex);
                    }
                }
    	}
	
	
	/**
	 * set user geo location, notify room participants 
	 */
	private void setUserLocation(JsonObject params, UserSession user) throws IOException {
		/* set geo coord */
		user.setGeoX(params.get("geox").getAsFloat());
		user.setGeoY(params.get("geoy").getAsFloat());
		
		JsonObject msg = new JsonObject();
		msg.addProperty("id", "geo");
		msg.addProperty("name", user.getName());
		msg.addProperty("geox", user.getGeoX());
		msg.addProperty("geoy", user.getGeoY());
		Room room = user.getRoom();

		if (room == null) return;

		room.sendMessage(msg);
	}
	
	
	/*
	 * user leave room
	 */
	private void leaveRoom(JsonObject params, UserSession user) throws IOException {
		Room room = user.getRoom();
		if (room != null) {
			room.leave(user);
			/* send update room message 2 all */
			sendUpdateRoom(room);
		}
	}
	
        
        /**
         * user leave producer room
         * @param params parameters
         * @param user user
         * @throws IOException 
         */
        private void leaveProducerRoom(JsonObject params, UserSession user) throws IOException {
            ProducerRoom room = (ProducerRoom)user.getRoom();
            if (room != null) {
                room.leave(user);
                sendUpdateProducerRoom(room);
            }
        }
        
        
        /**
         * move user to producer room
         * @param params
         * @param producer
         * @throws IOException 
         */
        private void moveParticipant(JsonObject params, UserSession producer) throws IOException {
            final String name = params.get("user").getAsString();            
            UserSession user = registry.getByName(name);
            if (user == null) {
                log.error("no user found {} for producer move", name);
                return;
            }

            try {
                user.sendMessage(params);
            } catch (IOException ex) {
                log.error("could not send move message to user {}: {}", user, ex.getMessage(), ex);
            }
        }
        
        
	/**
	 * room chat message
	 */
	private void chatRoom(JsonObject params, UserSession user) throws IOException {
		String uname = user.getName();
		String rname = null;
		Room room = user.getRoom();
		if (room != null) {
			rname = room.getName();
		}

		String message = params.get("message").getAsString();
		
		if (uname != null && room != null && message != null) {
		    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		    String strDate = sdf.format(new Date());
			
			JsonObject m = new JsonObject();
			m.addProperty("id", "chat");
			m.addProperty("date", strDate);
			m.addProperty("name", uname);
			m.addProperty("room", rname);
			m.addProperty("message", message);
			log.info("ROOM: {} user: {} chat message: {}", rname, uname, message);
			room.sendMessage(m);
			room.addChatMessage(m);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		UserSession user = registry.removeBySession(session);
		if (user == null)
			return;
		Room room = user.getRoom();
		if (room == null)
			return;
		room.leave(user);
	}


	private void sendMessage2ALL(JsonObject msg) {
		for (UserSession u : registry.getUserSessions()) {
			try {
				u.sendMessage(msg);
			} catch (final IOException e) {
				log.error("session with user {} could not be notified", u, e);
			}
		}
	}
	
	
	/*
	 * add empty room, notify registered participants
	 */
	private void addRoom(JsonObject params, UserSession user) throws IOException 
	{
		JsonElement je = params.get("name");
		if (null == je) {
			log.error("no 'room' parameter!");
			return;
		}

		String roomName = je.getAsString();
		ds.print("addRoom: %s", roomName);

		Room room = roomManager.getRoom(roomName);
		if (null != room) {
			/* already exist */
			log.warn("room {} already exist!", roomName);
			user.sendError("room already exist: " + roomName);
			return;
		}

		/* create room */
		String roomDescr = params.get("descr").getAsString();
		String roomColor = params.get("color").getAsString();
                String roomImageURL = params.get("imageurl").getAsString();
 
		boolean r = roomManager.addRoom(user.getName(), roomName, roomDescr, roomColor,
                        roomImageURL, user.getChannel());

		if (r) {
			/* notify users */
			room = roomManager.getRoom(roomName);
			JsonObject msg = new JsonObject();
			msg.addProperty("id", "addRoom");
			msg.addProperty("name", room.getName());
			msg.addProperty("descr", room.getDescr());
			msg.addProperty("color", room.getColorName());
                        msg.addProperty("imageurl", room.getImageURL());
			msg.addProperty("nusers", room.getUsersCount());
                        msg.addProperty("owner", room.getOwner());
                        msg.addProperty("channel", room.getChannel());
			sendMessage2ALL(msg);
			return;
		}
		
		user.sendError("cannot create room: " + roomName);
	}


	/*
	 * add empty producer room, notify producer
	 */
	private void addProducerRoom(JsonObject params, UserSession user) throws IOException 
	{
		JsonElement je = params.get("name");
		if (null == je) {
			log.error("no 'room' parameter!");
			return;
		}

		String roomName = je.getAsString();

                ProducerRoom room = roomManager.getProducerRoom(user.getName(), roomName);
		if (null != room) {
			/* already exist */
			log.warn("producer room {} already exist!", roomName);
			user.sendError("room already exist: " + roomName);
			return;
		}

		/* create room */
		String roomDescr = params.get("descr").getAsString();
		String roomColor = params.get("color").getAsString();
                String roomImageURL = params.get("imageurl").getAsString();
 
		boolean r = roomManager.addProducerRoom(user.getName(), roomName, roomDescr, roomColor,
                        roomImageURL, user.getChannel());

		if (r) {
			/* notify producer */
                        room = roomManager.getProducerRoom(user.getName(), roomName);
			JsonObject msg = new JsonObject();
			msg.addProperty("id", "addProducerRoom");
			msg.addProperty("name", room.getName());
			msg.addProperty("descr", room.getDescr());
			msg.addProperty("color", room.getColorName());
                        msg.addProperty("imageurl", room.getImageURL());
			msg.addProperty("nusers", room.getUsersCount());
                        msg.addProperty("owner", room.getOwner());
                        msg.addProperty("channel", room.getChannel());
                        user.sendMessage(msg);
			return;
		}
		
		user.sendError("cannot create producer room: " + roomName);
	}


	/**
	 * remove room, close room participants, notify other registered
	 * participants
	 */
	private void removeRoom(JsonObject params, UserSession user) throws IOException 
	{
		JsonElement je = params.get("name");
		if (null == je) {
			log.error("no 'room' parameter!");
			return;
		}

		String roomName = je.getAsString();
		ds.print("removeRoom: %s", roomName);

		Room room = roomManager.getRoom(roomName);
		if (null == room) {
			ds.print("room {} not exist!", roomName);
			log.error("room {} not exist!", roomName);
			user.sendError("room " + roomName + " dont exist");
			return;
		}

		roomManager.removeRoom(room);

		/* notify users */
		JsonObject msg = new JsonObject();
		msg.addProperty("id", "removeRoom");
		msg.addProperty("name", roomName);
		
		sendMessage2ALL(msg);
	}

	
	/*
	 * list rooms 4 given participant
	 */
	private void listRooms(JsonObject params, UserSession user) throws IOException {
		log.info("listRooms request");

		try {
			final JsonObject msg = new JsonObject();

			final JsonArray roomArray = new JsonArray();

			for (final Room room : roomManager.getRooms()) {
				JsonObject r = new JsonObject();
				r.addProperty("name", room.getName());
				r.addProperty("descr", room.getDescr());
				r.addProperty("color", room.getColorName());
                                r.addProperty("imageurl", room.getImageURL());
				r.addProperty("nusers", room.getUsersCount());
                                r.addProperty("owner", room.getOwner());
				roomArray.add(r);
			}

			msg.addProperty("id", "listRooms");
			msg.add("data", roomArray);

			user.sendMessage(msg);

		} catch (Exception ex) {
			ds.error("Exception while sending list: " + ex.getMessage());
			ex.printStackTrace(System.err);
			user.sendError("exception while sending list");
		}
	}

	/*
	 * list producer rooms
	 */
	private void listProducerRooms(JsonObject params, UserSession user) throws IOException {
		log.info("listProducerRooms request for user {}", user.getName());

		try {
			final JsonObject msg = new JsonObject();

			final JsonArray roomArray = new JsonArray();

			for (final Room room : roomManager.getProducerRooms(user.getName())) {
				JsonObject r = new JsonObject();
				r.addProperty("name", room.getName());
				r.addProperty("descr", room.getDescr());
				r.addProperty("color", room.getColorName());
				r.addProperty("nusers", room.getUsersCount());
                                r.addProperty("owner", room.getOwner());
				roomArray.add(r);
			}

			msg.addProperty("id", "listProducerRooms");
			msg.add("data", roomArray);

			user.sendMessage(msg);

		} catch (IOException ex) {
			ex.printStackTrace(System.err);
			user.sendError("exception while sending producer room list");
		}

	}

    /**
     * add play uri to producer room
     */
    private void addPlayUri(JsonObject params, UserSession user) {
        ProducerRoom room = (ProducerRoom) user.getRoom();
        String uri = params.get("uri").getAsString();
        String title = params.get("title").getAsString();
        room.addPlayUri(uri, title);
    }

    /**
     * remove play uri
     */
    private void removePlayUri(JsonObject params, UserSession user) throws IOException {
        ProducerRoom room = (ProducerRoom) user.getRoom();
        String playUriId = params.get("name").getAsString();
        room.removePlayUri(playUriId);
    }
    
    /**
     * update recent participants
     */
    private void updateRecentParticipants(String userName) throws IOException {
	log.info("updateRecentParticipants with user {}", userName);

        // find entry with such user name, delete if exist
        for (int i = 0; i < recentParticipants.size(); i++) {
            JsonObject json = recentParticipants.get(i);
            if (json.get("name").getAsString().equals(userName)) {
                recentParticipants.remove(i);
                break;
            }
        }
        
        JsonObject json = new JsonObject();
        json.addProperty("name", userName);
        json.addProperty("dt", (new Date()).toString());
        recentParticipants.add(json);
        sendMessage2ALL(recentParticipants2Json());
    }
    
    /**
     * prepare recent participants message
     */
    private JsonObject recentParticipants2Json() throws IOException {
        final JsonObject msg = new JsonObject();
        final JsonArray array = new JsonArray();

        for (int i = recentParticipants.size() - 1; i >= 0; --i) {
            array.add(recentParticipants.get(i));
        }

        msg.addProperty("id", "recentParticipants");
        msg.add("data", array);

        return msg;
    }


    /*
     * send recent participants list 2 user
    */
    private void recentParticipants(JsonObject params, UserSession user) throws IOException {
        log.info("Sending recent participants list to {}", user);
        user.sendMessage(recentParticipants2Json());
    }
    
    /**
     * update recent producers
     */
    private void updateRecentProducers(String userName) throws IOException {
	log.info("updateRecentProducers with user {}", userName);

        // find entry with such user name, delete if exist
        for (int i = 0; i < recentProducers.size(); i++) {
            JsonObject json = recentProducers.get(i);
            if (json.get("name").getAsString().equals(userName)) {
                recentProducers.remove(i);
                break;
            }
        }
        
        JsonObject json = new JsonObject();
        json.addProperty("name", userName);
        json.addProperty("dt", (new Date()).toString());
        recentProducers.add(json);
        sendMessage2ALL(recentProducers2Json());
    }
    
    /**
     * prepare recent producers message
     */
    private JsonObject recentProducers2Json() throws IOException {
        final JsonObject msg = new JsonObject();
        final JsonArray array = new JsonArray();

        for (int i = recentProducers.size() - 1; i >= 0; --i) {
            array.add(recentProducers.get(i));
        }

        msg.addProperty("id", "recentProducers");
        msg.add("data", array);

        return msg;
    }


    /*
     * send recent producers list 2 user
    */
    private void recentProducers(JsonObject params, UserSession user) throws IOException {
        log.info("Sending recent producers list to {}", user);
        user.sendMessage(recentProducers2Json());
    }
    
}
