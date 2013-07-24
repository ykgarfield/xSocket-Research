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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.IntrospectionBasedDynamicMBean;
import org.xsocket.Resource;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnectionScoped;
import org.xsocket.connection.IConnectionTimeoutHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IHandler;
import org.xsocket.connection.IIdleTimeoutHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.IWriteCompletionHandler;




/**
 * utility class 
 * 
 * @author grro@xsocket.org
 */
final class MultiplexedUtils {
	
	private static final Logger LOG = Logger.getLogger(MultiplexedUtils.class.getName());
	

	@SuppressWarnings("unchecked")
	private static final Map<Class, HandlerInfo> handlerInfoCache = ConnectionUtils.newMapCache(25);
	
    @SuppressWarnings("unchecked")
	private static final Map<Class, CompletionHandlerInfo> completionHandlerInfoCache = ConnectionUtils.newMapCache(25);


	private static String implementationVersion = null;
	private static String implementationDate = null;
	private static String xSocketImplementationVersion;
	

	
	private MultiplexedUtils() { }


	
	
	/**
	 * @deprecated use {@link MultiplexedUtils#getImplementationVersion()} instead
	 */
	public static String getVersionInfo() {
		return getImplementationVersion();
	}
	
	
    /**
     * get the xSocket implementation version
     * 
     * @return the xSocket implementation version
     */
    static String getXSocketImplementationVersion() {
        
        if (xSocketImplementationVersion == null) {
            readVersionFile();
        }
        
        return xSocketImplementationVersion;
    }
	

    
    
    
	/**
	 * get the implementation version
	 * 
	 * @return the implementation version
	 */
	public static String getImplementationVersion() {
		
		if (implementationVersion == null) {
			readVersionFile();
		}
		
		return implementationVersion;
	}

	
	/**
	 * get the implementation date
	 * 
	 * @return the implementation date
	 */
	public static String getImplementationDate() {
		
		if (implementationDate== null) {
			readVersionFile();
		}
		
		return implementationDate;
	}

	
	private static void readVersionFile() {
		
		implementationVersion = "<unknown>";
		implementationDate = "<unknown>";
			
		InputStreamReader isr = null;
		LineNumberReader lnr = null;
		try {
			isr = new InputStreamReader(ConnectionUtils.class.getResourceAsStream("/org/xsocket/connection/multiplexed/version.txt"));
			if (isr != null) {
				lnr = new LineNumberReader(isr);
				String line = null;
				do {
					line = lnr.readLine();
					if (line != null) {
						if (line.startsWith("Implementation-Version=")) {
							implementationVersion = line.substring("Implementation-Version=".length(), line.length()).trim();
							
						} else if (line.startsWith("Implementation-Date=")) {
							implementationDate = line.substring("Implementation-Date=".length(), line.length()).trim();

						} else if (line.startsWith("Dependency.xSocket.Implementation-Version=")) {
						    xSocketImplementationVersion = line.substring("Dependency.xSocket.Implementation-Version=".length(), line.length()).trim();
						}
					}
				} while (line != null);
				lnr.close();
			}
			
		} catch (IOException ioe) {
		    
            implementationDate = "<unknown>";
            implementationVersion  = "<unknown>";
            xSocketImplementationVersion  = "<unknown>";

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Error occured by reading version file " + ioe.toString());
			}
			
		} finally {
			try {
				if (lnr != null) {
					lnr.close();
				}
					
				if (isr != null) {
					isr.close();
				}
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("exception occured by closing version.txt file stream " + ioe.toString());
				}
			}
		} 

	}
	
	
	
	
	
	static void injectServerField(IServer server, Object handler) {
		Field[] fields = handler.getClass().getDeclaredFields();
		for (Field field : fields) {
			if (field.isAnnotationPresent(Resource.class)) {
				Resource res = field.getAnnotation(Resource.class);
				if ((field.getType() == IServer.class) || (res.type() == IServer.class)) {
					field.setAccessible(true);
					try {
						field.set(handler, server);
					} catch (IllegalAccessException iae) {
						LOG.warning("could not inject server for attribute " + field.getName() + ". Reason " + iae.toString());
					}
				}
			}
		}
	}
	
	
	
	static ObjectName exportMbean(MBeanServer mbeanServer, ObjectName objectname, Object handler) {
		try {
			String namespace = objectname.getDomain();
			objectname = new ObjectName(namespace + ":type=HttpRequestHandler, name=" + handler.getClass().getSimpleName());
			mbeanServer.registerMBean(new IntrospectionBasedDynamicMBean(handler), objectname);
		} catch (JMException mbe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error could not register handlers mbean. reason: " + mbe.toString());
			}
		}
		
		return objectname;
	}
	
	

	
	static HandlerInfo getHandlerInfo(IHandler handler) {
		HandlerInfo handlerInfo = handlerInfoCache.get(handler.getClass());

		if (handlerInfo == null) {
			handlerInfo = new  HandlerInfo(handler);
			handlerInfoCache.put(handler.getClass(), handlerInfo);
		}

		return handlerInfo;
	}
	
	
	  static CompletionHandlerInfo getCompletionHandlerInfo(IWriteCompletionHandler handler) {
	        CompletionHandlerInfo completionHandlerInfo = completionHandlerInfoCache.get(handler.getClass());

	        if (completionHandlerInfo == null) {
	            completionHandlerInfo = new CompletionHandlerInfo(handler);
	            completionHandlerInfoCache.put(handler.getClass(), completionHandlerInfo);
	        }

	        return completionHandlerInfo;
	    }
	
	
	private static boolean isHandlerMultithreaded(Object handler) {
		Execution execution = handler.getClass().getAnnotation(Execution.class);
		if (execution != null) {
			if(execution.value() == Execution.NONTHREADED) {
				return false;
				
			} else {
				return true;
			}

		} else {
			return true;
		}
	}
	
	@SuppressWarnings("unchecked")
	private static boolean isMethodThreaded(Class clazz, String methodname, boolean dflt, Class... paramClass) {
		try {
			Method meth = clazz.getMethod(methodname, paramClass);
			Execution execution = meth.getAnnotation(Execution.class);
			if (execution != null) {
				if(execution.value() == Execution.NONTHREADED) {
					return false;
				} else {
					return true;
				}
			} else {
				return dflt;
			}
			
		} catch (NoSuchMethodException nsme) {
			return dflt;
		}
	}
	
	
	static final class HandlerInfo {
		
		private boolean isConnectHandler = false; 
		private boolean isPipelineConnectHandler = false;
		private boolean isConnectHandlerMultithreaded = false;
		
		private boolean isDataHandler = false;
		private boolean isPipelineDataHandler = false;
		private boolean isDataHandlerMultithreaded = false;
		
		private boolean isDisconnectHandler = false;
		private boolean isPipelineDisconnectHandler = false;
		private boolean isDisconnectHandlerMultithreaded = false;
		
		private boolean isIdleTimeoutHandler = false;
		private boolean isPipelineIdleTimeoutHandler = false;
		private boolean isIdleTimeoutHandlerMultithreaded = false;
		
		private boolean isConnectionTimeoutHandler = false;
		private boolean isPipelineConnectionTimeoutHandler = false;
		private boolean isConnectionTimeoutHandlerMultithreaded = false;
		
		
		private boolean isLifeCycle = false;
		private boolean isConnectionScoped = false;
		private boolean isHandlerMultithreaded = false;
		private boolean isNonThreaded = false;
		
		

		HandlerInfo(IHandler handler) {
			isConnectHandler = (handler instanceof IConnectHandler);
			isDataHandler = (handler instanceof IDataHandler);
			isDisconnectHandler = (handler instanceof IDisconnectHandler);
			isIdleTimeoutHandler = (handler instanceof IIdleTimeoutHandler);
			isConnectionTimeoutHandler = (handler instanceof IConnectionTimeoutHandler);
			isLifeCycle = (handler instanceof ILifeCycle);
			
			isConnectionScoped = (handler instanceof IConnectionScoped);
			
			
			isHandlerMultithreaded = MultiplexedUtils.isHandlerMultithreaded(handler);
			
			
			if (isHandlerMultithreaded()) {
				isConnectHandlerMultithreaded = true;
				isDisconnectHandlerMultithreaded = true;
				isDataHandlerMultithreaded = true;
				isConnectionTimeoutHandlerMultithreaded = true;
				isIdleTimeoutHandlerMultithreaded = true;
			}
			
			
			if (handler instanceof IConnectHandler) {
				isConnectHandler = true;
				isConnectHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onConnect", isHandlerMultithreaded(), INonBlockingConnection.class);
			}
			
			if (handler instanceof IDataHandler) {
				isDataHandler = true;
				isDataHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onData", isHandlerMultithreaded(), INonBlockingConnection.class);
			}
		
			
			if (handler instanceof IDisconnectHandler) {
				isDisconnectHandler = true;
				isDisconnectHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onDisconnect", isHandlerMultithreaded(), INonBlockingConnection.class);
			}
			
			if (handler instanceof IIdleTimeoutHandler) {
				isIdleTimeoutHandler = true;
				isIdleTimeoutHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onIdleTimeout", isHandlerMultithreaded(), INonBlockingConnection.class);
			}

			if (handler instanceof IConnectionTimeoutHandler) {
				isConnectionTimeoutHandler = true;
				isConnectionTimeoutHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onConnectionTimeout", isHandlerMultithreaded(), INonBlockingConnection.class);			
			}
			
			
			
			if (handler instanceof IPipelineConnectHandler) {
				isPipelineConnectHandler = true;
				isConnectHandler = true;
				isConnectHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onConnect", isHandlerMultithreaded(), INonBlockingPipeline.class);
			}
			
			if (handler instanceof IPipelineDisconnectHandler) {
				isPipelineDisconnectHandler = true;
				isDisconnectHandler = true;
				isDisconnectHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onDisconnect", isHandlerMultithreaded(), INonBlockingPipeline.class);
			}

			if (handler instanceof IPipelineDataHandler) {
				isPipelineDataHandler = true;
				isDataHandler = true;
				isDataHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onData", isHandlerMultithreaded(), INonBlockingPipeline.class);
			}
			
			if (handler instanceof IPipelineIdleTimeoutHandler) {
				isPipelineIdleTimeoutHandler = true;
				isIdleTimeoutHandler = true;
				isIdleTimeoutHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onIdleTimeout", isHandlerMultithreaded(), INonBlockingPipeline.class);
			}
			
			if (handler instanceof IPipelineConnectionTimeoutHandler) {
				isPipelineConnectionTimeoutHandler = true;
				isConnectionTimeoutHandler = true;
				isConnectionTimeoutHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onConnectionTimeout", isHandlerMultithreaded(), INonBlockingPipeline.class);
			}
					
			isConnectionScoped = (handler instanceof IConnectionScoped);				
			isLifeCycle = (handler instanceof ILifeCycle);
			
			
			isNonThreaded = !isHandlerMultithreaded && !isConnectHandlerMultithreaded && 
			                !isDataHandlerMultithreaded && !isDisconnectHandlerMultithreaded &&
			                !isIdleTimeoutHandlerMultithreaded && !isConnectionTimeoutHandlerMultithreaded;
		}
		

		public boolean isPipelineConnectHandler() {
			return isPipelineConnectHandler;
		}
		
		public boolean isConnectHandler() {
			return isConnectHandler;
		}

		public boolean isDataHandler() {
			return isDataHandler;
		}

		public boolean isPipelineDataHandler() {
			return isPipelineDataHandler;
		}
		
		public boolean isDisconnectHandler() {
			return isDisconnectHandler;
		}
		
		public boolean isPipelineDisconnectHandler() {
			return isPipelineDisconnectHandler;
		}

		public boolean isIdleTimeoutHandler() {
			return isIdleTimeoutHandler;
		}

		public boolean isPipelineIdleTimeoutHandler() {
			return isPipelineIdleTimeoutHandler;
		}

		public boolean isConnectionTimeoutHandler() {
			return isConnectionTimeoutHandler;
		}
		
		public boolean isPipelineConnectionTimeoutHandler() {
			return isPipelineConnectionTimeoutHandler;
		}
		
		public boolean isLifeCycle() {
			return isLifeCycle; 
		}

		public boolean isConnectionScoped() {
			return isConnectionScoped;
		}

		public boolean isNonthreaded() {
			return isNonThreaded;
		}
		
		public boolean isHandlerMultithreaded() {
			return isHandlerMultithreaded;
		}

		public boolean isConnectHandlerMultithreaded() {
			return isConnectHandlerMultithreaded;
		}

		public boolean isDataHandlerMultithreaded() {
			return isDataHandlerMultithreaded;
		}

		public boolean isDisconnectHandlerMultithreaded() {
			return isDisconnectHandlerMultithreaded;
		}

		public boolean isIdleTimeoutHandlerMultithreaded() {
			return isIdleTimeoutHandlerMultithreaded;
		}

		public boolean isConnectionTimeoutHandlerMultithreaded() {
			return isConnectionTimeoutHandlerMultithreaded;
		}
	}
	
	
	static final class CompletionHandlerInfo {
        
        private boolean isOnWrittenMultithreaded = false;
        private boolean isOnExceptionMultithreaded = false;

        public CompletionHandlerInfo(IWriteCompletionHandler handler) {
            
            boolean isHandlerMultithreaded = isHandlerMultithreaded(handler);
            
            isOnWrittenMultithreaded = isMethodThreaded(handler.getClass(), "onWritten", isHandlerMultithreaded, int.class);
            isOnExceptionMultithreaded = isMethodThreaded(handler.getClass(), "onException", isHandlerMultithreaded, IOException.class);
        }


        public boolean isOnWrittenMultithreaded() {
            return isOnWrittenMultithreaded;
        }

        public boolean isOnExceptionMutlithreaded() {
            return isOnExceptionMultithreaded;
        }
    }
}
