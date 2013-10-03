package org.jboss.as.test.integration.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROTOCOL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TCP;

import java.util.concurrent.LinkedBlockingQueue;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.logging.syslogserver.TCPSyslogServerConfig;
import org.jboss.as.test.integration.security.common.BlockedFileSyslogServerEventHandler;
import org.jboss.dmr.ModelNode;
import org.junit.runner.RunWith;
import org.productivity.java.syslog4j.server.SyslogServer;

@RunWith(Arquillian.class)
@RunAsClient
@ServerSetup(AuditLogToTCPSyslogTestCase.AuditLogToTCPSyslogTestCaseSetup.class)
public class AuditLogToTCPSyslogTestCase extends AuditLogToSyslogTestCase {

    static class AuditLogToTCPSyslogTestCaseSetup extends AuditLogToSyslogTestCase.AuditLogToSyslogSetup {

        final int SYSLOG_PORT = 9177;

        @Override
        protected void setupSyslogServer() throws Exception {
            // clear created server instances (TCP/UDP)
            SyslogServer.shutdown();
            // start and set syslog server
            // TCPNetSyslogServerConfig config = new TCPNetSyslogServerConfig();
            TCPSyslogServerConfig config = new TCPSyslogServerConfig();
            config.setPort(SYSLOG_PORT);
            config.setUseStructuredData(true);
            queue = new LinkedBlockingQueue<String>();
            config.addEventHandler(new BlockedFileSyslogServerEventHandler(queue, logFile.getAbsolutePath(), false));
            server = SyslogServer.createInstance(TCP, config);
            // start syslog server
            SyslogServer.getThreadedInstance("tcp");
        }

        @Override
        protected ModelNode addProtocol() {
            ModelNode op = Util.createAddOperation(AUDIT_SYSLOG_HANDLER_ADDR.append(PROTOCOL, TCP));
            op.get(PORT).set(SYSLOG_PORT);
            op.get(HOST).set("localhost");
            return op;
        }

    }

}
