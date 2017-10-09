/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat Middleware LLC, and individual contributors
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
package org.wildfly.test.manual.elytron.seccontext;

import java.io.IOException;
import java.net.URL;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import static org.jboss.as.test.integration.security.common.Utils.REDIRECT_STRATEGY;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import static org.wildfly.test.manual.elytron.seccontext.AbstractSecurityContextPropagationTestBase.server1;
import static org.wildfly.test.manual.elytron.seccontext.SeccontextUtil.SERVER1_BACKUP;
import static org.wildfly.test.manual.elytron.seccontext.SeccontextUtil.WAR_ENTRY_SERVLET_FORM;

/**
 *
 * @author Josef Cacek
 */
public abstract class AbstractHASecurityContextPropagationTestCase extends AbstractSecurityContextPropagationTestBase {

    private static final ServerHolder server1backup = new ServerHolder(SERVER1_BACKUP, TestSuiteEnvironment.getServerAddress(),
            2000);

    /**
     * Start server1backup.
     */
    @Before
    public void startServer1backup() throws CommandLineException, IOException, MgmtOperationException {
        server1backup.resetContainerConfiguration(new ServerConfigurationBuilder()
                .withDeployments(WAR_ENTRY_SERVLET_FORM + "backup")
                .build());
    }

    /**
     * Shut down server1backup.
     */
    @AfterClass
    public static void shutdownServer1backup() throws IOException {
        server1backup.shutDown();
    }

    /**
     * Verifies, the distributable web-app with FORM authentication supports SSO out of the box.
     *
     * <pre>
     * When: HTTP client calls WhoAmIServlet as "admin" (using FORM authn) on first cluster node and then
     *       it calls WhoAmIServlet (without authentication needed) on the second cluster node
     * Then: the call to WhoAmIServlet on second node (without authentication) passes and returns "admin"
     *       (i.e. SSO works with FORM authentication)
     * </pre>
     */
    @Test
    public void testServletSso() throws Exception {
        final URL whoamiUrl = new URL(
                server1.getApplicationHttpUrl() + "/" + WAR_ENTRY_SERVLET_FORM + WhoAmIServlet.SERVLET_PATH);
        final URL whoamiBackupUrl = new URL(
                server1backup.getApplicationHttpUrl() + "/" + WAR_ENTRY_SERVLET_FORM + WhoAmIServlet.SERVLET_PATH);

        try (final CloseableHttpClient httpClient = HttpClientBuilder.create().setRedirectStrategy(REDIRECT_STRATEGY).build()) {
            assertEquals("Unexpected result from WhoAmIServlet", "admin",
                    doHttpRequestFormAuthn(httpClient, whoamiUrl, true, "admin", "admin", SC_OK));
            assertEquals("Unexpected result from WhoAmIServlet (backup-server)", "admin",
                    doHttpRequest(httpClient, whoamiBackupUrl, SC_OK));
        }
    }

    /**
     * Verifies, the credential forwarding works within clustered SSO (FORM authentication). This simulates failover on
     * distributed web application (e.g. when load balancer is used).
     *
     * <pre>
     * When: HTTP client calls WhoAmIServlet as "admin" (using FORM authn) on second cluster node and then
     *       it calls EntryServlet (without authentication needed) on the first cluster node;
     *       the EntryServlet uses Elytron API to forward authentication (credentials) to call remote WhoAmIBean
     * Then: the calls pass and WhoAmIBean returns "admin" username
     * </pre>
     */
    @Test
    @Ignore("JBEAP-13217")
    public void testServletSsoPropagation() throws Exception {
        final URL entryServletUrl = getEntryServletUrl(WAR_ENTRY_SERVLET_FORM, null, null,
                ReAuthnType.FORWARDED_AUTHENTICATION);
        final URL whoamiUrl = new URL(
                server1backup.getApplicationHttpUrl() + "/" + WAR_ENTRY_SERVLET_FORM + WhoAmIServlet.SERVLET_PATH);

        try (final CloseableHttpClient httpClient = HttpClientBuilder.create().setRedirectStrategy(REDIRECT_STRATEGY).build()) {
            assertEquals("Unexpected result from WhoAmIServlet (backup-server)", "admin",
                    doHttpRequestFormAuthn(httpClient, whoamiUrl, true, "admin", "admin", SC_OK));
            assertEquals("Unexpected result from EntryServlet", "admin", doHttpRequest(httpClient, entryServletUrl, SC_OK));
        }
    }
}
