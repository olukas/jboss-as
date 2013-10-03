package org.jboss.as.test.integration.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROTOCOL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UDP;

import java.util.concurrent.LinkedBlockingQueue;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.logging.syslogserver.UDPSyslogServerConfig;
import org.jboss.as.test.integration.security.common.BlockedFileSyslogServerEventHandler;
import org.jboss.dmr.ModelNode;
import org.junit.runner.RunWith;
import org.productivity.java.syslog4j.SyslogConstants;
import org.productivity.java.syslog4j.server.SyslogServer;

@RunWith(Arquillian.class)
@RunAsClient
@ServerSetup(AuditLogToUDPSyslogTestCase.AuditLogToUDPSyslogTestCaseSetup.class)
public class AuditLogToUDPSyslogTestCase extends AuditLogToSyslogTestCase {

    static class AuditLogToUDPSyslogTestCaseSetup extends AuditLogToSyslogTestCase.AuditLogToSyslogSetup {

        final int SYSLOG_PORT = 9176;

        @Override
        protected void setupSyslogServer() throws Exception {
            // clear created server instances (TCP/UDP)
            SyslogServer.shutdown();
            // start and set syslog server
            final UDPSyslogServerConfig config = new UDPSyslogServerConfig();
            config.setPort(SYSLOG_PORT);
            config.setUseStructuredData(true);
            queue = new LinkedBlockingQueue<String>();
            config.addEventHandler(new BlockedFileSyslogServerEventHandler(queue, logFile.getAbsolutePath(), false));
            server = SyslogServer.createInstance(UDP, config);
            // start syslog server
            SyslogServer.getThreadedInstance(SyslogConstants.UDP);
        }

        @Override
        protected ModelNode addProtocol() {
            ModelNode op = Util.createAddOperation(AUDIT_SYSLOG_HANDLER_ADDR.append(PROTOCOL, UDP));
            op.get(PORT).set(SYSLOG_PORT);
            op.get(HOST).set("localhost");
            return op;
        }

    }

}
