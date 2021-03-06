/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: UserSession.java
 * Purpose	: User Session class   
 * Author	: Sergey K
 * Created	: 10/08/2016
 */


package tv.liveu.tvbroadcast;

import java.io.Closeable;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.kurento.client.Continuation;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.client.OnIceCandidateEvent;
import org.kurento.client.RtpEndpoint;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.internal.server.KurentoServerException;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

import redsoft.dsagent.*;


public class UserSession implements Closeable {

	private static final Logger log = LoggerFactory.getLogger(UserSession.class);
	private final Ds ds = new Ds(getClass().getSimpleName());

	private String name;
        private String role;
        private String channel = null;
	private float geoX = 0;
	private float geoY = 0;
	private Random random = new Random();
	
	private final WebSocketSession session;

	private MediaPipeline pipeline; // room and selected video pipeline

	private Room room;
        private Room lastNewsRoom;
	private WebRtcEndpoint outgoingMedia;
	private WebRtcEndpoint outgoingLiveMedia;
	private WebRtcEndpoint nextVideoMedia; // media 4 preview (next video)
        private WebRtcEndpoint liveRoomMedia; // media 4 live room video
        private RtpEndpoint centralMedia = null; // media 2 Cental
	
	private final ConcurrentMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, WebRtcEndpoint> incomingLiveMedia = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, WebRtcEndpoint> incomingPlayMedia = new ConcurrentHashMap<>();
       
	private boolean isLiveState = false; // is user on live state
	
	/* constructor used to create UserSession without users name, room name, and media
	 * all this will be added later if needed
	 */
	public UserSession(final WebSocketSession session) {
		ds.print("UserSession object created: %s", session.getId());
		this.session = session;
	}
	

