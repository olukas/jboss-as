package org.jboss.as.test.integration.logging.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.integration.security.common.BlockedFileSyslogServerEventHandler;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.productivity.java.syslog4j.server.SyslogServer;
import org.productivity.java.syslog4j.server.SyslogServerIF;
import org.xnio.IoUtils;

@RunWith(Arquillian.class)
@RunAsClient
@ServerSetup(SyslogHandler2TestCase.SyslogHandlerTestCaseSetup.class)
public class SyslogHandler2TestCase {

    @ContainerResource
    private ManagementClient managementClient;

    private static final String TRACE_LOG = "trace_log_to_syslog";
    private static final String DEBUG_LOG = "debug_log_to_syslog";
    private static final String INFO_LOG = "info_log_to_syslog";
    private static final String WARN_LOG = "warn_log_to_syslog";
    private static final String ERROR_LOG = "error_log_to_syslog";
    private static final String FATAL_LOG = "fatal_log_to_syslog";
    private static final String EXPECTED_TRACE = "DEBUG";
    private static final String EXPECTED_DEBUG = "DEBUG";
    private static final String EXPECTED_INFO = "INFO";
    private static final String EXPECTED_WARN = "WARN";
    private static final String EXPECTED_ERROR = "ERROR";
    private static final String EXPECTED_FATAL = "EMERGENCY";

    private static final String FILE_NAME = "tempSyslogFile.log";
    private static File logFile;
    private static SyslogServerIF server;
    private static BlockingQueue<String> queue;
    private static final int ADJUSTED_SECOND = TimeoutUtil.adjust(1000);
    private static final String PACKAGE = SyslogHandler2TestCase.class.getPackage().getName();
    private static final Logger LOGGER = Logger.getLogger(SyslogHandlerTestCase.class.getPackage().getName());

    @Test
    public void testAuditLoggingToSyslog() throws Exception {
        deleteFile();
        LOGGER.trace(TRACE_LOG);
        queue.poll(15 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        checkLogFile(EXPECTED_TRACE, TRACE_LOG, logFile);

    }

    private void deleteFile() throws IOException {
        if (logFile.exists()) {
            logFile.delete();
            server.getConfig().removeAllEventHandlers();
            server.getConfig()
                    .addEventHandler(new BlockedFileSyslogServerEventHandler(queue, logFile.getAbsolutePath(), false));
        }
    }

    protected void checkLogFile(String expectedlevel, String pattern, File file) throws IOException {
        boolean level = false;
        boolean log = false;
        final BufferedReader reader = new BufferedReader(new FileReader(file));
        try {
            String line = reader.readLine();
            while (line != null) {
                if (line.contains(expectedlevel)) {
                    level = true;
                }
                if (line.contains(pattern)) {
                    log = true;
                }
                line = reader.readLine();
            }
        } finally {
            IoUtils.safeClose(reader);
        }
        Assert.assertTrue("message", level);
        Assert.assertTrue("message", log);

    }

    @Deployment
    public static WebArchive deployment() {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war");
        return war;
    }

    static class SyslogHandlerTestCaseSetup implements ServerSetupTask {

        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {

            final int PORT = 9176;

            logFile = new File(System.getProperty("java.io.tmpdir"), FILE_NAME);

            if (logFile.exists()) {
                logFile.delete();
            }

            // start and set syslog server
            server = SyslogServer.getInstance("udp");
            server.getConfig().setPort(PORT);
            queue = new LinkedBlockingQueue<String>();
            server.getConfig()
                    .addEventHandler(new BlockedFileSyslogServerEventHandler(queue, logFile.getAbsolutePath(), false));
            SyslogServer.getThreadedInstance("udp");

            ModelNode op;
            ModelNode result;
            op = new ModelNode();
            op.get(OP).set(ADD);
            op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            op.get(OP_ADDR).add("logger", PACKAGE);
            op.get("level").set("TRACE");
            result = managementClient.getControllerClient().execute(op);
            Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());
            op = new ModelNode();
            op.get(OP).set(ADD);
            op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            op.get(OP_ADDR).add("syslog-handler", "SYSLOG");
            op.get("level").set("TRACE");
            op.get("port").set(PORT);
            result = managementClient.getControllerClient().execute(op);
            Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());
            op = new ModelNode();
            op.get(OP).set("add-handler");
            op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            op.get(OP_ADDR).add("logger", PACKAGE);
            op.get("name").set("SYSLOG");
            result = managementClient.getControllerClient().execute(op);
            Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

            // second syslog handler for testing that lower messages then are
            // specified in level aren't logged
            // op = new ModelNode();
            // op.get(OP).set(ADD);
            // op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            // op.get(OP_ADDR).add("syslog-handler", "SYSLOG2");
            // op.get("level").set("ERROR");
            // op.get("port").set(PORT);
            // managementClient.getControllerClient().execute(op);
            // op = new ModelNode();
            // op.get(OP).set("add-handler");
            // op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            // op.get(OP_ADDR).add("logger", PACKAGE);
            // op.get("name").set("SYSLOG2");
            // managementClient.getControllerClient().execute(op);
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            // stop syslog server
            SyslogServer.shutdown();

            // remove syslog-handler SYSLOG
            ModelNode op;
            ModelNode result;
            op = new ModelNode();
            op.get(OP).set(REMOVE);
            op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            op.get(OP_ADDR).add("syslog-handler", "SYSLOG");
            result = managementClient.getControllerClient().execute(op);
            Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

            // remove syslog-handler SYSLOG2
            // op = new ModelNode();
            // op.get(OP).set(REMOVE);
            // op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            // op.get(OP_ADDR).add("syslog-handler", "SYSLOG2");
            // managementClient.getControllerClient().execute(op);

            // remove logger
            op = new ModelNode();
            op.get(OP).set(REMOVE);
            op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            op.get(OP_ADDR).add("logger", PACKAGE);
            result = managementClient.getControllerClient().execute(op);
            Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

            // delete log file
            if (logFile.exists()) {
                logFile.delete();
            }
        }

    }
}
