/*******************************************************************************
* Product of NIST/ITL Advanced Networking Technologies Division (ANTD).        *
*******************************************************************************/
package gov.nist.javax.sip.header;
import javax.sip.header.*;


/**
* RecordRoute List of SIP headers (a collection of Addresses)
*
*@version  JAIN-SIP-1.1
*
*@author M. Ranganathan <mranga@nist.gov>  <br/>
*
*<a href="{@docRoot}/uncopyright.html">This code is in the public domain.</a>
*
*/
public class RecordRouteList extends SIPHeaderList {
    
        /** Default constructor
         */    
	public RecordRouteList() {
	 	super( RecordRoute.class, RecordRouteHeader.NAME);
	}

	
        
}
