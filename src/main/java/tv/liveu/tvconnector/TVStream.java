package tv.liveu.tvconnector;

import org.kurento.client.RtpEndpoint;


/**
 * Video, audio TV stream.
 * 
 * @author slava
 *
 */
public class TVStream {

	/** Stream description */
	private final String description;
	
	/** Kurento RTP media endpoint */
	private final RtpEndpoint endpoint;
	
	/**
	 * TV stream channel with media and control
	 *   
	 * @param description description for stream
	 * @param endpoint kurento RTP endpoint
	 */
	public TVStream(String description, RtpEndpoint endpoint) {
		this.description = description;
		this.endpoint = endpoint;
	}
	
	/**
	 * get stream state
	 * 
	 * @return current stream state
	 */
	public int getState() {
		return 0;
	}
	
	/**
	 * get Kurento RTP endpoint
	 * 
	 * @return <code>RtpEndpoint</code>
	 */
	 public RtpEndpoint getEndpoint() {
		 return this.endpoint;
	 }
	 
	 /**
	  * send <b>preview</b> request for this stream, corresponding <code>TVStreamEvent</code> will be emitted
	  */
	 public void requestPreview() {}
	 
	 /**
	  * send <b>live</b> request for this stream, corresponding <code>TVStreamEvent</code> will be emitted
	  */
	 public void requestLive() {}
	 
	 /**
	  * send request for high-quality media
	  */
	 public void requestHQ() {}

	 /**
	  * register event listener for stream events
	  * 
	  * @param listener <code>TVStreamListener</code> class implementation
	  */
	  public void addEventListener(TVStreamListener listener) {
		return;
	  }
		
}

