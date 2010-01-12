package gov.nist.javax.sip.header.extensions;


import java.text.ParseException;

import javax.sip.header.Header;
import javax.sip.header.Parameters;

/**
 * References header: See http://tools.ietf.org/html/draft-worley-references-05
 */
public interface ReferencesHeader extends Parameters, Header {
    
    public static final String NAME = "References";
    
    public static final String CHAIN = "chain";
    
    public static final String INQUIRY =  "inquiry";
    
    public static final String REFER = "refer" ;
    
    public static final String SEQUEL = "sequel";
    
    public static final String XFER =  "xfer";
    
    public static final String BRANCH = "branch";
    
    public static final String METHOD = "method";
    
    public static final String REL = "rel";
    
    public void setCallId(String callId) throws ParseException;
       
    public String getCallId();
       
    public void setRel (String rel) throws ParseException;
    
    public String getRel();
    
    public void setBranch(String branch) throws ParseException;
     
    public String getBranch();
       
    public void setMethod(String method)throws ParseException;
    
    public String getMethod();
    
    

}
