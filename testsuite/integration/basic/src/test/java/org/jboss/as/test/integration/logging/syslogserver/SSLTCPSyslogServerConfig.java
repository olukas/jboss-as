package org.jboss.as.test.integration.logging.syslogserver;

import org.productivity.java.syslog4j.server.impl.net.tcp.ssl.SSLTCPNetSyslogServerConfig;

public class SSLTCPSyslogServerConfig extends SSLTCPNetSyslogServerConfig {

    public Class getSyslogServerClass() {
        return SSLTCPSyslogServer.class;
    }

}
