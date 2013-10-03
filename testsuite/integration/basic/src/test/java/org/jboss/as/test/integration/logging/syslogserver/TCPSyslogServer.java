package org.jboss.as.test.integration.logging.syslogserver;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import org.productivity.java.syslog4j.SyslogRuntimeException;
import org.productivity.java.syslog4j.server.impl.net.tcp.TCPNetSyslogServer;

public class TCPSyslogServer extends TCPNetSyslogServer {

    public void run() {
        try {
            this.serverSocket = createServerSocket();
            this.shutdown = false;

        } catch (SocketException se) {
            throw new SyslogRuntimeException(se);

        } catch (IOException ioe) {
            throw new SyslogRuntimeException(ioe);
        }

        while (!this.shutdown) {
            try {
                Socket socket = this.serverSocket.accept();

                TCPNetSyslogSocketHandler handler = new TCPNetSyslogSocketHandler(this.sockets, this, socket);

                Thread t = new Thread(handler);

                t.start();

            } catch (SocketException se) {
                if ("Socket closed".equals(se.getMessage())) {
                    this.shutdown = true;
                    System.out.println("ERRORRRRRRRRRRRRRRR");

                } else {
                    System.out.println("ERRORRRRRRRRRRRRRRR");
                }

            } catch (IOException ioe) {
                System.err.println(ioe);

                try {
                    Thread.sleep(500);

                } catch (InterruptedException ie) {
                    System.out.println("ERRORRRRRRRRRRRRRRR");
                }
            }
        }
    }

}
