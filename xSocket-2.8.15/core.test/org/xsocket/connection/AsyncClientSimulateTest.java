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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;




/**
 *
 * @author yanping gao
 */
public class AsyncClientSimulateTest {


    int PACKET_LEN = 3247;
    int SEND_TIMES = 2000;

    int TEST_LOOP = 5;
    String SEND_TIMEOUT = "10000";
    
    boolean SIMULATE = true;

    class EchoHandler implements IDataHandler, IConnectHandler {
        AtomicInteger count = new AtomicInteger(0);
        Random random = new Random();

        @Execution(Execution.NONTHREADED)
        public boolean onConnect(INonBlockingConnection connection)
                throws IOException {
            // by set threshold avoid outofMemory
            // if set threshold, it will lead multi-thread writing issue

            // NOTE: different threshold will show a little diff.
            // some times, test will OK; others, test will fail.
            connection.setMaxReadBufferThreshold(8000);
            return true;
        }

        public boolean onData(INonBlockingConnection nbc) throws IOException,
                ClosedChannelException, BufferUnderflowException,
                MaxReadSizeExceededException {

            ByteBuffer[] data = nbc.readByteBufferByLength(PACKET_LEN);

            /* simulate different processing by simple sleep */
            if(SIMULATE){
                int count = this.count.addAndGet(1);
                try {
                    if (count % 50 == 0)
                        Thread.sleep(random.nextInt(80));
                    else if (count % 20 == 0)
                        Thread.sleep(random.nextInt(20));
                    else if (count % 10 == 0)
                        Thread.sleep(random.nextInt(10));
                    else if (count % 5 == 0)
                        Thread.sleep(random.nextInt(5));
                } catch (Exception e) {
                    //
                }
            }
            /* end of simulate core process */

            nbc.setAutoflush(false);
            nbc.write(data);
            nbc.flush();

            return true;
        }
    }

    @Test
    public void testSimple() throws Exception {
        Exception result = null;
        for (int i = 0; i < TEST_LOOP; i++) {
            try {
                IServer server = new Server(new EchoHandler());
                ConnectionUtils.start(server);
                Thread.sleep(500);
                asyncClient(server.getLocalPort());
                server.close();
            } catch (Exception e) {
                e.printStackTrace();
                result = e;
            }
        }
        
        if(result != null)
            throw result;
    }

    private void asyncClient(int port) throws Exception {
        final AtomicInteger count = new AtomicInteger(0);

        IDataHandler clientHandler = new IDataHandler() {
            public boolean onData(INonBlockingConnection nbc)
                    throws IOException, BufferUnderflowException,
                    MaxReadSizeExceededException {
                // read and throw it
                nbc.readBytesByLength(PACKET_LEN);

                count.addAndGet(1);
                return true;
            }
        };

        System.setProperty("org.xsocket.connection.sendFlushTimeoutMillis", SEND_TIMEOUT);
        INonBlockingConnection conn = new NonBlockingConnection("localhost",
                port, clientHandler);

        byte[] data = new byte[PACKET_LEN];
        try{
            long start = System.currentTimeMillis();
            for (int i = 0; i < SEND_TIMES; i++) {
                conn.write(data);
            }
    
            while (count.get() < SEND_TIMES)
                Thread.sleep(10);
            System.out.println("spent: " + (System.currentTimeMillis() - start));
        }finally{
            conn.close();
        }
    }
}

