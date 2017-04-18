/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: RoomManager.java
 * Purpose	: Room Manager class  
 * Author	: Sergey K
 * Created	: 10/08/2016
 */

package tv.liveu.tvbroadcast;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import redsoft.dsagent.*;


public class RoomManager {

	private final Logger log = LoggerFactory.getLogger(RoomManager.class);

	private final Ds ds = new Ds(getClass().getSimpleName());

//	@Autowired
	private KurentoClient kurento = KurentoClient.create();
	private MediaPipeline pipeline;

        /* news rooms */
	private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();
        
        /* private producers rooms */
        private final ConcurrentMap<String, ConcurrentMap<String, ProducerRoom>> producerRooms = new ConcurrentHashMap<>();
        
	//private TvbConnector tvbStudio;

	public RoomManager() throws Exception {

		try {
			pipeline = kurento.createMediaPipeline();
			
			if (Settings.TEST_ROOMS) {
				/* create test rooms */
				log.info("creating test Rooms");
				addRoom("TestSystem", "Sport", "Cycling, rowing", "Green", "img/demo-cam-img-3.jpg", "NewsChannel");
				addRoom("TestSystem", "Nature", "Bears, horses, cats", "DarkSlateGray", "img/demo-cam-img-1.jpg", "NewsChannel");
//				addRoom("TestSystem", "Tourism", "Traveling, journey, hiking", "DarkGoldenRod", "img/demo-cam-img-2.jpg", "NewsChannel");
//				addRoom("TestSystem", "Cinema", "Films", "DarkBlue", "img/demo-cam-img-1.jpg", "NewsChannel");
			}
		} catch (Exception e) {
			log.error("error creating RoomManager: {}", e);
			throw e;
		}
		ds.print(0, "RoomManager object created");
	}

	/**
	 * find room and return room
	 * 
	 * @return Room or null
	 */
	public Room getRoom(String roomName) {
		return this.rooms.get(roomName);
	}
	
	/**
	 * Create new room
         * @param roomOwner the room owner name
	 * @param roomName the name of the room
	 * @param roomDescr description 4 room
	 * @param roomColor room color used in browser
         * @param roomImageURL room image URL
         * @param roomChannel room channel
	 * @return true if room created, false if already exists
	 */
	public boolean addRoom(String roomOwner, String roomName, String roomDescr, String roomColor,
                String roomImageURL, String roomChannel) {
		if (this.rooms.containsKey(roomName)) {
			log.warn("Room {} already exists", roomName);
			return false;
		}
		log.info("Adding room {} descr {} color {} image {} owner {}", roomName, roomDescr, roomColor, roomImageURL, roomChannel);
		rooms.put(roomName, new Room(roomOwner, roomName, roomDescr, roomColor, roomImageURL, roomChannel, pipeline));
		return true;
	}

	/**
	 * Removes a room from the list of available rooms.
	 *
	 * @param room
	 *            the room to be removed
	 */
	public void removeRoom(Room room) {
		this.rooms.remove(room.getName());
		room.close();
		log.info("{}: removed and closed", room);
	}
        
	/**
	 * get all rooms
         * @return Collection or rooms
	 */
	public Collection<Room> getRooms() {
		return rooms.values();
	}

        /**
         * Add producer room
         * @param owner owner of the room
         * @param roomName room name
         * @param roomDescr room description
         * @param roomColor room display color
         * @param roomImageURL room image URL
         * @param roomChannel room channel
         * @return false if room already exist or error, true if added
         */
        public boolean addProducerRoom(String owner, String roomName, String roomDescr, String roomColor,
                String roomImageURL, String roomChannel) {
            ConcurrentMap<String, ProducerRoom> ownerRooms;

            if (this.producerRooms.containsKey(owner)) {
                ownerRooms = this.producerRooms.get(owner);
            } else {
                ownerRooms = new ConcurrentHashMap<String, ProducerRoom>();
                producerRooms.put(owner, ownerRooms);
            }
            
            if (ownerRooms.containsKey(roomName)) {
		log.warn("Room {} for {} already exists", roomName, owner);
                return false;
            }
            
            log.info("Adding room {} descr {} color {} owner {}", roomName, roomDescr, roomColor, roomImageURL, owner);
            ownerRooms.put(roomName, new ProducerRoom(owner, roomName, roomDescr, roomColor, roomImageURL,
                    roomChannel, pipeline));
            return true;            
        }
        
        
        /**
         * Get room for given owner
         * @param owner owner name
         * @param roomName room name
         * @return ProducerRoom or null
         */
        public ProducerRoom getProducerRoom(String owner, String roomName) {
            ConcurrentMap<String, ProducerRoom> ownerRooms = this.producerRooms.get(owner);
            if (ownerRooms == null) {
                return null;
            }
            
            return ownerRooms.get(roomName);
        }
        
        
        /**
         * remove owner room
         * @param owner owner name
         * @param room room
         */
        public void removeProducerRoom(String owner, ProducerRoom room) {
            ConcurrentMap<String, ProducerRoom> ownerRooms = this.producerRooms.get(owner);
            if (ownerRooms == null) {
                return;
            }

            ownerRooms.remove(room.getName());
            room.close();
            log.info("{}: removed and closed", room);
        }
        
        
        /**
         * return all owner rooms
         * @param owner owner name
         * @return Collection of ProducerRooms
         */
        public Collection<ProducerRoom> getProducerRooms(String owner) {
            ConcurrentMap<String, ProducerRoom> ownerRooms = this.producerRooms.get(owner);
            if (ownerRooms == null) {
                ownerRooms = new ConcurrentHashMap<>();
            }

            return ownerRooms.values();
	}
}
