package tv.liveu.tvconnector;

/**
 * This event is generated for a TVConnector event.
 * Event can be kind of:
 * <ul>
 * <li>open-close connection to Central</li>
 * <li>create-destroy <code>TVStream</code></li>
 * <li>stream control and authentication events</li>
 * </ul>
 * 
 * @author slava
 * @see TVConnector
 * @see TVConnectorListener
 * @see TVStream
 */
public class TVConnectorEvent {
	/** This event indicates that connection is successfully opened */
	public static final int CONNECT_SUCCESS = 1;
	
	/** This event indicates that connection is failed */
	public static final int CONNECT_FAILED = 2; 
	
	/** This event indicates that new stream created */
	public static final int STREAM_CREATED = 201;
	
	/** This event indicates that stream is destroyed */
	public static final int STREAM_DESTROYED = 202;

	
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
	public TVConnectorEvent(int id, String message) {
		this.id = id;
	}
	
	/**
	 * This method returns the ID of event
	 * 
	 *  @return the id of event
	 */
	int getID() { return this.id; }

}
