/*
 *  Copyright (c) xsocket.org, 2006 - 2010. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Please refer to the LGPL license at: http://www.gnu.org/copyleft/lesser.txt
 * The latest copy of this software may be found on http://www.xsocket.org/
 */
package org.xsocket.connection.multiplexed;

import java.io.IOException;

import java.nio.BufferUnderflowException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.SerializedTaskQueue;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnectionScoped;
import org.xsocket.connection.IConnectionTimeoutHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IHandler;
import org.xsocket.connection.IIdleTimeoutHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.multiplexed.MultiplexedUtils.HandlerInfo;


/**
*
* @author grro@xsocket.org
*/
class PipelineHandlerAdapter {
	
	private static final Logger LOG = Logger.getLogger(PipelineHandlerAdapter.class.getName());
	
	
	private static final IHandler NULL_HANDLER = new NullHandler();
	private static final HandlerInfo NULL_HANDLER_INFO = MultiplexedUtils.getHandlerInfo(NULL_HANDLER);
	

	private IHandler handler = null;
	private HandlerInfo handlerInfo = null;
	
	PipelineHandlerAdapter(IHandler handler, HandlerInfo handlerInfo) {
		this.handler = handler;
		this.handlerInfo = handlerInfo;
	}
	
	
	static PipelineHandlerAdapter newInstance(IHandler handler) {
		if (handler == null) {
			return new PipelineHandlerAdapter(NULL_HANDLER, NULL_HANDLER_INFO);
			
		} else {
			HandlerInfo handlerInfo = MultiplexedUtils.getHandlerInfo(handler);
			return new PipelineHandlerAdapter(handler, handlerInfo);
		}
	}
	
	
	final IHandler getHandler() {
		return handler;
	}
	
	final HandlerInfo getHandlerInfo() {
		return handlerInfo;
	}
	
	
	
	public boolean onConnect(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		
	    if (handlerInfo.isConnectHandler()) {
		    
		    Runnable task = new Runnable() {
                public void run() {
                    try {
                        performOnConnect(connection, taskQueue);
                    } catch (IOException ioe) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("error occured while performing onConnect multithreaded " + getHandler() + " " + ioe.toString());
                        }
                    }
                }
            };
            
