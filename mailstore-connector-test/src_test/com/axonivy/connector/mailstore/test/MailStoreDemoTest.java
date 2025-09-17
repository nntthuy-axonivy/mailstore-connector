package com.axonivy.connector.mailstore.test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;

import ch.ivyteam.ivy.bpm.engine.client.BpmClient;
import ch.ivyteam.ivy.bpm.engine.client.element.BpmProcess;
import ch.ivyteam.ivy.bpm.exec.client.IvyProcessTest;
import ch.ivyteam.ivy.environment.AppFixture;
import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.util.WaitUtil;

@Testcontainers
@IvyProcessTest
class MailStoreDemoTest {

	private List<String> logs = new ArrayList<>();

	private static final Path ssl = Path.of("../mailstore-connector-demo/docker/config/ssl/").toAbsolutePath();
	
	@SuppressWarnings("resource")
	@Container
	private final GenericContainer<?> mailContainer = new GenericContainer<>(
			DockerImageName.parse("mailserver/docker-mailserver:latest"))
		.withExposedPorts(993) // IMAPS
		.withCreateContainerCmdModifier(cmd -> {cmd
			.withHostName("mailserver")
			.withDomainName("test.mail.market.org")
			.withEnv(List.of(
				"SSL_TYPE=manual", 
				"SSL_KEY_PATH=/tmp/mail.key",
				"SSL_CERT_PATH=/tmp/mail.crt")
			)
			.withHostConfig(HostConfig.newHostConfig()
				.withPortBindings(
					new PortBinding(Ports.Binding.bindPort(993), new ExposedPort(993)))
				.withBinds(
					new Bind(ssl.resolve("mail.key").toString(),
					new Volume("/tmp/mail.key")),
					new Bind(ssl.resolve("mail.crt").toString(),
					new Volume("/tmp/mail.crt")))
			);
		})
		.waitingFor(new LogMessageWaitStrategy()
				.withRegEx(".*You need at least one mail account to start Dovecot.*"))
		.withStartupTimeout(Duration.ofSeconds(10))
		.withLogConsumer(frame -> {
			System.out.println(frame.getUtf8String());
			logs.add(frame.getUtf8String());
		});

	@BeforeAll
	static void setup(@TempDir Path conf) throws Exception {
		var trustP12 = conf.resolve("truststore.p12");
		trustCert(trustP12, ssl.resolve("mail.crt"));
		setIvyTrustStoreFile(trustP12);
	}
	
	@Test
	void imapRead(BpmClient client, AppFixture fixture) throws Exception {
		String userName = "user1@test.local";
		finishMailserverUserSetup(userName);
		configureDemo(fixture, userName);
		System.setProperty("mail.socket.debug", "true");

		BpmProcess mailStoreDemo = BpmProcess.name("MailStoreDemo");
		var result = client.start().process(mailStoreDemo.elementName("handleEmailsIvy.ivp")).execute();
		Ivy.log().info(result);
	}

	private void configureDemo(AppFixture fixture, String userName) {
		String imap = "mailstore-connector.localhost-imap";
		fixture.var(imap + ".port", "993");
		fixture.var(imap + ".user", userName);
		fixture.var(imap + ".password", "password123");
	}

	private void finishMailserverUserSetup(String userName) throws Exception {
		Thread.sleep(Duration.ofSeconds(5)); // container still needs a few secs before accepting commands
		mailContainer.execInContainer("setup", "email", "add", userName, "password123");
		new WaitUtil(() -> logs.toString().contains("mailserver.test.mail.market.org is up and running"))
			.interval(Duration.ofSeconds(1))
			.timeout(Duration.ofSeconds(10))
			.start();
		Thread.sleep(Duration.ofSeconds(2)); // wait until created users are enacted; SSL stack fully running
	}

	@SuppressWarnings("restriction")
	private static void setIvyTrustStoreFile(Path trustP12) {
		var ivyYaml = ch.ivyteam.ivy.configuration.restricted.IConfiguration.instance();
		ivyYaml.set("SSL.Client.TrustStore.File", trustP12.toString());
	}
	
	private static void trustCert(Path truststore, Path cert) throws Exception {
		var password = "changeit";
		var keyStore = KeyStore.getInstance("PKCS12");
		keyStore.load(null, null);
		keyStore.setCertificateEntry("mail", loadCertificate(cert));
		try(OutputStream os = Files.newOutputStream(truststore, StandardOpenOption.CREATE)) {
			keyStore.store(os, password.toCharArray());
		}
	}
	
	private static Certificate loadCertificate(Path cert) throws Exception {
		try (InputStream inputStream = Files.newInputStream(cert, StandardOpenOption.READ)) {
			CertificateFactory certFactory = CertificateFactory.getInstance("X509");
			return certFactory.generateCertificate(inputStream);
		}
	}

}
