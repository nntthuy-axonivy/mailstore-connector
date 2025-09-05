package com.axonivy.connector.mailstore;

import static com.axonivy.connector.mailstore.MailStoreService.LOG;

import java.util.Properties;

import javax.mail.Session;

import org.apache.commons.lang3.BooleanUtils;

import com.axonivy.connector.mailstore.enums.StartTLS;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.ssl.restricted.IvySslSocketFactory;
import ch.ivyteam.ivy.ssl.restricted.SslClientSettings;
import ch.ivyteam.ivy.ssl.restricted.SslConfig;
import ch.ivyteam.ivy.vars.Variable;

public class MailSessionProvider {

	private static final String PROPERTIES_VAR = "properties";

	private interface Property {
		String MAIL_SMTP_SSL_SOCKET_FACTORY = "mail.smtp.ssl.socketFactory";
		String MAIL_SMTP_SSL_SOCKET_FACTORY_FALLBACK = "mail.smtp.ssl.socketFactory.fallback";
	}

	static Session getSession(String storeName) throws Exception {
		Properties properties = getProperties(storeName);
		if (isStartTLSEnabled(properties)) {
			properties.put(Property.MAIL_SMTP_SSL_SOCKET_FACTORY, ivySslContext());
			// ensures that socket will only be created with our socket factory otherwise it
			// will fail
			properties.setProperty(Property.MAIL_SMTP_SSL_SOCKET_FACTORY_FALLBACK, "false");
		}

		return Session.getInstance(properties, null);
	}

	private static boolean isStartTLSEnabled(Properties properties) {
		if (properties == null) {
			return false;
		}
		return BooleanUtils.toBoolean(properties.getProperty(StartTLS.ENABLE.getProperty()))
				|| BooleanUtils.toBoolean(properties.getProperty(StartTLS.REQUIRED.getProperty()));
	}

	private static IvySslSocketFactory ivySslContext() {
		return new IvySslSocketFactory(new SslConfig(false, "noAlias", SslClientSettings.instance()));
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
