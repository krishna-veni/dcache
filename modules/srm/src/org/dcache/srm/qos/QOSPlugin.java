package org.dcache.srm.qos;

import org.dcache.srm.util.Configuration;

public interface QOSPlugin {
	QOSTicket createTicket(
			String credential, 
			Long bytes,
			String srcURL, 
			int srcPortMin, 
			int srcPortMax,
			String srcProtocol,
			String dstURL, 
			int dstPortMin,
	        int dstPortMax,
	        String dstProtocol);

	void setSrmConfiguration(Configuration configuration);
	
	void addTicket(QOSTicket qosTicket);
	
	boolean submit();
	
	void sayStatus(QOSTicket qosTicket);
	
}
