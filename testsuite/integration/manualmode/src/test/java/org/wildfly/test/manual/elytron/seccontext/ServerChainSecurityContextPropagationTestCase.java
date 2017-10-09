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
import java.util.concurrent.Callable;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import static org.wildfly.test.manual.elytron.seccontext.AbstractSecurityContextPropagationTestBase.server1;
import static org.wildfly.test.manual.elytron.seccontext.SeccontextUtil.FIRST_SERVER_CHAIN_EJB;
import static org.wildfly.test.manual.elytron.seccontext.SeccontextUtil.JAR_ENTRY_EJB_SERVER_CHAIN;
import static org.wildfly.test.manual.elytron.seccontext.SeccontextUtil.SERVER3;
import static org.wildfly.test.manual.elytron.seccontext.SeccontextUtil.WAR_WHOAMI_SERVER_CHAIN;

/**
 *
 * @author olukas
 */
public class ServerChainSecurityContextPropagationTestCase extends AbstractSecurityContextPropagationTestBase {

    private static final ServerHolder server3 = new ServerHolder(SERVER3, TestSuiteEnvironment.getServerAddressNode1(), 250);

    @Before
    public void startServer3() throws CommandLineException, IOException, MgmtOperationException {
        server3.resetContainerConfiguration(new ServerConfigurationBuilder()
                .withDeployments(WAR_WHOAMI_SERVER_CHAIN)
                .withAdditionalUsers("another-server")
                .withCliCommands("/subsystem=elytron/simple-permission-mapper=seccontext-server-permissions"
                        + ":write-attribute(name=permission-mappings[1],value={principals=[another-server],"
                        + "permissions=[{class-name=org.wildfly.security.auth.permission.LoginPermission},"
                        + "{class-name=org.wildfly.security.auth.permission.RunAsPrincipalPermission, target-name=\"*\"}]})")
                .build());
    }

    /**
     * Shut down servers.
     */
    @AfterClass
    public static void shutdownServer3() throws IOException {
        server3.shutDown();
    }

    /**
     * Setup seccontext-server1.
     */
    protected void setupServer1() throws CommandLineException, IOException, MgmtOperationException {
        server1.resetContainerConfiguration(new ServerConfigurationBuilder()
                .withDeployments(FIRST_SERVER_CHAIN_EJB)
                .build());
    }

    /**
     * Setup seccontext-server2.
     */
    protected void setupServer2() throws CommandLineException, IOException, MgmtOperationException {
        server2.resetContainerConfiguration(new ServerConfigurationBuilder()
                .withDeployments(JAR_ENTRY_EJB_SERVER_CHAIN)
                .build());
    }

    @Test
    public void testForwardedAuthenticationPropagationChain() throws Exception {
        String[] tripleWhoAmI = SeccontextUtil.switchIdentity("admin", "admin",
                getTripleWhoAmICallable(ReAuthnType.FORWARDED_AUTHENTICATION, null, null, ReAuthnType.FORWARDED_AUTHENTICATION,
                        null, null), ReAuthnType.AC_AUTHENTICATION);
        assertNotNull("The firstServerChainBean.tripleWhoAmI() should return not-null instance", tripleWhoAmI);
        assertArrayEquals("Unexpected principal names returned from tripleWhoAmI", new String[]{"admin", "admin", "admin"},
                tripleWhoAmI);
    }

    @Test
    public void testForwardedAuthorizationPropagationChain() throws Exception {
        String[] tripleWhoAmI = SeccontextUtil.switchIdentity("admin", "admin",
                getTripleWhoAmICallable(ReAuthnType.FORWARDED_AUTHORIZATION, "server", "server", ReAuthnType.FORWARDED_AUTHORIZATION,
                        "another-server", "another-server"), ReAuthnType.AC_AUTHENTICATION);
        assertNotNull("The firstServerChainBean.tripleWhoAmI() should return not-null instance", tripleWhoAmI);
        assertArrayEquals("Unexpected principal names returned from tripleWhoAmI", new String[]{"admin", "admin", "admin"},
                tripleWhoAmI);
    }

    protected Callable<String[]> getTripleWhoAmICallable(final ReAuthnType firstType, final String firstUsername,
            final String firstPassword, final ReAuthnType secondType, final String secondUsername, final String secondPassword) {
        return () -> {
            final FirstServerChain bean = SeccontextUtil.lookup(
                    SeccontextUtil.getRemoteEjbName(FIRST_SERVER_CHAIN_EJB, "FirstServerChainBean",
                            FirstServerChain.class.getName(), isEntryStateful()), server1.getApplicationRemotingUrl());
            final String server2Url = server2.getApplicationRemotingUrl();
            final String server3Url = server3.getApplicationRemotingUrl();
            return bean.tripleWhoAmI(new CallAnotherBeanInfo.Builder()
                    .username(firstUsername)
                    .password(firstPassword)
                    .type(firstType)
                    .providerUrl(server2Url)
                    .statefullWhoAmI(isWhoAmIStateful())
                    .build(),
                    new CallAnotherBeanInfo.Builder()
                    .username(secondUsername)
                    .password(secondPassword)
                    .type(secondType)
                    .providerUrl(server3Url)
                    .statefullWhoAmI(isWhoAmIStateful())
                    .lookupEjbAppName(WAR_WHOAMI_SERVER_CHAIN)
                    .build());
        };
    }

    @Override
    protected boolean isEntryStateful() {
        return false;
    }

    @Override
    protected boolean isWhoAmIStateful() {
        return false;
    }

}
