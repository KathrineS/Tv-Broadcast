package tv.liveu.tvconnector;

/**
 * This interface is for classes that wish to receive TV Connector events.
 *  
 * @author slava
 * @see TVConnector
 * @see TVConnectorEvent
 *
 */
public interface TVConnectorListener {

	/**
	 * This method is called when TVConnector connect attempt is done
	 * 
	 * @param event the <code>TVConnectorEvent</code> indicating connect attemp is done  
	 */
	void connectDone(TVConnectorEvent event);
	
	/**
	 * This method is called when TVConnector connection destroyed
	 * 
	 * @param event the <code>TVConnectorEvent</code> indicating connection is destroyed  
	 */
	void connectionDestroyed(TVConnectorEvent event);
	
	/**
	 * This method is called when new <code>TVStream</code> is created
	 * 
	 * @param event the <code>TVConnectorEvent</code> indicating <code>TVStream</code> is created  
	 */
	void streamCreated(TVConnectorEvent event);
	
	
	/**
	 * This method is called when <code>TVStream</code> is destroyed
	 * 
	 * @param event the <code>TVConnectorEvent</code> indicating <code>TVStream</code> is destroyed
	 */
	void streamDestroyed(TVConnectorEvent event);
	
}