            if (getHandlerInfo().isConnectHandlerMultithreaded()) {
                taskQueue.performMultiThreaded(task, connection.getWorkerpool());
                
            } else {
                taskQueue.performNonThreaded(task, connection.getWorkerpool());
            }
		}
		
		return true;
	}

	
	
	private void performOnConnect(INonBlockingConnection connection, SerializedTaskQueue taskQueue) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
	    try {
                
	        if (handlerInfo.isPipelineConnectHandler()) {
	            ((IPipelineConnectHandler) handler).onConnect((INonBlockingPipeline) connection);
	        } else {                
	            ((IConnectHandler) handler).onConnect(connection);
	        }
                
	    } catch (MaxReadSizeExceededException mee) {
	        closeSilence(connection);

	    } catch (BufferUnderflowException bue) {
	        //  ignore

	    } catch (RuntimeException re) {     
	        if (LOG.isLoggable(Level.FINE)) {
	            LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnect by appHandler. " + handler + " Reason: " + re.toString());
	        }
	        closeSilence(connection);
	        throw re;
                
	    } catch (IOException ioe) {
	        if (LOG.isLoggable(Level.FINE)) {
	            LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnect by appHandler. " + handler + " Reason: " + ioe.toString());
	        }
	        closeSilence(connection);
	        throw ioe;
	    }
	}
    

	
	
	public boolean onData(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		
	    if (handlerInfo.isDataHandler()) {
	        
            Runnable task = new Runnable() {
                public void run() {
                    try {
                       performOnData(connection, taskQueue);
                    } catch (IOException ioe) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("error occured while performing onData multithreaded " + getHandler() + " " + ioe.toString());
                        }
                    }
                }
            };
            
            if (getHandlerInfo().isDataHandlerMultithreaded()) {
                taskQueue.performMultiThreaded(task, connection.getWorkerpool());
                
            } else {
                taskQueue.performNonThreaded(task, connection.getWorkerpool());
            }
		}

		return true;
	}
	
	
	
	private void performOnData(INonBlockingConnection connection, SerializedTaskQueue taskQueue) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
   
	    // loop until readQueue is empty or nor processing has been done (readQueue has not been modified)
        while ((connection.available() != 0) && !connection.isReceivingSuspended()) {
            int version = connection.getReadBufferVersion();
            

            if (connection.getHandler() != handler) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("handler " + handler.getClass().getName() + " replaced by " + 
                             connection.getHandler().getClass().getName() + ". stop handling data for old handler");
                }
                return;
            }

            
            // calling onData method of the handler (return value will be ignored)
            try {
                
                if (handlerInfo.isPipelineDataHandler()) {
                    ((IPipelineDataHandler) handler).onData((INonBlockingPipeline) connection);
                } else {                
                    ((IDataHandler) handler).onData(connection);
                }

                if (connection.getReadBufferVersion() == version) {
                    return;
                }
                
            } catch (MaxReadSizeExceededException mee) {
                closeSilence(connection);
                return;

            } catch (BufferUnderflowException bue) {
                //  ignore
                return;

            } catch (RuntimeException re) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling data by appHandler. " + handler + " Reason: " + re.toString());
                }
                closeSilence(connection);
                throw re;
                        
            } catch (IOException ioe) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling data by appHandler. " + handler + " Reason: " + ioe.toString());
                }
                closeSilence(connection);
                throw ioe;
            }
        } 
    }
    
	
	
	public boolean onDisconnect(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue) throws IOException {
		
	    if (handlerInfo.isDisconnectHandler()) {
	        Runnable task = new Runnable() {
                public void run() {
                    try {
                        performOnDisconnect(connection, taskQueue);
                    } catch (IOException ioe) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("error occured while performing onDisconnect multithreaded " + getHandler() + " " + ioe.toString());
                        }
                    }
                }
            };
            
            if (getHandlerInfo().isDisconnectHandlerMultithreaded()) {
                taskQueue.performMultiThreaded(task, connection.getWorkerpool());
                
            } else {
                taskQueue.performNonThreaded(task, connection.getWorkerpool());
            }
		}
		
		return true;
	}
	
	
	
	private void performOnDisconnect(INonBlockingConnection connection, SerializedTaskQueue taskQueue) throws IOException {
  
	    try {
            
            if (handlerInfo.isPipelineDisconnectHandler()) {
                ((IPipelineDisconnectHandler) handler).onDisconnect((INonBlockingPipeline) connection);
            } else {    
                ((IDisconnectHandler) handler).onDisconnect(connection);
            }
            
        }  catch (RuntimeException re) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + handler + " Reason: " + re.toString());
            }
            closeSilence(connection);
            throw re;
            
        }  catch (IOException ioe) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + handler + " Reason: " + ioe.toString());
            }
            closeSilence(connection);
            throw ioe;
        }   
    }
	
	
	
	public boolean onIdleTimeout(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue) throws IOException {

	    if (handlerInfo.isIdleTimeoutHandler()) {
            
	        Runnable task = new Runnable() {
	            public void run() {
                    try {
                        performOnIdleTimeout(connection, taskQueue);
                    } catch (IOException ioe) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("error occured while performing onIdleTimeout multithreaded " + getHandler() + " " + ioe.toString());
                        }
                    }
                }
            };
            
            if (getHandlerInfo().isIdleTimeoutHandlerMultithreaded()) {
                taskQueue.performMultiThreaded(task, connection.getWorkerpool());
                
            } else {
                taskQueue.performNonThreaded(task, connection.getWorkerpool());
            }
        } else {
            closeSilence(connection);
        }
            
        return true;
	}
	
	
	
	private void performOnIdleTimeout(INonBlockingConnection connection, SerializedTaskQueue taskQueue) throws IOException {
	    try {
            
            boolean isHandled = false;
            
            if (handlerInfo.isPipelineIdleTimeoutHandler()) {
                isHandled = ((IPipelineIdleTimeoutHandler) handler).onIdleTimeout((INonBlockingPipeline) connection);
            } else {
                isHandled = ((IIdleTimeoutHandler) handler).onIdleTimeout(connection);
            }
            
            if (!isHandled) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection because idle timeout has been occured and timeout handler returns true)");
                }
                closeSilence(connection);
            }
            
        } catch (MaxReadSizeExceededException mee) {
            closeSilence(connection);

        } catch (BufferUnderflowException bue) {
            // ignore

        } catch (RuntimeException re) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onIdleTimeout by appHandler. " + handler + " Reason: " + re.toString());
            }
            closeSilence(connection);
            throw re;
            
        } catch (IOException ioe) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onIdleTimeout by appHandler. " + handler + " Reason: " + ioe.toString());
            }
            closeSilence(connection);
            throw ioe;
        }
    }
	
	
	public boolean onConnectionTimeout(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue) throws IOException {
 
	    if (handlerInfo.isConnectionTimeoutHandler()) {

            Runnable task = new Runnable() {
                public void run() {
                    try {
                        performOnConnectionTimeout(connection, taskQueue);
                    } catch (IOException ioe) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("error occured while performing onConnectionTimeout multithreaded " + getHandler() + " " + ioe.toString());
                        }
                    }
                }
            };
            
            if (getHandlerInfo().isConnectionTimeoutHandlerMultithreaded()) {
                taskQueue.performMultiThreaded(task, connection.getWorkerpool());
                
            } else {
                taskQueue.performNonThreaded(task, connection.getWorkerpool());
            }            
                    
        } else {
            closeSilence(connection);
        }
        
        return true;
	}
	
	
	
	private void performOnConnectionTimeout(INonBlockingConnection connection, SerializedTaskQueue taskQueue) throws IOException {
        try {
            
            boolean isHandled = false;
            
            if (handlerInfo.isPipelineConnectionTimeoutHandler()) {
                isHandled = ((IPipelineConnectionTimeoutHandler) handler).onConnectionTimeout((INonBlockingPipeline) connection);
            } else {
                isHandled = ((IConnectionTimeoutHandler) handler).onConnectionTimeout(connection);
            }
                
            if (!isHandled) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection because connection timeout has been occured and timeout handler returns true)");
                }
                closeSilence(connection);
            }

        } catch (MaxReadSizeExceededException mee) {
            closeSilence(connection);

        } catch (BufferUnderflowException bue) {
            //  ignore

        } catch (RuntimeException re) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnectionTimeout by appHandler. " + handler + " Reason: " + re.toString());
            }
            closeSilence(connection);
            throw re;
        
        } catch (IOException ioe) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnectionTimeout by appHandler. " + handler + " Reason: " + ioe.toString());
            }
            closeSilence(connection);
            throw ioe;
        }     
    }
    
	
	
	public final void onInit() {
		if (handlerInfo.isLifeCycle()) {
			((ILifeCycle) handler).onInit();
		}		
	}
	
	
	public final void onDestroy() {
		if (handlerInfo.isLifeCycle()) {
			try {
				((ILifeCycle) handler).onDestroy();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("exception occured by destroying " + handler + " " + ioe.toString());
				}
			}
		}		
	}
	
	
	PipelineHandlerAdapter getConnectionInstance() {
		if (handlerInfo.isConnectionScoped()) {
			try {
				IHandler hdlCopy = (IHandler) ((IConnectionScoped) handler).clone();
				return new PipelineHandlerAdapter(hdlCopy, handlerInfo);
			} catch (CloneNotSupportedException cnse) {
				throw new RuntimeException(cnse.toString());
			}
			
		} else {
			return this;
		}
	}
	
	
	private void closeSilence(INonBlockingConnection connection) {
		try {
			connection.close();
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection " + connection + " " + e.toString());
			}
		}
	}

	

	@Execution(Execution.NONTHREADED)
	private static final class NullHandler implements IHandler {
	}
}
