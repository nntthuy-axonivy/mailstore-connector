package com.axonivy.connector.mailstore;

import static com.axonivy.connector.mailstore.MailStoreService.LOG;

import java.util.Properties;

import javax.mail.Session;

import com.axonivy.connector.oauth.ssl.SSLContextConfigure;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.vars.Variable;

public class MailSessionProvider {

  private static final String PROPERTIES_VAR = "properties";

  static Session getSession(String storeName) throws Exception {
    Properties properties = getProperties(storeName);
    if (SSLContextConfigure.get().isStartTLSEnabled(properties)) {
      SSLContextConfigure.get().addIvyTrustStoreToCurrentContext();
    }

    return Session.getInstance(properties, null);
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
    String propertiesPrefix = String.format("%s.%s.%s.", MailStoreService.MAIL_STORE_VAR, storeName, PROPERTIES_VAR);

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
