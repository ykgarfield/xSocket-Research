/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
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
package org.xsocket.connection;


import java.io.Closeable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;




/**
 * The connector is responsible to connect a peer<br><br>
 *
 * @author grro@xsocket.org
 */
final class IoConnector extends MonitoredSelector implements Runnable, Closeable {

    private static final Logger LOG = Logger.getLogger(IoConnector.class.getName());
    
    static final String CONNECTOR_PREFIX = "xConnector";

    // is open flag
    private final AtomicBoolean isOpen = new AtomicBoolean(true);
    
    
    // timeout support
    private static final long DEFAULT_WATCHDOG_PERIOD_MILLIS =  1L * 60L * 1000L;
    private long watchDogPeriodMillis = DEFAULT_WATCHDOG_PERIOD_MILLIS;
    private final TimeoutCheckTask timeoutCheckTask = new TimeoutCheckTask();
    private TimerTask watchDogTask;

    
    // selector 
    private final Selector selector;
    private final String name;
    
    // queues
    private final ConcurrentLinkedQueue<Runnable> taskQueue = new ConcurrentLinkedQueue<Runnable>();

    

    public IoConnector(String name) {
        this.name = CONNECTOR_PREFIX + "#" + name;
        
        try {
            selector = Selector.open();
        } catch (IOException ioe) {
            String text = "exception occured while opening selector. Reason: " + ioe.toString();
            LOG.severe(text);
            throw new RuntimeException(text, ioe);
        }     
    }

    
    @Override
    void reinit() throws IOException {
        // reinit is not supported
    }
    

    /**
     * {@inheritDoc}
     */
    public void run() {

        // set thread name and attach dispatcher id to thread
        Thread.currentThread().setName(name);

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("selector " + name + " listening ...");
        }

        
        int handledTasks = 0;

        while(isOpen.get()) {
            try {
                handledTasks = performTaskQueue();
                
                int eventCount = selector.select(1000);
                
                if (eventCount > 0) {
                    handleConnect();
                    
                } else {
                	checkForLooping(handledTasks);
                }

            } catch (Exception e) {
                // eat and log exception
                LOG.warning("[" + Thread.currentThread().getName() + "] exception occured while processing. Reason " + DataConverter.toString(e));
            }
        }



