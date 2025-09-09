package com.axonivy.connector.mailstore.test;

import org.junit.jupiter.api.Test;

import ch.ivyteam.ivy.bpm.engine.client.BpmClient;
import ch.ivyteam.ivy.bpm.engine.client.element.BpmElement;
import ch.ivyteam.ivy.bpm.exec.client.IvyProcessTest;
import ch.ivyteam.ivy.environment.AppFixture;
import ch.ivyteam.ivy.environment.Ivy;

@IvyProcessTest
public class MailStoreDemoTest {

	@Test
	public void run(BpmClient client, AppFixture fixture) {
		fixture.var("mailstoreConnector.localhostImap.port", "143");
		java.lang.System.setProperty("mail.socket.debug", "true");
		var result = client.start().process(BpmElement.pid("183EF4C4DA38CCD9-f0")).execute();
		Ivy.log().info(result);
	}
	
}
