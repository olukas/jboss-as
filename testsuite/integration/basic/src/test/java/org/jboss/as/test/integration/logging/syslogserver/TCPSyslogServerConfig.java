package org.jboss.as.test.integration.logging.syslogserver;

import org.productivity.java.syslog4j.server.impl.net.tcp.TCPNetSyslogServerConfig;

public class TCPSyslogServerConfig extends TCPNetSyslogServerConfig {

    public Class getSyslogServerClass() {
        return TCPSyslogServer.class;
    }
}