	/*
	 * set media pipeline, create EPs
	 */
	public void setMediaPipeline(MediaPipeline p) {
		ds.print("Set MediaPipeline: name: %s, pipeline: %s", name, p.getName());
		this.pipeline = p;

		createOutgoingMedia();
		createOutgoingLiveMedia();
		createNextVideoMedia();
                createLiveRoomMedia();
	}
	
	
	public MediaPipeline getMediaPipeline() {
		return this.pipeline;
	}

	
	/*
	 * create outgoing media, bind ice candidates handler
	 */
	private void createOutgoingMedia() {
		this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();

		this.outgoingMedia.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
			
			@Override
			public void onEvent(OnIceCandidateEvent event) {
				JsonObject response = new JsonObject();
				response.addProperty("id", "iceCandidate");
				response.addProperty("name", name);
				response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
				try {
					synchronized (session) {
						session.sendMessage(new TextMessage(response.toString()));
					}
				} catch (IOException e) {
					log.error("{}: {}", this, e.getMessage());
				}
			}
		});
	}

	
	
	/*
	 * create outgoing media 4 live, bind ice candidates handler
	 */
	public void createOutgoingLiveMedia() {
		this.outgoingLiveMedia = new WebRtcEndpoint.Builder(pipeline).build();

		this.outgoingLiveMedia.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
			
			@Override
			public void onEvent(OnIceCandidateEvent event) {
				JsonObject response = new JsonObject();
				response.addProperty("id", "iceCandidate4Live");
				response.addProperty("name", name);
				response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
				try {
					synchronized (session) {
						session.sendMessage(new TextMessage(response.toString()));
					}
				} catch (IOException e) {
					log.error("{}: {}", this, e.getMessage());
				}
			}
		});
	}


	/*
	 * create selected participant media, bind ice candidates handler
	 */
	private void createNextVideoMedia() {
		this.nextVideoMedia = new WebRtcEndpoint.Builder(pipeline).build();

		this.nextVideoMedia.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

			@Override
			public void onEvent(OnIceCandidateEvent event) {
				JsonObject response = new JsonObject();
				response.addProperty("id", "iceCandidate4NextVideo");
				response.addProperty("name", name);
				response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
				try {
					synchronized (session) {
						session.sendMessage(new TextMessage(response.toString()));
					}
				} catch (IOException e) {
					log.error("{}: {}", this, e.getMessage());
				}
			}
		});
	}


	/*
	 * create live room media, bind ice candidates handler
	 */
	private void createLiveRoomMedia() {
		this.liveRoomMedia = new WebRtcEndpoint.Builder(pipeline).build();

		this.liveRoomMedia.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

			@Override
			public void onEvent(OnIceCandidateEvent event) {
				JsonObject response = new JsonObject();
				response.addProperty("id", "iceCandidate4LiveRoomVideo");
				response.addProperty("name", name);
				response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
				try {
					synchronized (session) {
						session.sendMessage(new TextMessage(response.toString()));
					}
				} catch (IOException e) {
					log.error("{}: {}", this, e.getMessage());
				}
			}
		});
	}

	public WebRtcEndpoint getOutgoingWebRtcPeer() {
		return outgoingMedia;
	}
	
	public WebRtcEndpoint getOutgoingLiveWebRtcPeer() {
		return outgoingLiveMedia;
	}
	
	public WebRtcEndpoint getNextVideoWebRtcPeer() {
		return nextVideoMedia;
	}

	public WebRtcEndpoint getLiveRoomWebRtcPeer() {
		return liveRoomMedia;
	}

        public String getName() {
		return name;
	}

	public void setName(String n) {
		this.name = n;
	}
	
        public String getRole() {
            return role;
        }
        
        public void setRole(String r) {
            this.role = r;
        }
        
        public String getChannel() {
            return channel;
        }
        
        public void setChannel(String c) {
            this.channel = c;
        }
        
        public boolean isProducer() {
            return this.role.compareTo("producer") == 0;
        }
        
        public boolean isParticipant() {
            return this.role.compareTo("participant") == 0;
        }
        
	public float getGeoX() {
            if (this.isProducer()) return 0;
            return this.geoX;
	}
	
	public void setGeoX(float x) {
		if (Settings.TEST_GEOLOCATION) {
			if (this.geoX == 0) {
				this.geoX = random.nextFloat() * 50;
			}
			return;
		}
		this.geoX = x + (random.nextFloat() - 0.5F) * 0.0002F;
	}

	public float getGeoY() {
                if (this.isProducer()) return 0;
		return this.geoY;
	}
	
	public void setGeoY(float y) {
		if (Settings.TEST_GEOLOCATION) {
			if (this.geoY == 0) {
				this.geoY = random.nextFloat() * 20 + 30;
			}
			return;
		}
		this.geoY = y + (random.nextFloat() - 0.5F) * 0.0002F;;
	}

	public WebSocketSession getSession() {
		return session;
	}

	public Room getRoom() {
		return this.room;
	}

	public void setRoom(Room r) {
		this.room = r;
                if (null == r) return;
                if (r.getClass() == Room.class) {
                    lastNewsRoom = r;
                }
	}

	public boolean getLiveState() {
		return isLiveState;
	}
	
	public void setLiveState(boolean state) {
		isLiveState = state;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer("UO:");
		if (name != null) {
			sb.append(name);
		} else {
			sb.append("NULL");
		}
		return sb.toString();
	}
	
        
	/* handle video request on preview video */
	public void processSdp4NextVideo(String sdpOffer) throws IOException {
		log.info("{}: selected paticipant video SDP offer: {}", this, sdpOffer);
		//ds.print("User '%s' requesting selected participant video in room '%s'", this, room.getName());

		final String ipSdpAnswer = nextVideoMedia.processOffer(sdpOffer);
		final JsonObject m = new JsonObject();
		m.addProperty("id", "sdpAnswer4NextVideo");
		m.addProperty("sdpAnswer", ipSdpAnswer);

		log.info("{}: selected paticipant video SDP answer: {}", this, ipSdpAnswer);
		//ds.print("User '%s': SDP answer for sender selected paticipant video is: %s", this.name, ipSdpAnswer);
		sendMessage(m);
		nextVideoMedia.gatherCandidates();
	}

	
	/* handle video request on live room video */
	public void processSdp4LiveRoomVideo(String sdpOffer) throws IOException {
		log.info("{}: live room paticipant video SDP offer: {}", this, sdpOffer);

		final String ipSdpAnswer = liveRoomMedia.processOffer(sdpOffer);
		final JsonObject m = new JsonObject();
		m.addProperty("id", "sdpAnswer4LiveRoomVideo");
		m.addProperty("sdpAnswer", ipSdpAnswer);

		log.info("{}: live room paticipant video SDP answer: {}", this, ipSdpAnswer);
		//ds.print("User '%s': SDP answer for sender selected paticipant video is: %s", this.name, ipSdpAnswer);
		sendMessage(m);
		liveRoomMedia.gatherCandidates();
	}

	
	/* handle video request on live video */
	public void processSdp4Live(UserSession sender, String sdpOffer) throws IOException {
		log.info("{}: live video for {} SDP offer: {}", this, sender, sdpOffer);
		//ds.print("User '%s' requesting LIVE video in room '%s'", this, room.getName());
		
		WebRtcEndpoint ep = getLiveEndpointForUser(sender);
		
		String sdpAnswer = ep.processOffer(sdpOffer);

		log.info("{}: live video for {} SDP answer: {}", this, sender, sdpAnswer);
	
		if (ep == outgoingLiveMedia) { // don't connect myself ??? are we need this?
			log.info("{}: loopback LIVE VIDEO with {}", this, sender);
		} else {
			try {
				log.info("{}: LIVE VIDEO CONNECT {} -> {}", this, sender, this);
				sender.getOutgoingWebRtcPeer().connect(ep);	
			} catch (KurentoServerException ex) {
				log.error("{}: error on LIVE VIDEO CONNECT from {}: {}", this, sender, ex);
				return;
			}
		}
		
		
		final JsonObject m = new JsonObject();
		m.addProperty("id", "sdpAnswer4Live");
		m.addProperty("name", sender.getName());
		m.addProperty("sdpAnswer", sdpAnswer);
		sendMessage(m);
		ep.gatherCandidates();
	}

	
	/*
	 * some participant wants video from as
	 * */
	public void processSdp(UserSession sender, String sdpOffer, boolean audioOnly) throws IOException {
		log.info("{}: video for {} SDP offer: {}", this, sender, sdpOffer);
		ds.print("User '%s' connecting with '%s' in room '%s'", this.name, sender.getName(), room.getName());

		WebRtcEndpoint ep = getEndpointForUser(sender);
		final String sdpAnswer = ep.processOffer(sdpOffer);
		log.info("{}: video for {} SDP answer: {}", this, sender, sdpAnswer);
		
		if (ep == outgoingMedia) { // don't connect myself ??? are we need this?
			log.info("{}: loopback VIDEO with {}", this, sender);
		} else {
			try {
                                if (audioOnly) {
                                    log.info("{}: AUDIO CONNECT {} -> {}", this, sender, this);
                                    sender.getOutgoingWebRtcPeer().connect(ep, MediaType.AUDIO);
                                } else {
                                    log.info("{}: VIDEO CONNECT {} -> {}", this, sender, this);
                                    sender.getOutgoingWebRtcPeer().connect(ep);                                    
                                }
			} catch (KurentoServerException ex) {
				log.error("{}: error on AUDIO/VIDEO CONNECT from {}: {}", this, sender, ex);
				return;
			}
		}
		
		
		final JsonObject msg = new JsonObject();
		msg.addProperty("id", "receiveVideoAnswer");
		msg.addProperty("name", sender.getName());
		msg.addProperty("sdpAnswer", sdpAnswer);

		ds.print("User '%s': SDP answer for sender '%s' is: %s", this, sender, sdpAnswer);
		sendMessage(msg);
		// moved higher, returned back: getEndpointForUser(sender).gatherCandidates();
		ep.gatherCandidates();
	}

	
	/*
	 * get or create incoming endpoint 4 room participant 
	 */
	private WebRtcEndpoint getEndpointForUser(final UserSession sender) {
		if (sender.getName().equals(name)) {
			log.debug("{}: EP configuring loopback", this);
			return outgoingMedia;
		}

		log.info("{}: EP request for video from {}", this, sender);
		ds.print("User '%s': receiving video from: %s", this.name, sender.getName());

		WebRtcEndpoint incoming = incomingMedia.get(sender.getName());
		if (incoming == null) {
			log.info("{}: EP creating new for {}", this, sender);
			ds.print(2, "new WebRtcEndpoint");
			incoming = new WebRtcEndpoint.Builder(pipeline).build();

			incoming.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
				@Override
				public void onEvent(OnIceCandidateEvent event) {
					JsonObject response = new JsonObject();
					response.addProperty("id", "iceCandidate");
					response.addProperty("name", sender.getName());
					response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
					try {
						synchronized (session) {
							session.sendMessage(new TextMessage(response.toString()));
						}
					} catch (IOException e) {
						log.error("{}: {}", this, e.getMessage());
					}
				}
			});

			incomingMedia.put(sender.getName(), incoming);
		}

		log.info("{}: EP success for {}", this, sender);
