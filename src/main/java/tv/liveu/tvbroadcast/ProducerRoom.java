/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tv.liveu.tvbroadcast;

import com.google.gson.JsonArray;
import java.io.IOException;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import org.kurento.client.MediaPipeline;
import org.kurento.client.PlayerEndpoint;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Collection;
import java.util.List;

/**
 * Producer private room class
 * @author slava
 */
public class ProducerRoom extends Room {

    /* private room link */
    final private String roomLink;

    /* list of play URLs */
    private final ConcurrentMap<String, PlayerEndpoint> playUriMedia = new ConcurrentHashMap<>();

    /**
     * Create empty private producer room
     *
     * @param roomOwner owner producer name
     * @param roomName room name
     * @param descr description for room
     * @param colorName room color
     * @param imageURL room image URL
     * @param roomChannel room channel
     * @param pipeline media pipeline
     */
    public ProducerRoom(String roomOwner, String roomName, String descr, String colorName, String imageURL,
            String roomChannel, MediaPipeline pipeline) {
        super(roomOwner, roomName, descr, colorName, imageURL, roomChannel, pipeline);
        this.roomLink = roomName + "_" + roomOwner;
    }

    
    /**
     * get uri link for this room
     * @return string representation of link
     */
    public String getLink() {
        return this.roomLink;
    }

    /**
     * join user to this room
     * @param user user to join
     * @throws IOException 
     */
    @Override
    public void join(UserSession user) throws IOException {
        super.join(user);
        /* send list of PlayURL if any */
        sendPlayUriList(user);
    }

    
    /**
     * get endpoint for play uri
     * @param playUriId id of the media stream
     * @return PlayerEndpoint for given id
     */
    public PlayerEndpoint getPlayUri(String playUriId) {
        return playUriMedia.get(playUriId);
    }
    
    
    /**
     * add URI for playing
     * @param uri URI to play
     * @return 
     */
    public Collection<String> addPlayUri(String uri, String title) {
        PlayerEndpoint p = new PlayerEndpoint.Builder(this.getPipeline(), uri).build();
        p.addTag("title", title);
        playUriMedia.put(p.getId(), p);
        final JsonObject msg = new JsonObject();
        msg.addProperty("id", "newPlayUri");
        msg.addProperty("name", p.getId());
        msg.addProperty("title", p.getTag("title"));
        msg.addProperty("uri", p.getUri());
        msg.addProperty("room", this.getName());

        p.play();
        
        log.info("{}: notifying participants about new play uri {}", this, p.getUri());
        return this.sendMessage(msg);
    }
    
    
    /**
     * remove URI for playing
     * @param uri
     * @throws IOException 
     */
    public void removePlayUri(String playUriId) throws IOException {

        PlayerEndpoint p = playUriMedia.remove(playUriId);

        log.info("{}: notifying all participant that {} is removed", this, playUriId);

        final List<String> unnotifiedParticipants = new ArrayList<>();
        final JsonObject msg = new JsonObject();
        msg.addProperty("id", "removePlayUri");
        msg.addProperty("name", p.getId());
        for (final UserSession participant : participants.values()) {
            try {
                participant.cancelPlayUri(p.getId());
                participant.sendMessage(msg);
            } catch (final IOException e) {
                unnotifiedParticipants.add(participant.getName());
            }
        }

        if (!unnotifiedParticipants.isEmpty()) {
            log.error("{}: participants {} could not be notified that play uri {} removed", this,
                    unnotifiedParticipants, p.getId());

        }
    }


    /**
     * send play uri list to user (at room enter)
     * @param user user to send
     * @throws IOException 
     */
    private void sendPlayUriList(UserSession user) throws IOException {
        final JsonArray uriArray = new JsonArray();
        for (final String s : playUriMedia.keySet()) {
            PlayerEndpoint p = playUriMedia.get(s);
            JsonObject r = new JsonObject();
            r.addProperty("name", p.getId());
            r.addProperty("title", p.getTag("title"));
            r.addProperty("uri", p.getUri());
            uriArray.add(r);
        }

        final JsonObject msg = new JsonObject();
        msg.addProperty("id", "listPlayUri");
        msg.addProperty("room", this.getName());
        msg.add("data", uriArray);
        log.info("{} sending a play uri list of {}", user, uriArray.size());
        user.sendMessage(msg);
    }
    
}