        try {
            selector.close();
        } catch (Exception e) {
            // eat and log exception
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("error occured by close selector within tearDown " + DataConverter.toString(e));
            }
        }
    }
    
    
    @Override
    String printRegistered() {
        StringBuilder sb = new StringBuilder();
        
        Set<SelectionKey> keys = new HashSet<SelectionKey>();
        keys.addAll(selector.keys());
        
        for (SelectionKey key : keys) {
            sb.append(ConnectionUtils.printSelectionKey(key) + "\r\n");
        }
            
        return sb.toString();
    }
    
    @Override
    int getNumRegisteredHandles() {
    	return selector.keys().size();
    }

 
    public void close() throws IOException {
    	isOpen.set(false);
    }
    
    
    private int performTaskQueue() throws IOException {

        int handledTasks = 0;

        while (true) {
            Runnable task = taskQueue.poll();

            if (task == null) {
                return handledTasks;

            } else {
                task.run();
                handledTasks++;
            }
        }
    }




    private void handleConnect() {
        Set<SelectionKey> selectedEventKeys = selector.selectedKeys();

        Iterator<SelectionKey> it = selectedEventKeys.iterator();

        while (it.hasNext()) {
            SelectionKey eventKey = it.next();
            it.remove();

            RegisterTask registerTask = (RegisterTask) eventKey.attachment();

            if (eventKey.isValid() && eventKey.isConnectable()) {
                
                try {
                    boolean isConnected = ((SocketChannel) eventKey.channel()).finishConnect();
                    if (isConnected) {
                    	eventKey.cancel();
                    	registerTask.callback.onConnectionEstablished();
                    }
                } catch (IOException ioe) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("error occured by performing handling connect event " + ioe.toString());
                    }
                    try {
                        eventKey.channel().close();
                    } catch (IOException e) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("error occured by closing channel " + e.toString());
                        }
                    }
                    
                    registerTask.callback.onConnectError(ioe);
                }
            }
        }
    }

      
    public void connectAsync(SocketChannel channel, InetSocketAddress remoteAddress, long connectTimeoutMillis, IIoConnectorCallback callback) throws IOException {
        assert (channel.isOpen());

        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("try to connect " + remoteAddress + " (connect timeout " + DataConverter.toFormatedDuration(connectTimeoutMillis) + ")");
        }
        

        RegisterTask registerTask = new RegisterTask(channel, callback, remoteAddress, System.currentTimeMillis() + (long) connectTimeoutMillis);
        addToTaskQueue(registerTask);
                
        if (connectTimeoutMillis >= 1000) {
            updateTimeoutCheckPeriod(connectTimeoutMillis / 5);
        } else {
            updateTimeoutCheckPeriod(200);
        }
    }
    
    
    private void addToTaskQueue(Runnable task) {
        taskQueue.add(task);
        selector.wakeup();
    }
    
    
    
    private final class RegisterTask implements Runnable {

        private final SocketChannel channel;
        private final IIoConnectorCallback callback;
        private final InetSocketAddress remoteAddress;
        private final long expireTime;
        
        private SelectionKey selectionKey;
        
        public RegisterTask(SocketChannel channel, IIoConnectorCallback callback, InetSocketAddress remoteAddress, long expireTime) throws IOException {
            this.channel = channel;
            this.callback = callback;
            this.remoteAddress = remoteAddress;
            this.expireTime = expireTime;
            
            channel.configureBlocking(false);
        }
        
        boolean isExpired(long currentTime) {
            return (currentTime > expireTime); 
        }
        
        
        public void run() {
          
            selectionKey = null;
            try {
                selectionKey = channel.register(selector, SelectionKey.OP_CONNECT);
                selectionKey.attach(this);
                connect(channel, remoteAddress);
            
            } catch (IOException ioe) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("error occured by registering channel " + channel + " reason " + ioe.toString());
                }
                
                if (selectionKey != null) {
                    selectionKey.cancel();
                }
                
                try {
                    channel.close();
                } catch (IOException e) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("error occured by closing channel " + e.toString());
                    }
                }
                
                
                callback.onConnectError(ioe);
            }
        }
        
        private void connect(SocketChannel channel, InetSocketAddress remoteAddress) throws IOException {
            try {
                channel.connect(remoteAddress);
            } catch (UnresolvedAddressException uae) {
                throw new IOException("connecting " + remoteAddress + " failed " + uae.toString());
            } 
        }
    }
    
    
    
    void updateTimeoutCheckPeriod(long requiredMinPeriod) {

        synchronized (this) {

            // if non watchdog task already exists and the required period is smaller than current one -> return
            if ((watchDogTask != null) && (watchDogPeriodMillis <= requiredMinPeriod)) {
                return;
            }
    
            // set watch dog period
            watchDogPeriodMillis = requiredMinPeriod;
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("update watchdog period " + DataConverter.toFormatedDuration(watchDogPeriodMillis));
            }
    
            // if watchdog task task already exits -> terminate it
            if (watchDogTask != null) {
                watchDogTask.cancel();
                watchDogTask = null;
            }
            
            // create and run new watchdog task
            watchDogTask = new TimerTask() {
        
                public void run() {
                    addToTaskQueue(timeoutCheckTask);
                }
            };
                
                
            IoProvider.getTimer().schedule(watchDogTask, watchDogPeriodMillis, watchDogPeriodMillis);
        }
    }   
    
    
    
    private final class TimeoutCheckTask implements Runnable {
        
        public void run() {
            
            try {
                long currentMillis = System.currentTimeMillis();
                
                for (SelectionKey selectionKey : selector.keys()) {
                    RegisterTask registerTask = (RegisterTask) selectionKey.attachment();
                    if (registerTask.isExpired(currentMillis)) {
                        selectionKey.cancel();
                        registerTask.callback.onConnectTimeout();
                    }
                }
            } catch (Exception e) {
                // eat and log exception
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("error occured by performing timeout check task " + e.toString());
                }
            }
        }
    }    
}
