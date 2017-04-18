package tv.liveu.tvconnector;


/**
 * This interface is for classes that wish to receive TV Stream events.
 * This includes media, control, TV operator activity associated with TV Stream.
 *  
 * @author slava
 * @see TVStream
 * @see TVStreamEvent
 *
 */
public interface TVStreamListener {
	/**
	 * This method is called when TV Stream enter <b>preview</b> mode
	 * 
	 * @param event the <code>TVStreamEvent</code> indicating entering in <b>preview</b> mode  
	 */
	void previewEntered(TVStreamEvent event);
	
	/**
	 * This method is called when TV Stream leaves <b>preview</b> mode
	 * 
	 * @param event the <code>TVStreamEvent</code> indicating leaving from <b>preview</b> mode  
	 */
	void previewLeaved(TVStreamEvent event);

	/**
	 * This method is called when TV Stream enter <b>live</b> mode
	 * 
	 * @param event the <code>TVStreamEvent</code> indicating entering in <b>live</b> mode  
	 */
	void liveEntered(TVStreamEvent event);
	
	/**
	 * This method is called when TV Stream leaves <b>live</b> mode
	 * 
	 * @param event the <code>TVStreamEvent</code> indicating leaving from <b>live</b> mode  
	 */
	void liveLeaved(TVStreamEvent event);
	

	/**
	 * This method is called when media (video and audio) streams is started
	 * 
	 * @param event the <code>TVStreamEvent</code> indicating media stream is started
	 */
	void mediaStarted(TVStreamEvent event);

	/**
	 * This method is called when media (video and audio) streams is stopped
	 * 
	 * @param event the <code>TVStreamEvent</code> indicating media stream is stopped
	 */
	void mediaStopped(TVStreamEvent event);
	
	/**
	 * This method is called when stream media quality is changed
	 * 
	 * @param event the <code>TVStreamEvent</code> indicating media stream quality changed
	 */
	void qualityChanged(TVStreamEvent event);
}