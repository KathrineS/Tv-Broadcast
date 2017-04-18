package tv.liveu.tvconnector;


import java.util.ArrayList;
/**
 * Connection to TV provider. Handle media, control streams and TV broadcast related functionality.
 * 
 * @author slava
 *
 */
public class TVConnector {
	
	public TVConnector() {}
	
	public void onStreamPublised() {} // may all this to Listener and Event?
	
	public void open(String url) {}
	
	public void close() {};
	
	public ArrayList<String> getTVStreamList() { return null; }
	
	public void publishTVStream(TVStream stream) {}
		
	public TVStream getTVStream(String name) { return null; }
	
	
}