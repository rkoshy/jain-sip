
package gov.nist.javax.sip;

import java.util.*;
import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.stack.*;
import gov.nist.javax.sip.message.*;
import javax.sip.message.*;
import javax.sip.header.*;
import javax.sip.address.*;
import javax.sip.*;
import gov.nist.core.*;
import java.io.*;
import java.text.ParseException;
//ifdef SIMULATION
/*
import sim.java.net.*;
//endif
 */
/** Event Scanner to deliver events to the Listener.
 *
 * @version JAIN-SIP-1.1 $Revision: 1.7 $ $Date: 2004-04-16 02:53:08 $
 *
 * @author M. Ranganathan <mranga@nist.gov>  <br/>
 *
 * <a href="{@docRoot}/uncopyright.html">This code is in the public domain.</a>
 *
 */
class EventScanner implements Runnable {
    
    private   boolean isStopped;
    
    protected int refCount;
    
    // SIPquest: Fix for deadlocks
    private LinkedList pendingEvents = new LinkedList();

    private int[] eventMutex = {0};
    
    private SipStackImpl sipStackImpl;
    
//ifdef SIMULATION
/*
        private     SimMessageObject pendingEventsShadow;
//endif
*/
    
    
    public EventScanner(SipStackImpl sipStackImpl) {
        this.pendingEvents = new LinkedList();
//ifdef SIMULATION
/*
                this.pendingEventsShadow = new SimMessageObject();
                SimThread myThread = new SimThread(this);
//else
*/
        Thread myThread = new Thread(this);
//endif
//
        this.sipStackImpl = sipStackImpl;
        myThread.setName("EventScannerThread");
        
        myThread.start();
        
    }
    
    
    public void addEvent( EventWrapper eventWrapper ) {

//ifndef SIMULATION
//
	synchronized (this.eventMutex)
//else
/*
	this.pendingEventsShadow.enterCriticalSection();
	try 
//endif
*/ 
	{

            		pendingEvents.add(eventWrapper);
        
			// Add the event into the pending events list

//ifdef SIMULATION
/* 
			this.pendingEventsShadow.doNotify();
//else
*/

            		eventMutex.notify();
//endif
        }

//ifdef SIMULATION
/*
	finally { this.pendingEventsShadow.leaveCriticalSection(); }
//endif
*/

    }
    
    
    /** Stop the event scanner.
     * Decrement the reference count and exit the scanner thread if the ref count
     * goes to 0.
     */
    
    public void stop() {
        
        if (this.refCount >  0 ) this.refCount --;
        
        if (this.refCount == 0) {
            synchronized (eventMutex) {
                isStopped = true;
                eventMutex.notify();
            }
        }
    }
    
    
    
