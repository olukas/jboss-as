/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.logging.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletResponse;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.logging.util.AbstractLoggingTest;
import org.jboss.as.test.integration.logging.util.LoggingServlet;
import org.jboss.as.test.integration.security.common.Utils;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.osgi.metadata.ManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.productivity.java.syslog4j.SyslogConstants;
import org.productivity.java.syslog4j.server.SyslogServer;
import org.productivity.java.syslog4j.server.SyslogServerEventHandlerIF;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.productivity.java.syslog4j.server.SyslogServerIF;
import org.productivity.java.syslog4j.util.SyslogUtility;

/**
 * A SyslogHandlerTestCase for testing that logs are logged to syslog
 * 
 * @author Ondrej Lukas
 */
@RunWith(Arquillian.class)
@ServerSetup(SyslogHandlerTestCase.SyslogHandlerTestCaseSetup.class)
@RunAsClient
public class SyslogHandlerTestCase extends AbstractLoggingTest {

    private static Logger LOGGER = Logger.getLogger(SyslogHandlerTestCase.class);

    @ContainerResource
    private ManagementClient managementClient;

    private static final String TRACE_LOG = "trace";
    private static final String DEBUG_LOG = "debug";
    private static final String INFO_LOG = "info";
    private static final String WARN_LOG = "warn";
    private static final String ERROR_LOG = "error";
    private static final String FATAL_LOG = "fatal";
    private static final String EXPECTED_TRACE = "DEBUG";
    private static final String EXPECTED_DEBUG = "DEBUG";
    private static final String EXPECTED_INFO = "INFO";
    private static final String EXPECTED_WARN = "WARN";
    private static final String EXPECTED_ERROR = "ERROR";
    private static final String EXPECTED_FATAL = "EMERGENCY";
    private static final int PORT = 10514;

    private static BlockingQueue<String> queue;
    private static final int ADJUSTED_SECOND = TimeoutUtil.adjust(1000);

    /*
     * test that messages on all levels are logged
     */
    @Test
    public void testAllLevelLogs(@ArquillianResource(LoggingServlet.class) URL deployementUrl) throws Exception {
        setSyslogAttribute("level", "TRACE");
        queue.poll();
        makeLogs(deployementUrl, "Logger?text=Syslog");
        testLog(DEBUG_LOG, EXPECTED_DEBUG, "DEBUG");
        testLog(TRACE_LOG, EXPECTED_TRACE, "TRACE");
        testLog(INFO_LOG, EXPECTED_INFO, "INFO");
        testLog(WARN_LOG, EXPECTED_WARN, "WARN");
        testLog(ERROR_LOG, EXPECTED_ERROR, "ERROR");
        testLog(FATAL_LOG, EXPECTED_FATAL, "FATAL");
        String checkFinish = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNull("this message was logged but wasn't be: " + checkFinish, checkFinish);
    }

    /*
     * test that only messages on specific level or higher level are logged
     */
    @Test
    public void testLogOnSpecificLevel(@ArquillianResource(LoggingServlet.class) URL deployementUrl) throws Exception {
        setSyslogAttribute("level", "ERROR");
        queue.poll();
        makeLogs(deployementUrl, "Logger?text=Syslog");
        testLog(ERROR_LOG, EXPECTED_ERROR, "ERROR");
        testLog(FATAL_LOG, EXPECTED_FATAL, "FATAL");
        String checkFinish = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNull("this message was logged but wasn't be: " + checkFinish, checkFinish);
    }

    /*
     * test that there is no log if syslog is disabled
     */
    @Test
    public void testDisabledSyslog(@ArquillianResource(LoggingServlet.class) URL deployementUrl) throws Exception {
        setSyslogAttribute("level", "TRACE");
        setSyslogAttribute("enabled", "false");
        try {
            queue.poll();
            makeLogs(deployementUrl, "Logger?text=Syslog");
            String checkFinish = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertNull("this message was logged but wasn't be: " + checkFinish, checkFinish);
        } finally {
            setSyslogAttribute("enabled", "true");
        }
    }

