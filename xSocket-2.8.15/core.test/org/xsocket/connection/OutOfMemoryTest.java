package org.xsocket.connection;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Ignore;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;



public final class OutOfMemoryTest  {

    public static final String  PROTOCOL_DELIMITER  = "\0";
    

    @Ignore
    @Test
    public void testClientServerCom() throws Exception {
        
        IServer serv = null;

        System.out.println("Starting test server..");
        serv = new Server("127.0.0.1", 10101, new ServerHandler());
        serv.start();

        ConcurrentHashMap<String, INonBlockingConnection> clients = new ConcurrentHashMap<String, INonBlockingConnection>();

        for (int i = 1; i <= 10; i++) {
            INonBlockingConnection con = new NonBlockingConnection(serv.getLocalAddress(), serv.getLocalPort(),
                    new ClientHandler(i), true, 1000);

            con.write("Hey Dude #" + i + PROTOCOL_DELIMITER);

            clients.put(con.getId(), con);
        }

        while (clients.size() > 0) {
            
            for (String conId : clients.keySet()) {
                INonBlockingConnection con = clients.get(conId);
                ClientHandler handler = (ClientHandler) con.getHandler();

                if (handler.hasGotEcho()) {
                    System.out.println("[" + handler.getClientId() + "] Removing client {} ");
                    INonBlockingConnection conRemoved = clients.remove(conId);
                    conRemoved.close();
                } else {
                    System.out.println("[" + handler.getClientId() + "] Client still waiting for echo.. ");
                }
            }

            Thread.sleep(2000);
        }

        serv.close();
    }

    
    private class ServerHandler implements IHandler, IConnectHandler, IDataHandler,
            IDisconnectHandler {

        public boolean onConnect(INonBlockingConnection con) throws IOException,
                BufferUnderflowException, MaxReadSizeExceededException {
            System.out.println("[" + con.getId() + "] Server Connected ");

            con.write("Welcome " + con.getId() + PROTOCOL_DELIMITER);
            
            return true;
        }

        public boolean onData(INonBlockingConnection con) throws IOException,
                BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
            String read = con.readStringByDelimiter(PROTOCOL_DELIMITER);
            System.out.println("[" + con.getId() + "] Server Data Received read: " + read);

            Random rand = new Random();
            long sleepTime = (long) (1000 * rand.nextDouble());
            try {
                System.out.println("[" + con.getId() + "] Server Thread. Sleeping for " + sleepTime + " ms");
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            System.out.println("[" + con.getId() + "] Server Thread Filling Large StringBuffer");
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < 1000000; i++) {
                sb.append(i);
            }
            sb.setLength(0);

            con.write("Echo " + read + PROTOCOL_DELIMITER);

            System.out.println("[" + con.getId() + "] Server Sent Echo read " + read);
            
            return true;
        }

        
        public boolean onDisconnect(INonBlockingConnection con) throws IOException {
            System.out.println("[" + con.getId() + "] Server Disconnected");
            return true;
        }
    }

    
    
    private class ClientHandler implements IConnectHandler, IDataHandler, IDisconnectHandler {
        private int     clientId    = 0;
        private boolean gotEcho     = false;

        public ClientHandler(int clientId) {
            this.clientId = clientId;
        }

        public int getClientId() {
            return clientId;
        }

        public boolean hasGotEcho() {
            return gotEcho;
        }

        public void setGotEcho(boolean gotEcho) {
            this.gotEcho = gotEcho;
        }

        public boolean onConnect(INonBlockingConnection con) throws IOException,
                BufferUnderflowException, MaxReadSizeExceededException {
            System.out.println("[" + con.getId() + "] Client Connected");
            return true;
        }

        public boolean onData(INonBlockingConnection con) throws IOException,
                BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
            String read = con.readStringByDelimiter(PROTOCOL_DELIMITER);

            System.out.println("[" + con.getId() + "] Client Data Received read " + read);

            if (read.contains("Echo ")) {
                setGotEcho(true);
            }

            return true;
        }

        public boolean onDisconnect(INonBlockingConnection con) throws IOException {
            System.out.println("[" + con.getId() + "] Client Disconnected");
            
            return true;
        }
    }	
}