    public void run() {
        while (true) {
            EventObject sipEvent = null;
            EventWrapper eventWrapper = null;
            
            LinkedList eventsToDeliver;
//ifndef SIMULATION
//
		synchronized (this.eventMutex)
//else
/*
		this.pendingEventsShadow.enterCriticalSection();
		try 
//endif
*/ 
		{
                // First, wait for some events to become available.
                if (pendingEvents.isEmpty()) {
                    // There's nothing in the list, check to make sure we haven't
                    // been stopped. If we have, then let the thread die.
                    if (this.isStopped) {
                        if (LogWriter.needsLogging)
                            sipStackImpl.logMessage( "Stopped event scanner!!");
                        return;
                    }
                    
                    // We haven't been stopped, and the event list is indeed
                    // rather empty. Wait for some events to come along.
                    try {
//ifdef SIMULATION
/*
			this.pendingEventsShadow.doWait();
//else
*/
                        eventMutex.wait();
//endif
                    } catch (InterruptedException ex) {
                        // Let the thread die a normal death
                        sipStackImpl.logMessage("Interrupted!");
                        return;
                    }
                }
                
                // There are events in the 'pending events list' that need
                // processing.  Hold onto the old 'pending Events' list, but
                // make a new one for the other methods to operate on. This
                // tap-dancing is to avoid deadlocks and also to ensure that
                // the list is not modified while we are iterating over it.
                eventsToDeliver = pendingEvents;
                pendingEvents = new LinkedList();
            }
//ifdef SIMULATION
/*
	   finally { this.pendingEventsShadow.leaveCriticalSection(); }
//endif
*/
            ListIterator iterator = eventsToDeliver.listIterator();
            while (iterator.hasNext()) {
                eventWrapper = (EventWrapper) iterator.next();
                sipEvent = eventWrapper.sipEvent;
                if (LogWriter.needsLogging) {
                    sipStackImpl.logMessage(
                    "Processing "
                    + sipEvent
                    + "nevents "
                    + eventsToDeliver.size());
                }
                SipListener sipListener = ((SipProviderImpl)sipEvent.getSource()).sipListener;
                if (sipEvent instanceof RequestEvent) {
                    // Check if this request has already created a
                    // transaction
                    SIPRequest sipRequest =
                    (SIPRequest) ((RequestEvent) sipEvent).getRequest();
                    // If this is a dialog creating method for which a server
                    // transaction already exists or a method which is
                    // not dialog creating and not within an existing dialog
                    // (special handling for cancel) then check to see if
                    // the listener already created a transaction to handle
                    // this request and discard the duplicate request if a
                    // transaction already exists. If the listener chose
                    // to handle the request statelessly, then the listener
                    // will see the retransmission.
                    // Note that in both of these two cases, JAIN SIP will allow
                    // you to handle the request statefully or statelessly.
                    // An example of the latter case is REGISTER and an example
                    // of the former case is INVITE.
                    if (sipStackImpl.isDialogCreated(sipRequest.getMethod())) {
                        SIPServerTransaction tr =  (SIPServerTransaction) sipStackImpl.findTransaction(sipRequest, true);
                        DialogImpl dialog = sipStackImpl.getDialog(sipRequest.getDialogId(true)) ;
                        if (tr != null  &&  ! tr.passToListener() ) {
                            if (LogWriter.needsLogging)
                                sipStackImpl.logMessage(
                                "transaction already exists!");
                            continue;
                        }
                    } else if  ( (!sipRequest.getMethod().equals(Request.CANCEL)) &&
                    sipStackImpl.getDialog(sipRequest.getDialogId(true)) == null)  {
                        // not dialog creating and not a cancel.
                        // transaction already processed this message.
                        SIPTransaction tr = sipStackImpl.findTransaction(sipRequest, true);
                        // If transaction already exists bail.
                        if (tr != null) {
                            if (LogWriter.needsLogging)
                                sipStackImpl.logMessage(
                                "transaction already exists!");
                            continue;
                        }
                    }
                    // Set up a pointer to the transaction.
                    sipRequest.setTransaction(eventWrapper.transaction);
                    // Processing incoming CANCEL.
                    if (sipRequest.getMethod().equals(Request.CANCEL)) {
                        SIPTransaction tr =
                        sipStackImpl.findTransaction(sipRequest, true);
                        if (tr != null
                        && tr.getState()
                        == SIPTransaction.TERMINATED_STATE) {
                            // If transaction already exists but it is
                            // too late to cancel the transaction then
                            // just respond OK to the CANCEL and bail.
                            if (LogWriter.needsLogging)
                                sipStackImpl.logMessage(
                                "Too late to cancel Transaction");
                            // send OK and just ignore the CANCEL.
                            try {
                                tr.sendMessage(
                                sipRequest.createResponse(Response.OK));
                            } catch (IOException ex) {
                                // Ignore?
                            }
                            continue;
                        }
                    }
                    
                    // Change made by SIPquest
                    try {
                        sipListener.processRequest((RequestEvent) sipEvent);
                    }
                    catch (Exception ex) {
                        // We cannot let this thread die under any
                        // circumstances. Protect ourselves by logging
                        // errors to the console but continue.
                        ex.printStackTrace();
                    }
                    if (eventWrapper.transaction != null) eventWrapper.transaction.clearEventPending();
                    
                } else if (sipEvent instanceof ResponseEvent) {
                    // Change made by SIPquest
                    try {
                        sipListener.processResponse((ResponseEvent) sipEvent);
                    }
                    catch (Exception ex) {
                        // We cannot let this thread die under any
                        // circumstances. Protect ourselves by logging
                        // errors to the console but continue.
                        ex.printStackTrace();
                    }
                    // The original request is not needed except for INVITE
                    // transactions -- null the pointers to the transactions so
                    // that state may be released.
                    SIPClientTransaction ct = (SIPClientTransaction)
                    eventWrapper.transaction;
                    if ( ct != null
                    && TransactionState.COMPLETED == ct.getState()
                    && ct.getOriginalRequest() != null
                    && !ct.getOriginalRequest().getMethod().equals
                    (Request.INVITE)) {
                        // reduce the state to minimum
                        // This assumes that the application will not need
                        // to access the request once the transaction is
                        // completed.
                        ct.clearState() ;
                    }
                    // mark no longer in the event queue.
                    if (ct != null ) {
                        ct.clearEventPending();
                        // If responses have been received in the window
                        // notify the pending response thread so he can take care of it.
                        // cannot do this in the context of the current thread else it
                        // will deadlock.
                        if (ct.hasResponsesPending()) {
                            synchronized(sipStackImpl) { sipStackImpl.notify(); }
                        }
                    }
                } else if (sipEvent instanceof TimeoutEvent) {
                    // Change made by SIPquest
                    try {
                        sipListener.processTimeout((TimeoutEvent) sipEvent);
                    }
                    catch (Exception ex) {
                        // We cannot let this thread die under any
                        // circumstances. Protect ourselves by logging
                        // errors to the console but continue.
                        ex.printStackTrace();
                    }
                } else {
                    if (LogWriter.needsLogging)
                        sipStackImpl.logMessage("bad event" + sipEvent);
                }
            }
        } // end While
    }
}