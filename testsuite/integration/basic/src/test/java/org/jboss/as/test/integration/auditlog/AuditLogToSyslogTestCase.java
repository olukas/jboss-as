package org.jboss.as.test.integration.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT_LOG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOGGER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOG_READ_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSLOG_FORMAT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSLOG_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.security.common.BlockedFileSyslogServerEventHandler;
import org.jboss.as.test.integration.security.common.Utils;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.productivity.java.syslog4j.server.SyslogServer;
import org.productivity.java.syslog4j.server.SyslogServerIF;
import org.xnio.IoUtils;

/**
 * Test that syslog-handler logs in Audit Log
 * 
 * @author: Ondrej Lukas
 */
public abstract class AuditLogToSyslogTestCase {

    @ContainerResource
    private ManagementClient managementClient;

    private static final String FILE_NAME = "tempSyslogFile.log";
    protected static File logFile;
    protected static SyslogServerIF server;
    protected static BlockingQueue<String> queue;
    private static final int ADJUSTED_SECOND = TimeoutUtil.adjust(1000);
    private static List<Long> properties = new ArrayList<Long>();
    protected static String SYSLOG_HANDLER_NAME = "audit-test-syslog-handler";

    protected static final PathAddress AUDIT_LOG_ADDRESS = PathAddress.pathAddress().append(CORE_SERVICE, MANAGEMENT)
            .append(ACCESS, AUDIT);
    protected static final PathAddress AUDIT_LOG_LOGGER_ADDR = AUDIT_LOG_ADDRESS.append(LOGGER, AUDIT_LOG);
    protected static final PathAddress AUDIT_SYSLOG_HANDLER_ADDR = AUDIT_LOG_ADDRESS
            .append(SYSLOG_HANDLER, SYSLOG_HANDLER_NAME);
    protected static final PathAddress AUDIT_LOG_LOGGER_SYSLOG_HANDLER_ADDR = AUDIT_LOG_LOGGER_ADDR.append(HANDLER,
            SYSLOG_HANDLER_NAME);

    @Test
    public void testAuditLoggingToSyslog() throws Exception {

        if (logFile.exists()) {
            logFile.delete();
            server.getConfig().removeAllEventHandlers();
            server.getConfig()
                    .addEventHandler(new BlockedFileSyslogServerEventHandler(queue, logFile.getAbsolutePath(), false));
        }

        Assert.assertEquals(0, readFile(logFile));
        makeOneLog();
        queue.poll(3 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertEquals("Audit log is logged to syslog even it isn't enabled", 0, readFile(logFile));
        try {
            enableOrDisableLog(true);
            queue.poll(15 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertEquals("Enabled message of audit log isn't logged to syslog", 1, readFile(logFile));
            makeOneLog();
            queue.poll(15 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertEquals("Audit log isn't logged to syslog", 2, readFile(logFile));
        } finally {
            enableOrDisableLog(false);
        }
        if (logFile.exists()) {
            logFile.delete();
            server.getConfig().removeAllEventHandlers();
            server.getConfig()
                    .addEventHandler(new BlockedFileSyslogServerEventHandler(queue, logFile.getAbsolutePath(), false));
        }
        makeOneLog();
        queue.poll(3 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertEquals("Audit log is logged to syslog even it was disabled", 0, readFile(logFile));
    }

    private void enableOrDisableLog(boolean value) throws Exception {
        ModelNode op = Util.getWriteAttributeOperation(AUDIT_LOG_LOGGER_ADDR, ENABLED, value);
        Utils.applyUpdate(op, managementClient.getControllerClient());
    }

    private void makeOneLog() throws Exception {
        long timeStamp = System.currentTimeMillis();
        properties.add(Long.valueOf(timeStamp));
        ModelNode op = Util.createAddOperation(PathAddress.pathAddress().append(SYSTEM_PROPERTY, Long.toString(timeStamp)));
        op.get(NAME).set(NAME);
        op.get(VALUE).set("someValue");
        Utils.applyUpdate(op, managementClient.getControllerClient());
    }

    private final Pattern DATE_STAMP_PATTERN = Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d - \\{");

    protected int readFile(File file) throws IOException {
        int counter = 0;
        final BufferedReader reader = new BufferedReader(new FileReader(file));
        try {
            String line = reader.readLine();
            while (line != null) {
                if (DATE_STAMP_PATTERN.matcher(line).find()) {
                    counter++;
                }
                line = reader.readLine();
            }
        } finally {
            IoUtils.safeClose(reader);
        }
        return counter;
    }

    @Deployment
    public static WebArchive deployment() {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war");
        return war;
    }

    abstract static class AuditLogToSyslogSetup implements ServerSetupTask {

        private static final String FORMATTER = "formatter";
        private static final String JSON_FORMATTER = "json-formatter";

        protected abstract void setupSyslogServer() throws Exception;

        protected abstract ModelNode addProtocol();

        protected List<ModelNode> addProtocolSettings() {
            return null;
        }

        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {

            logFile = new File(System.getProperty("java.io.tmpdir"), FILE_NAME);

            if (logFile.exists()) {
                logFile.delete();
            }

            setupSyslogServer();

            ModelNode op;

            // set logging to syslog
            final ModelNode compositeOp = new ModelNode();
            compositeOp.get(OP).set(COMPOSITE);
            compositeOp.get(OP_ADDR).setEmptyList();
            ModelNode steps = compositeOp.get(STEPS);
            op = Util.createAddOperation(AUDIT_SYSLOG_HANDLER_ADDR);
            op.get(FORMATTER).set(JSON_FORMATTER);
            op.get(SYSLOG_FORMAT).set("RFC5424");
            steps.add(op);
            steps.add(addProtocol());
            Utils.applyUpdate(compositeOp, managementClient.getControllerClient());

            List<ModelNode> protocolSettings = addProtocolSettings();
            if (protocolSettings != null) {
                Utils.applyUpdates(protocolSettings, managementClient.getControllerClient());
            }

            op = Util.createAddOperation(AUDIT_LOG_LOGGER_SYSLOG_HANDLER_ADDR);
            Utils.applyUpdate(op, managementClient.getControllerClient());

            op = Util.getWriteAttributeOperation(AUDIT_LOG_LOGGER_ADDR, LOG_READ_ONLY, false);
            Utils.applyUpdate(op, managementClient.getControllerClient());

        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            // stop syslog server
            SyslogServer.shutdown();
            server.setThread(null);
            server.getConfig().removeAllEventHandlers();

            for (Long property : properties) {
                Utils.applyUpdate(
                        Util.createRemoveOperation(PathAddress.pathAddress().append(SYSTEM_PROPERTY, Long.toString(property))),
                        managementClient.getControllerClient());
            }

            Utils.applyUpdate(Util.createRemoveOperation(AUDIT_LOG_LOGGER_SYSLOG_HANDLER_ADDR),
                    managementClient.getControllerClient());

            Utils.applyUpdate(Util.createRemoveOperation(AUDIT_SYSLOG_HANDLER_ADDR), managementClient.getControllerClient());

            Utils.applyUpdate(Util.getWriteAttributeOperation(AUDIT_LOG_LOGGER_ADDR, LOG_READ_ONLY, false),
                    managementClient.getControllerClient());

            if (logFile.exists()) {
                logFile.delete();
            }

        }

    }

}
