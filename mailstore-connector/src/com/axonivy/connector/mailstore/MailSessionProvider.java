package com.axonivy.connector.mailstore;

import static com.axonivy.connector.mailstore.MailStoreService.LOG;

import java.util.Properties;

import javax.mail.Session;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.lang3.BooleanUtils;

import com.axonivy.connector.mailstore.enums.StartTLS;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.ssl.restricted.IvySslSocketFactory;
import ch.ivyteam.ivy.ssl.restricted.SslClientSettings;
import ch.ivyteam.ivy.ssl.restricted.SslConfig;
import ch.ivyteam.ivy.vars.Variable;

public class MailSessionProvider {

	private static final String PROPERTIES_VAR = "properties";

	/**
	 * https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/imap/package-summary.html
	 */
	private interface Property {
		
		public interface SMTP {
			String SSL_SOCKET_FACTORY = "mail.smtp.ssl.socketFactory";
			String SSL_SOCKET_FACTORY_FALLBACK = "mail.smtp.ssl.socketFactory.fallback";
		}
		public interface IMAP {
			String SSL_ENABLED= "mail.imap.ssl.enable";
			String SSL_SOCKET_FACTORY = "mail.imap.ssl.socketFactory";
			String SOCKET_FACTORY = "mail.imap.socketFactory";
			String SOCKET_FACTORY_FALLBACK = "mail.imap.socketFactory.fallback";
			
		}
	}

	static Session getSession(String storeName) throws Exception {
		Properties properties = getProperties(storeName);
		enrichSslContext(properties);
		return Session.getInstance(properties, null);
	}

	private static void enrichSslContext(Properties properties) {
		var socketFactory = ivySslContext();
		boolean tlsEnabled = isStartTLSEnabled(properties);
		if (tlsEnabled) {
			properties.put(Property.SMTP.SSL_SOCKET_FACTORY, socketFactory);
			// ensures that socket will only be created with our socket factory otherwise it will fail
			properties.setProperty(Property.SMTP.SSL_SOCKET_FACTORY_FALLBACK, "false");
		}
		boolean imapSsl = BooleanUtils.toBoolean(properties.getProperty(Property.IMAP.SSL_ENABLED));
		if (imapSsl) {
			Ivy.log().info("enabling imap SSL context");
			properties.put(Property.IMAP.SSL_SOCKET_FACTORY, socketFactory);
			properties.put(Property.IMAP.SOCKET_FACTORY_FALLBACK, "false");
		}
		properties.put(Property.IMAP.SOCKET_FACTORY, ivySslContext());
	}

	private static boolean isStartTLSEnabled(Properties properties) {
		if (properties == null) {
			return false;
		}
		String enableRaw = properties.getProperty(StartTLS.ENABLE.getProperty());
		boolean enable = BooleanUtils.toBoolean(enableRaw);
		boolean required = BooleanUtils.toBoolean(properties.getProperty(StartTLS.REQUIRED.getProperty()));
		return enable || required;
	}

	private static SSLSocketFactory ivySslContext() {
		SslClientSettings settings = SslClientSettings.instance();
		LOG.debug("using truststore file:"+settings.getTrustStore().getFile());
		return new IvySslSocketFactory(new SslConfig(false, "noAlias", settings));
	}

	static Session getSession() {
		Properties properties = getProperties();
		return Session.getDefaultInstance(properties, null);
	}

	static Properties getProperties() {
		Properties properties = System.getProperties();

		String propertiesPrefix = PROPERTIES_VAR + ".";
		for (Variable variable : Ivy.var().all()) {
			String name = variable.name();
			if (name.startsWith(propertiesPrefix)) {
				String propertyName = name.substring(propertiesPrefix.length());
				String value = variable.value();
				LOG.info("Setting additional property {0}: ''{1}''", propertyName, value);
				properties.setProperty(name, value);
			}
		}

		return properties;
	}

	// Only retrieve properties from the store that belong to
	static Properties getProperties(String storeName) {
		Properties properties = new Properties();
		String propertiesPrefix = String.format("%s.%s.%s.", MailStoreService.MAIL_STORE_VAR, storeName,
				PROPERTIES_VAR);

		for (Variable variable : Ivy.var().all()) {
			String name = variable.name();
			if (name.contains(propertiesPrefix)) {
				String propertyName = getSubstringAfterProperties(name);
				String value = variable.value();
				LOG.info("Setting additional property {0}: ''{1}''", propertyName, value);
				properties.setProperty(propertyName, value);
			}
		}

		return properties;
	}

	private static String getSubstringAfterProperties(String input) {
		int index = input.indexOf(PROPERTIES_VAR);

		return index != -1 ? input.substring(index + PROPERTIES_VAR.length() + 1) : null;
	}

}
