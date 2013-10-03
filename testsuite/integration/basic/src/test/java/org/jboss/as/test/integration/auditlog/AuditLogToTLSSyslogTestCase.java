package org.jboss.as.test.integration.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLIENT_CERT_STORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROTOCOL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSLOG_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TLS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.logging.syslogserver.SSLTCPSyslogServerConfig;
import org.jboss.as.test.integration.security.common.BlockedFileSyslogServerEventHandler;
import org.jboss.dmr.ModelNode;
import org.junit.runner.RunWith;
import org.productivity.java.syslog4j.server.SyslogServer;

@RunWith(Arquillian.class)
@RunAsClient
@ServerSetup(AuditLogToTLSSyslogTestCase.AuditLogToTLSSyslogTestCaseSetup.class)
public class AuditLogToTLSSyslogTestCase extends AuditLogToSyslogTestCase {

    static class AuditLogToTLSSyslogTestCaseSetup extends AuditLogToSyslogTestCase.AuditLogToSyslogSetup {

        final int SYSLOG_PORT = 9178;
        private static String PASSWORD = "test123";

        private static final PathAddress AUDIT_SYSLOG_TLS_ADDR = PathAddress.pathAddress().append(CORE_SERVICE, MANAGEMENT)
                .append(ACCESS, AUDIT).append(SYSLOG_HANDLER, SYSLOG_HANDLER_NAME).append(PROTOCOL, TLS);

        private static final String pathToCertDir = "/home/olukas/workspace/bash/key/";

        @Override
        protected void setupSyslogServer() throws Exception {
            // clear created server instances (TCP/UDP)
            SyslogServer.shutdown();
            // start and set syslog server
            // SSLTCPNetSyslogServerConfig config = new SSLTCPNetSyslogServerConfig();
            SSLTCPSyslogServerConfig config = new SSLTCPSyslogServerConfig();
            config.setPort(SYSLOG_PORT);
            config.setUseStructuredData(true);
            config.setKeyStore(pathToCertDir + "server.keystore");
            config.setKeyStorePassword(PASSWORD);
            config.setTrustStore(pathToCertDir + "server.truststore");
            config.setTrustStorePassword(PASSWORD);
            queue = new LinkedBlockingQueue<String>();
            config.addEventHandler(new BlockedFileSyslogServerEventHandler(queue, logFile.getAbsolutePath(), false));
            server = SyslogServer.createInstance("tls", config);
            // start syslog server
            SyslogServer.getThreadedInstance("tls");
        }

        @Override
        protected ModelNode addProtocol() {
            ModelNode op = Util.createAddOperation(AUDIT_SYSLOG_TLS_ADDR);
            op.get(PORT).set(SYSLOG_PORT);
            op.get(HOST).set("localhost");
            return op;
        }

        protected List<ModelNode> addProtocolSettings() {
            List<ModelNode> ops = new ArrayList<ModelNode>();
            ModelNode op1 = Util.createAddOperation(AUDIT_SYSLOG_TLS_ADDR.append(AUTHENTICATION, TRUSTSTORE));
            op1.get("keystore-password").set(PASSWORD);
            op1.get("keystore-path").set(pathToCertDir + "client.truststore");
            // ops.add(op1);
            ModelNode op2 = Util.createAddOperation(AUDIT_SYSLOG_TLS_ADDR.append(AUTHENTICATION, CLIENT_CERT_STORE));
            op2.get("keystore-password").set(PASSWORD);
            op2.get("keystore-path").set(pathToCertDir + "client.keystore");
            // ops.add(op2);
            return ops;
        }

    }
}
