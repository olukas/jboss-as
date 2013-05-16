package org.jboss.as.test.clustering.cluster.web;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.clustering.ViewChangeListener;
import org.jboss.as.test.clustering.ViewChangeListenerBean;
import org.jboss.as.test.clustering.ViewChangeListenerServlet;
import org.jboss.as.test.clustering.cluster.ClusterAbstractTestCase;
import org.jboss.as.test.clustering.single.web.SimpleServlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

public class ReplicationForNegotiationAuthenticatorTestCase extends ClusteredWebFailoverAbstractCase /*ClusterAbstractTestCase*/ {
	
	// spusti dva containery
	/*@Override
    protected void setUp() {
        super.setUp();
        deploy(DEPLOYMENTS);
    }
	
	@Test
    public void testSessionReplication(
            @ArquillianResource(SimpleServlet.class) @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1,
            @ArquillianResource(SimpleServlet.class) @OperateOnDeployment(DEPLOYMENT_2) URL baseURL2) {
		
	}*/
	
	
	
	
	
	@Deployment(name = DEPLOYMENT_1, managed = false, testable = false)
    @TargetsContainer(CONTAINER_1)
    public static Archive<?> deployment0() {
        return getDeployment();
    }

    @Deployment(name = DEPLOYMENT_2, managed = false, testable = false)
    @TargetsContainer(CONTAINER_2)
    public static Archive<?> deployment1() {
        return getDeployment();
    }
    
    // predelat
    private static Archive<?> getDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "distributable.war");
        war.addClass(SimpleServlet.class);
        // Take web.xml from the managed test.
        war.setWebXML(ClusteredWebSimpleTestCase.class.getPackage(), "web.xml");
        war.addClasses(ViewChangeListenerServlet.class, ViewChangeListener.class, ViewChangeListenerBean.class);
        war.setManifest(new StringAsset("Manifest-Version: 1.0\nDependencies: org.jboss.msc, org.jboss.as.clustering.common, org.infinispan\n"));
        war.addAsManifestResource(new StringAsset("<jboss-deployment-structure><deployment><dependencies>" //
                + "<module name='org.jboss.security.negotiation'/>" //
                + "</dependencies></deployment></jboss-deployment-structure>"), //
                "jboss-deployment-structure.xml");
        war.addAsWebInfResource(ClusteredWebSimpleTestCase.class.getPackage(), "jboss-web_1.xml", "jboss-web.xml");
        log.info(war.toString(true));
        return war;
    }

}