//		sender.getOutgoingWebRtcPeer().connect(incoming);

		return incoming;
	}

	
	/*
	 * get or create incoming live endpoint 4 administrator and self 
	 */
	private WebRtcEndpoint getLiveEndpointForUser(final UserSession sender) {
		if (sender.getName().equals(name)) {
			log.info("{}: EP live configuring loopback", this);
			ds.warning("User '%s' configuring loopback for live", this);
			return outgoingLiveMedia;
		}

		log.info("{}: EP request live video for {}", this, sender);
		ds.print("User '%s': receiving video for live from: %s", this.name, sender.getName());

		WebRtcEndpoint incoming = incomingLiveMedia.get(sender.getName());
		if (incoming == null) {
			log.info("{}: EP creating new live for {}", this, sender);

			incoming = new WebRtcEndpoint.Builder(pipeline).build();

			incoming.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
				@Override
				public void onEvent(OnIceCandidateEvent event) {
					JsonObject response = new JsonObject();
					response.addProperty("id", "iceCandidate4Live");
					response.addProperty("name", sender.getName());
					response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
					try {
						synchronized (session) {
							session.sendMessage(new TextMessage(response.toString()));
						}
					} catch (IOException e) {
						log.error("{}: {}", this, e.getMessage());
					}
				}
			});

			incomingLiveMedia.put(sender.getName(), incoming);
		}

		log.info("{}: EP success live EP for {}", this, sender);
		return incoming;
	}

        
	/*
	 * get or create incoming play uri endpoint
	 */
    private WebRtcEndpoint getPlayUriEndpoint(final String playUriId) {
        log.info("{}: EP request play uri for {}", this, playUriId);

        WebRtcEndpoint incoming = incomingPlayMedia.get(playUriId);
        if (incoming == null) {
            log.info("{}: EP creating new play uri for {}", this, playUriId);

            incoming = new WebRtcEndpoint.Builder(pipeline).build();

            incoming.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
                @Override
                public void onEvent(OnIceCandidateEvent event) {
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "iceCandidate4PlayUri");
                    response.addProperty("name", playUriId);
                    response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                    try {
                        synchronized (session) {
                            session.sendMessage(new TextMessage(response.toString()));
                        }
                    } catch (IOException e) {
                        log.error("{}: {}", this, e.getMessage());
                    }
                }
            });

            incomingPlayMedia.put(playUriId, incoming);
        }

        log.info("{}: EP success play uri for {}", this, playUriId);
        return incoming;
    }
	
    
    /* handle SDP offer for play uri */
    public void processSdp4PlayUri(PlayerEndpoint pe, String sdpOffer) throws IOException {
        log.info("{}: play uri for {} SDP offer: {}", this, pe.getId(), sdpOffer);

        WebRtcEndpoint ep = getPlayUriEndpoint(pe.getId());
        String sdpAnswer = ep.processOffer(sdpOffer);

        log.info("{}: play uri for {} SDP answer: {}", this, pe.getId(), sdpAnswer);

        try {
            log.info("{}: PLAY URI CONNECT {}", this, pe.getId());
            pe.connect(ep);
        } catch (KurentoServerException ex) {
            log.error("{}: error on PLAY URI CONNECT {}: {}", this, pe.getId(), ex);
            return;
        }

        final JsonObject m = new JsonObject();
        m.addProperty("id", "sdpAnswer4PlayUri");
        m.addProperty("name", pe.getId());
        m.addProperty("sdpAnswer", sdpAnswer);
        sendMessage(m);
        ep.gatherCandidates();
    }


    /**
     * release endpoint for uri reception
     * @param playUriId id of PlayerEndpoint
     */
    public void cancelPlayUri(final String playUriId) {
        log.info("{}: canceling play uri reception from {}", this, playUriId);
        final WebRtcEndpoint incoming = incomingPlayMedia.remove(playUriId);

        log.info("{}: play uri EP removing for {}", this, playUriId);

        if (incoming == null) {
            log.error("{}: play uri incoming for {} is NULL!", this, playUriId);
            return;
        }

        incoming.release(new Continuation<Void>() {
            @Override
            public void onSuccess(Void result) throws Exception {
                log.info("{}: Released successfully play uri EP for {}", this, playUriId);
            }

            @Override
            public void onError(Throwable cause) throws Exception {
                log.error("{}: Could not release play uri EP for {}", this, playUriId);
            }
        });
    }
	

	public void cancelVideoFrom(final UserSession sender) {
		cancelVideoFrom(sender.getName());
	}

	
	public void cancelVideoFrom(final String senderName) {
		log.info("{}: canceling video reception from {}", this, senderName);
		final WebRtcEndpoint incoming = incomingMedia.remove(senderName);

		log.info("{}: EP removing for {}", this, senderName);
		
		if (incoming == null) {
			log.error("{}: incoming for {} is NULL!", this, senderName);
			ds.print("PARTICIPANT %s: incoming for %s is NULL!", this, senderName);
			return;
		}
		
		incoming.release(new Continuation<Void>() {
			@Override
			public void onSuccess(Void result) throws Exception {
				log.info("{}: Released successfully incoming EP for {}", this, senderName);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.error("{}: Could not release incoming EP for {}", this, senderName);
			}
		});
	}
	

	void suspendVideoFrom(UserSession sender) {
		log.info("{}: suspending with {} in room {}", this, sender, room);
		WebRtcEndpoint ep = getEndpointForUser(sender);
		sender.getOutgoingWebRtcPeer().disconnect(ep);
	}
	
	void resumeVideoFrom(UserSession sender) {
		log.info("{}: resuming with {} in room {}", this, sender, room);
		WebRtcEndpoint ep = getEndpointForUser(sender);
		sender.getOutgoingWebRtcPeer().disconnect(ep);		
	}
	
	@Override
	public void close() throws IOException {
		log.info("{}: releasing resources", this);
		releaseIncoming();
		releaseLiveEPs();
                releasePlayEPs();
                if (centralMedia != null) disconnectCentral();
		
		outgoingMedia.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.info("{}: released outgoing EP", this);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.error("{}: could not release outgoing EP", this);
			}
		});

		nextVideoMedia.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.info("{}: released next video EP", this);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.error("{}: could not release next video EP", this);
			}
		});

		liveRoomMedia.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.info("{}: released live room video EP", this);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.error("{}: could not release live room video EP", this);
			}
		});
	}

	
	/*
	 * release incoming EPs
	 */
	public void releaseIncoming() {	
		for (final String remoteParticipantName : incomingMedia.keySet()) {

			log.info("{}: released incoming EP for {}", this, remoteParticipantName);

			final WebRtcEndpoint ep = incomingMedia.get(remoteParticipantName);

			ep.release(new Continuation<Void>() {

				@Override
				public void onSuccess(Void result) throws Exception {
					log.info("{}: released successfully incoming EP for {}", this, remoteParticipantName);
				}

				@Override
				public void onError(Throwable cause) throws Exception {
					log.error("{}: could not release incoming EP for {}", this,
							remoteParticipantName);
				}
			});
		}

		incomingMedia.clear();
	}
	
	
	/*
	 * release incoming LIVE EPs
	 */
	public void releaseLiveEPs() {
		for (final String remoteLiveParticipantName : incomingLiveMedia.keySet()) {

			log.info("{}: released incoming EP for {}", this, remoteLiveParticipantName);

			final WebRtcEndpoint ep = incomingLiveMedia.get(remoteLiveParticipantName);

			ep.release(new Continuation<Void>() {

				@Override
				public void onSuccess(Void result) throws Exception {
					log.info("{}: released successfully incoming EP for {}", this, remoteLiveParticipantName);
				}

				@Override
				public void onError(Throwable cause) throws Exception {
					log.error("{}: could not release incoming EP for {}", this,
							remoteLiveParticipantName);
				}
			});
		}
		
		incomingLiveMedia.clear();		
		
		outgoingLiveMedia.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.info("{}: released outgoing LIVE EP", this);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.error("{}: could not release outgoing LIVE EP", this);
			}
		});
	}
        
        
    /*
     * release incoming play uri EPs
     */
    public void releasePlayEPs() {
        for (final String s : incomingPlayMedia.keySet()) {

            log.info("{}: released incoming play uri EP for {}", this, s);

            final WebRtcEndpoint ep = incomingPlayMedia.get(s);

            ep.release(new Continuation<Void>() {

                @Override
                public void onSuccess(Void result) throws Exception {
                    log.info("{}: released successfully incoming play uri EP for {}", this, s);
                }

                @Override
                public void onError(Throwable cause) throws Exception {
                    log.error("{}: could not release incoming play uri EP for {}", this, s);
                }
            });
        }
        
        incomingPlayMedia.clear();
    }
	
	
	/**
	 * send JSON message to user
	 * 
	 */
	public void sendMessage(JsonObject message) throws IOException {
		log.debug("{}: sending message {}", this, message);
		ds.print(1, "Send to   '%s': %s'", name, message.toString());
		synchronized (session) {
			session.sendMessage(new TextMessage(message.toString()));
		}
	}

	/**
	 * send text message to user
	 */
	public void sendMessage(String  message) throws IOException {
		log.debug("{}: sending message {}", this, message);
		ds.print(1, "Send to   '%s': '%s'", name, message);
		synchronized (session) {
			session.sendMessage(new TextMessage(message));
		}
		
	}
	
	/**
	 * send error message to user
	 * @param message error description
	 */
	public void sendError(String message) throws IOException {
		log.warn("{}: sending error message: {}", this, message);
		JsonObject m = new JsonObject();
		m.addProperty("id", "error");
		m.addProperty("message", message);
		sendMessage(m);
	}
	
	
	/*
	 * add ice candidate 4 participants
	 */
	public void addCandidate(IceCandidate candidate, String name) {
		if (this.name.compareTo(name) == 0) {
			outgoingMedia.addIceCandidate(candidate);
		} else {
			WebRtcEndpoint webRtc = incomingMedia.get(name);
			if (webRtc != null) {
				webRtc.addIceCandidate(candidate);
			} else {
				log.error("{}: adding ice candidate for {}", this, name);
			}
		}
	}

	
	/*
	 * add ice candidate 4 live
	 */
	public void addLiveCandidate(IceCandidate candidate, String name) {
		if (this.name.compareTo(name) == 0) {
			outgoingLiveMedia.addIceCandidate(candidate);
		} else {
			WebRtcEndpoint webRtc = incomingLiveMedia.get(name);
			if (webRtc != null) {
				webRtc.addIceCandidate(candidate);
			} else {
				log.error("{}: adding live ice candidate for {}", this, name);
			}
		}
	}


    /*
     * add ice candidate for play uri
     */
    public void addCandidate4PlayUri(IceCandidate candidate, String name) {
        WebRtcEndpoint webRtc = incomingPlayMedia.get(name);

        if (webRtc != null) {
            webRtc.addIceCandidate(candidate);
        } else {
            log.error("{}: adding play uri ice candidate for {}", this, name);
        }
    }


        /**
         * connect user media to Central
         * @throws IOException 
         */
	public void connectCentral() throws IOException {
		log.info("{}: connecting to CENTRAL", this);
		if (centralMedia != null) {
			log.error("{}: already connected to CENTRAL", this);
			return;
		}
		centralMedia = new RtpEndpoint.Builder(pipeline).build(); 
		centralMedia.setOutputBitrate (400000);

                String  offer = "v=0\r\n"
	    		+ "o=- 12345 12345 IN IP4 54.216.201.107\r\n"
	    		+ "s=-\r\n"
	    		+ "c=IN IP4 54.216.201.107\r\n"
	    		+ "t=0 0\r\n"
	    		+ "m=video 52126 RTP/AVP 96 97 98\r\n"
	    		+ "a=rtpmap:96 H264/90000\r\n"
	    		+ "a=recvonly\r\n"
	    		+ "b=AS:2000\r\n"
	    		+ "m=audio 52128  RTP/AVP  0\r\n"
	            + "a=rtpmap:0 PCMU/8000";
	
		String a = centralMedia.processOffer(offer);
		log.info("{}: CENTRAL MEDIA ANSWER: '{}'", this, a);

                this.getOutgoingWebRtcPeer().connect(centralMedia);
                
                /* send live video to last news room */
                if (lastNewsRoom != null) {
                    lastNewsRoom.setLiveRoomParticipant(this);
                }

                return;
	}
	

        /**
         * disconnect user media from Central
         * @throws IOException 
         */
	public void disconnectCentral() throws IOException {
		log.info("{}: disconnecting from CENTRAL", this);
		if (centralMedia == null) {
			log.error("{}: not connected to CENTRAL (null)", this);
			return;
		}
	    this.getOutgoingWebRtcPeer().disconnect(centralMedia);
	    centralMedia.release();
	    centralMedia = null;
            
            /* stop live video to last news room */
            if (lastNewsRoom != null) {
                lastNewsRoom.clearLiveRoomParticipant();
            }

	}
        
        
        public boolean isCentralConnected() {
            return centralMedia != null;
        }

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}
		if (obj == null || !(obj instanceof UserSession)) {
			return false;
		}
		UserSession other = (UserSession) obj;
		boolean eq = name.equals(other.name);
		eq &= room.getName().equals(other.room.getName());
		return eq;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		int result = 1;
		result = 31 * result + name.hashCode();
		result = 31 * result + room.getName().hashCode();
		return result;
	}
}
