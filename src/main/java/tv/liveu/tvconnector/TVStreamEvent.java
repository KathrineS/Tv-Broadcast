package tv.liveu.tvconnector;


/**
 * This event is generated for a TV stream event.
 * There are three main categories of TV stream events:
 * <ul>
 * <li>Media stream events include media distribution (MMH)</li>
 * <li>Stream control events include stream control via Central</li>
 * <li>Management events include user or operator activity (moving to preview or Live)</li>
 * </ul>
 * @author slava
 * @see TVStream
 * @see TVStreamListener
 */
public class TVStreamEvent {

	/** This event indicates that stream is created */
	public static final int STREAM_CREATED = 1;
	
	/** This event indicates that stream enter preview mode */
	public static final int PREVIEW_ENTERED = 101;
	
	/** This event indicates that stream leave preview mode */
	public static final int PREVIEW_LEAVED = 102;
	
	/** This event indicates that stream enter live mode */
	public static final int LIVE_ENTERED = 105;
	
	/** This event indicates that stream leaved live mode */
	public static final int LIVE_LEAVED = 106;
	
	/** This event indicates that stream quality changed */
	public static final int STREAM_CHANGED = 301;
	/**
	 * The ID of event
	 * @see #getID()
	 */
	private int id;
	
	/**
	 * Initializes a new instance of <code>TVStreamEvent</code> with the specified information.
	 * Invalid id leads to unspecified results.
	 * 
	 * @param id the event id
	 * @param message event description
	 */
	public TVStreamEvent(int id, String message) {
		this.id = id;
	}
	
	/**
	 * This method returns the ID of event
	 * 
	 *  @return the id of event
	 */
	int getID() { return this.id; }
}
