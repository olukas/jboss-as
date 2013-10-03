package org.jboss.as.test.integration.logging.syslogserver;

import java.io.IOException;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

import org.productivity.java.syslog4j.SyslogRuntimeException;

public class SSLTCPSyslogServer extends TCPSyslogServer {
    public void initialize() throws SyslogRuntimeException {
        super.initialize();

        SSLTCPSyslogServerConfig sslTcpSyslogServerConfig = (SSLTCPSyslogServerConfig) this.tcpNetSyslogServerConfig;

        String keyStore = sslTcpSyslogServerConfig.getKeyStore();

        if (keyStore != null && !"".equals(keyStore.trim())) {
            System.setProperty("javax.net.ssl.keyStore", keyStore);
        }

        String keyStorePassword = sslTcpSyslogServerConfig.getKeyStorePassword();

        if (keyStorePassword != null && !"".equals(keyStorePassword.trim())) {
            System.setProperty("javax.net.ssl.keyStorePassword", keyStorePassword);
        }

        String trustStore = sslTcpSyslogServerConfig.getTrustStore();

        if (trustStore != null && !"".equals(trustStore.trim())) {
            System.setProperty("javax.net.ssl.trustStore", trustStore);
        }

        String trustStorePassword = sslTcpSyslogServerConfig.getTrustStorePassword();

        if (trustStorePassword != null && !"".equals(trustStorePassword.trim())) {
            System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
        }
    }

    protected ServerSocketFactory getServerSocketFactory() throws IOException {
        ServerSocketFactory serverSocketFactory = SSLServerSocketFactory.getDefault();

        return serverSocketFactory;
    }

}