    private void testLog(String level, String expectedLevel, String textLevel) throws Exception {
        String log = queue.poll(15 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(log);
        Assert.assertTrue("message on " + textLevel + " level wasn't logged, logged was: " + log,
                log.contains("Syslog: LoggingServlet is logging " + level + " message"));
        Assert.assertTrue("message on " + textLevel + " level wasn't logged on expected level, logged was: " + log,
                log.contains(expectedLevel));
    }

    private void setSyslogAttribute(String attribute, String level) throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        op.get(OP_ADDR).add(SUBSYSTEM, "logging");
        op.get(OP_ADDR).add("logging-profile", "syslog-profile");
        op.get(OP_ADDR).add("syslog-handler", "SYSLOG");
        op.get("name").set(attribute);
        op.get("value").set(level);
        Utils.applyUpdate(op, managementClient.getControllerClient());
    }

    private void makeLogs(URL deployementUrl, String spec) throws MalformedURLException, IOException {
        URL url = new URL(deployementUrl, spec);
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        int statusCode = http.getResponseCode();
        assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpServletResponse.SC_OK);
    }

    @Deployment
    public static WebArchive deployment() {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war");
        war.addClasses(LoggingServlet.class);
        war.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                ManifestBuilder builder = ManifestBuilder.newInstance();
                StringBuffer dependencies = new StringBuffer();
                builder.addManifestHeader("Dependencies", dependencies.toString());
                builder.addManifestHeader("Logging-Profile", "syslog-profile");
                return builder.openStream();
            }
        });
        return war;
    }

    static class SyslogHandlerTestCaseSetup implements ServerSetupTask {

        private static SyslogServerIF server;

        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {

            LOGGER.info("starting syslog server on port " + PORT);

            queue = new LinkedBlockingQueue<String>();
            // start and set syslog server
            try {
                server = SyslogServer.getInstance(SyslogConstants.UDP);
                server.getConfig().setPort(PORT);
                SyslogServerEventHandlerIF eventHandler = new BlockedSyslogServerEventHandler(queue);
                server.getConfig().addEventHandler(eventHandler);
                SyslogServer.getThreadedInstance(SyslogConstants.UDP);
                LOGGER.info("syslog server is running");
            } catch (Exception ex) {
                LOGGER.info("Exception during starting syslog server!");
                throw ex;
            }

            ModelNode op;
            ModelNode result;
            ModelControllerClient client = managementClient.getControllerClient();

            // create syslog-profile
            op = new ModelNode();
            op.get(OP).set(ADD);
            op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            op.get(OP_ADDR).add("logging-profile", "syslog-profile");
            Utils.applyUpdate(op, client);

            op = new ModelNode();
            op.get(OP).set(ADD);
            op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            op.get(OP_ADDR).add("logging-profile", "syslog-profile");
            op.get(OP_ADDR).add("syslog-handler", "SYSLOG");
            op.get("level").set("TRACE");
            op.get("port").set(PORT);
            op.get("enabled").set("true");
            Utils.applyUpdate(op, client);

            op = new ModelNode();
            op.get(OP).set(ADD);
            op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            op.get(OP_ADDR).add("logging-profile", "syslog-profile");
            op.get(OP_ADDR).add("root-logger", "ROOT");
            op.get("level").set("TRACE");
            ModelNode handlers = op.get("handlers");
            handlers.add("SYSLOG");
            op.get("handlers").set(handlers);
            Utils.applyUpdate(op, client);

            LOGGER.info("syslog server setup complete");
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            // stop syslog server
            LOGGER.info("stopping syslog server");
            server.shutdown();
            server.setThread(null);
            server.getConfig().removeAllEventHandlers();
            LOGGER.info("syslog server stopped");

            // remove syslog-profile
            ModelNode op = new ModelNode();
            op.get(OP).set(REMOVE);
            op.get(OP_ADDR).add(SUBSYSTEM, "logging");
            op.get(OP_ADDR).add("logging-profile", "syslog-profile");
            Utils.applyUpdate(op, managementClient.getControllerClient());

            LOGGER.info("syslog server shutdown complete");
        }
    }

    private static class BlockedSyslogServerEventHandler implements SyslogServerEventHandlerIF {

        private static final long serialVersionUID = -3814601581286016000L;
        private BlockingQueue<String> queue = new LinkedBlockingQueue<String>();

        public BlockedSyslogServerEventHandler(BlockingQueue<String> queue) throws IOException {
            this.queue = queue;
        }

        public void event(SyslogServerIF syslogServer, SyslogServerEventIF event) {
            String level = SyslogUtility.getLevelString(event.getLevel());
            queue.offer(level + " " + event.getMessage());
        }
    }
}
