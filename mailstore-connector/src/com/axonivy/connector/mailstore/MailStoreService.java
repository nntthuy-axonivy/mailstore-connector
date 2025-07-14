package com.axonivy.connector.mailstore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Flags.Flag;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.axonivy.connector.mailstore.enums.MailMovingMethod;
import com.axonivy.connector.mailstore.provider.BasicUserPasswordProvider;
import com.axonivy.connector.mailstore.provider.UserPasswordProvider;
import com.axonivy.connector.oauth.ssl.SSLContextConfigure;

import ch.ivyteam.ivy.bpm.error.BpmError;
import ch.ivyteam.ivy.bpm.error.BpmPublicErrorBuilder;
import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.vars.Variable;
import ch.ivyteam.log.Logger;

public class MailStoreService {
	private static final MailStoreService INSTANCE = new MailStoreService();
	private static final Logger LOG = Ivy.log();
	private static final String MAIL_STORE_VAR = "mailstoreConnector";
	private static final String PROTOCOL_VAR = "protocol";
	private static final String HOST_VAR = "host";
	private static final String PORT_VAR = "port";
	private static final String DEBUG_VAR = "debug";
	private static final String PROPERTIES_VAR = "properties";
	private static final String MOVING_METHOD_VAR = "movingMethod";
	private static final String ERROR_BASE = "mailstore:connector";
	private static final Address[] EMPTY_ADDRESSES = new Address[0];
	private static Map<String, UserPasswordProvider> userPasswordProviderRegister = new HashMap<>();

	public static MailStoreService get() {
		return INSTANCE;
	}

	/**
	 * Get a {@link MessageIterator}.
	 * 
	 * @param storeName     name of Email Store (Imap Configuration)
	 * @param srcFolderName source folder name
	 * @param dstFolderName destination folder name (if <code>null</code> then handled mails will be deleted)
	 * @param delete        delete mail from source folder?
	 * @param filter        a filter predicate
	 * @return
	 * @throws MessagingException
	 */
	public static MessageIterator messageIterator(String storeName, String srcFolderName, String dstFolderName,
			boolean delete, Predicate<Message> filter) {
		return messageIterator(storeName, srcFolderName, dstFolderName, delete, filter, null);
	}

	/**
	 * Get a {@link MessageIterator}.
	 * 
	 * @param storeName     name of Email Store (Imap Configuration)
	 * @param srcFolderName source folder name
	 * @param dstFolderName destination folder name (if <code>null</code> then handled mails will be deleted)
	 * @param delete        delete mail from source folder?
	 * @param filter        a filter predicate
	 * @param sort          a sort comparator
	 * @return
	 * @throws MessagingException
	 */
	public static MessageIterator messageIterator(String storeName, String srcFolderName, String dstFolderName,
			boolean delete, Predicate<Message> filter, Comparator<Message> comparator) {
		return new MessageIterator(storeName, srcFolderName, Arrays.asList(dstFolderName), delete, filter, comparator);
	}
	
	/**
	 * Get a {@link MessageIterator}.
	 * 
	 * @param storeName     name of Email Store (Imap Configuration)
	 * @param srcFolderName source folder name
	 * @param dstFolderNames list destination folder will be moved to these folder
	 * @param delete        delete mail from source folder?
	 * @param filter        a filter predicate
	 * @param sort          a sort comparator
	 * @return
	 * @throws MessagingException
	 */
	public static MessageIterator messageIterator(String storeName, String srcFolderName, 
			boolean delete, Predicate<Message> filter, Comparator<Message> comparator, List<String> dstFolderNames) {
		return new MessageIterator(storeName, srcFolderName, dstFolderNames, delete, filter, comparator);
	}
	
	/**
	 * Get a {@link Predicate} to match subjects against a regular expression.
	 * 
	 * Note, that the full subject must match. If you want a "contains"
	 * match, use something like:
	 * 
	 * <pre>
	 * subjectMatches(".*my matching pattern.*", false);
	 * </pre>
	 * 
	 * @param pattern
	 * @param caseSensitive
	 * @return
	 */
	public static Predicate<Message> subjectMatches(String pattern) {
		Pattern subjectPattern = createStandardPattern(pattern);
		return m -> {
			try {
				return subjectPattern.matcher(nullSafe(m.getSubject(), "")).matches();
			} catch (MessagingException e) {
				throw buildError("predicate:subjectmatches").build();
			}
		};
	}

	/**
	 * Get a {@link Predicate} to match "from" addresses against a regular expression.
	 * 
	 * Note, that the full address must match. If you want a "contains"
	 * match, use something like:
	 * 
	 * <pre>
	 * fromMatches(".*my matching pattern.*");
	 * </pre>
	 * 
	 * @param pattern
	 * @return
	 */
	public static Predicate<Message> fromMatches(String pattern) {
		Pattern fromPattern = createStandardPattern(pattern);
		return m -> {
			try {
				boolean result = false;
				for(Address address : nullSafe(m.getFrom(), EMPTY_ADDRESSES)) {
					result = fromPattern.matcher(address.toString()).matches();
				}
				return result;
			} catch (MessagingException e) {
				throw buildError("predicate:frommatches").build();
			}
		};
	}

	/**
	 * Get a {@link Predicate} to match any "to" addresses against a regular expression.
	 * 
	 * Note, that the full address must match. If you want a "contains"
	 * match, use something like:
	 * 
	 * <pre>
	 * toMatches(".*my matching pattern.*");
	 * </pre>
	 * 
	 * @param pattern
	 * @return
	 */
	public static Predicate<Message> toMatches(String pattern) {
		return rcptMatches(RecipientType.TO, pattern, "tomatches");
	}

	/**
	 * Get a {@link Predicate} to match any "cc" addresses against a regular expression.
	 * 
	 * Note, that the full address must match. If you want a "contains"
	 * match, use something like:
	 * 
	 * <pre>
	 * ccMatches(".*my matching pattern.*");
	 * </pre>
	 * 
	 * @param pattern
	 * @return
	 */
	public static Predicate<Message> ccMatches(String pattern) {
		return rcptMatches(RecipientType.CC, pattern, "ccmatches");
	}

	/**
	 * Get a {@link Predicate} to match any "bcc" addresses against a regular expression.
	 * 
	 * Note, that the full address must match. If you want a "contains"
	 * match, use something like:
	 * 
	 * <pre>
	 * bccMatches(".*my matching pattern.*");
	 * </pre>
	 * 
	 * Note, that received messages typically do not contain a BCC header.
	 * 
	 * @param pattern
	 * @return
	 */
	public static Predicate<Message> bccMatches(String pattern) {
		return rcptMatches(RecipientType.BCC, pattern, "bccmatches");
	}

	private static Predicate<Message> rcptMatches(RecipientType recipientType, String pattern, String errorCode) {
		Pattern rcptPattern = createStandardPattern(pattern);
		return m -> {
			try {
				boolean result = false;
				for(Address address : nullSafe(m.getRecipients(recipientType), EMPTY_ADDRESSES)) {
					if(rcptPattern.matcher(address.toString()).matches()) {
						result = true;
						break;
					}
				}
				return result;
			} catch (MessagingException e) {
				throw buildError("predicate:" + errorCode).build();
			}
		};
	}

	/**
	 * Get a {@link Predicate} to match any recipient addresses against a regular expression.
	 * 
	 * Note, that the full address must match. If you want a "contains"
	 * match, use something like:
	 * 
	 * <pre>
	 * anyRecipientMatches(".*my matching pattern.*");
	 * </pre>
	 * 
	 * @param pattern
	 * @return
	 */
	public static Predicate<Message> anyRecipientMatches(String pattern) {
		Pattern rcptPattern = createStandardPattern(pattern);
		return m -> {
			try {
				boolean result = false;
				for(Address address : nullSafe(m.getAllRecipients(), EMPTY_ADDRESSES)) {
					if(rcptPattern.matcher(address.toString()).matches()) {
						result = true;
						break;
					}
				}

				return result;
			} catch (MessagingException e) {
				throw buildError("predicate:frommatches").build();
			}
		};
	}

	/**
	 * Get a {@link Predicate} to match any header against a regular expression.
	 * 
	 * Note, that the full header must match. If you want a "contains"
	 * match, use something like:
	 * 
	 * <pre>
	 * headerMatches("Reply-To", ".*my matching pattern.*");
	 * </pre>
	 * 
	 * @param pattern
	 * @return
	 */
	public static Predicate<Message> headerMatches(String headerName, String pattern) {
		Pattern headerPattern = createStandardPattern(pattern);
		return m -> {
			try {
				boolean result = false;
				for(String header : nullSafe(m.getHeader(headerName), new String[0])) {
					if(headerPattern.matcher(header).matches()) {
						result = true;
						break;
					}
				}

				return result;
			} catch (MessagingException e) {
				throw buildError("predicate:headermatches").build();
			}
		};
	}

	/**
	 * Does this message have any attachments?
	 * 
	 * @param includeSubMessages also look into sub-messages?
	 * @return
	 */
	public static Predicate<Message> hasAttachment(boolean includeSubMessages) {
		return m -> {
			Collection<Part> parts = MessageService.getAllParts(m, includeSubMessages, MessageService.isAttachment());
			return parts.size() > 0;
		};
	}

	/**
	 * Does this message have any part like this?
	 * 
	 * @param mimeType accept all if <code>null</code>
	 * @param disposition accept all if <code>null</code> 
	 * @param filenamePattern accept all if <code>null</code>
	 * @param includeSubMessages also look into sub-messages?
	 * @return
	 */
	public static Predicate<Message> hasPart(String mimeType, String disposition, String filenamePattern, boolean includeSubMessages) {
		return m -> {
			Predicate<Part> p = MessageService.alwaysTrue();
			if(mimeType != null) {
				p = p.and(MessageService.isMimeType(mimeType));
			}
			if(disposition != null) {
				p = p.and(MessageService.isDisposition(disposition));
			}
			if(filenamePattern != null) {
				p = p.and(MessageService.filenameMatches(filenamePattern));
			}

			Collection<Part> parts = MessageService.getAllParts(m, includeSubMessages, p);
			return parts.size() > 0;
		};
	}

	/**
	 * Always return true.
	 * 
	 * @return
	 */
	public static Predicate<Message> alwaysTrue() {
		return m -> true;
	}

	/**
	 * Always return false.
	 * 
	 * @return
	 */
	public static Predicate<Message> alwaysFalse() {
		return m -> false;
	}

	/**
	 * Function to Register Authentication Provider
	 * 
	 * client need to register authentication provider before they connect to mailstore, if not default basic authentication will be used
	 * 
	 */
	public static void registerUserPasswordProvider(String storeName, UserPasswordProvider userPasswordProvider) {
		userPasswordProviderRegister.put(storeName, userPasswordProvider);
	}

	/**
	 * Iterate through the E-Mails of a store.
	 * 
	 * Optionally remove or move messages to a destination folder when they were handled.
	 * 
	 * Note that the {@link Iterator} will only close and return it's resources when it was
	 * running to the end. If it is terminated earlier, the {@link #close()} method must be
	 * called. It is not a problem, to call the close method on a closed object again.
	 */
	public static class MessageIterator implements Iterator<Message>, AutoCloseable {
		private Store store;
		private Folder srcFolder;
		private boolean delete;
		private Message[] messages;
		private int nextIndex;
		private ClassLoader originalClassLoader;
		private Map<String, Folder> dstFolderMap;
		private MailMovingMethod mailMovingMethod;

		private MessageIterator(String storeName, String srcFolderName, List<String> dstFolderNames, boolean delete,
				Predicate<Message> filter, Comparator<Message> comparator) {
			try {
				// Use own classloader so that internal classes of javax.mail API are found.
				// If they cannot be found on the classpath, then mail content will not
				// be recognized and always be reported as IMAPInputStream
				originalClassLoader = Thread.currentThread().getContextClassLoader();
				Thread.currentThread().setContextClassLoader(Session.class.getClassLoader());

				this.delete = delete;
				store = MailStoreService.openStore(storeName);
				srcFolder = MailStoreService.openFolder(store, srcFolderName, Folder.READ_WRITE);
				mailMovingMethod = MailMovingMethod.from(getVar(storeName, MOVING_METHOD_VAR));
				
				if(CollectionUtils.isNotEmpty(dstFolderNames)) {
					dstFolderMap = new LinkedHashMap<>();
					for(String dstFolderName : dstFolderNames) {
						if(StringUtils.isNotBlank(dstFolderName)) {
							dstFolderMap.put(dstFolderName, MailStoreService.openFolder(store, dstFolderName, Folder.READ_WRITE));
						}else {
							dstFolderMap.put(dstFolderName, null);
						}
					}
				}
				
				messages = srcFolder.getMessages();

				// pre-fetch headers
				FetchProfile fetchProfile = new FetchProfile();
				fetchProfile.add(FetchProfile.Item.ENVELOPE);
				srcFolder.fetch(messages, fetchProfile);

				if(filter != null) {
					messages = Stream.of(messages).filter(filter).toArray(Message[]::new);
				}
				
				if (comparator != null) {
					messages = Stream.of(messages).sorted(comparator).toArray(Message[]::new);
				}

				LOG.debug("Received {0}{1} messages.", messages.length, filter != null ? " matching" : "");

				nextIndex = 0;
			} catch(Exception e) {
				try {
					close();
				}
				catch(Exception closeException) {
					LOG.info("Ignoring exception in close that happened during handling of iterator exception.", closeException);
				}
				throw buildError("iterator").withCause(e).build();
			}
		}

		/**
		 * Close and sync all actions to the mail server.
		 * 
		 * Will be called automatically after the last element is fetched.
		 * Must be called if the iterator does not run to the end.
		 */
		@Override
		public void close() {
			try {
				Exception exception = null;
				for (Folder dstFolder : dstFolderMap.values()) {
		            if (dstFolder != null && dstFolder.isOpen()) {
						try {
							dstFolder.close();
						} catch (Exception e) {
							LOG.error("Could not close destination folder {0}", e, dstFolder);
							if (exception == null) {
								exception = e;
							}
						}
					}
		        }
				if (srcFolder != null && srcFolder.isOpen()) {
					try {
						srcFolder.close();
					} catch (Exception e) {
						LOG.error("Could not close source folder {0}", e, srcFolder);
						if (exception == null) {
							exception = e;
						}
					}
				}
				if (store != null) {
					try {
						store.close();
					} catch (Exception e) {
						LOG.error("Could not close store {0}", e, srcFolder);
						if (exception == null) {
							exception = e;
						}
					}
				}
				if (exception != null) {
					throw buildError("close").withCause(exception).build();
				} 
			} finally {
				Thread.currentThread().setContextClassLoader(originalClassLoader);
			}
		}

		@Override
		public boolean hasNext() {
			boolean hasNext = messages.length > nextIndex;
			if(!hasNext) {
				close();
			}
			return hasNext;
		}

		@Override
		public Message next() {
			try {
				Message current = messages[nextIndex];
				nextIndex += 1;
				return current;
			} catch (Exception e) {
				throw new NoSuchElementException("Could not access message at index: " + nextIndex + " length: " + (messages != null ? messages.length : "null"), e);
			}
		}

		/**
		 * Call this function, when the message was handled successfully and should be deleted/moved.
		 * 
		 * It will then be moved to the destination folder (if there is one)
		 * and will be deleted in the source folder (if the delete option is set).
		 * If this function is not called, the message will be coming again in the
		 * next iterator.
		 */
		public void handledMessage(boolean handled) {
			handledMessage(handled, null);
		}
		
		private Folder getFirstEmailFolder() {
			if(null == dstFolderMap) {
				return null;
			}
			return dstFolderMap.values().stream().collect(Collectors.toList()).get(0);
		}		
		
		/**
		 * Call this function, when the message was handled successfully and should be deleted/moved to a particular destination folder name
		 * 
		 * It will then be moved to the destination folder (if there is one)
		 * and will be deleted in the source folder (if the delete option is set).
		 * If this function is not called, the message will be coming again in the
		 * next iterator.
		 */
		public void handledMessage(boolean handled, String dstFolderName) {
			String subject = null;
			try {
				if (handled) {
					Message current = messages[nextIndex - 1];
					subject = MailStoreService.toString(current);
					try {
						Folder dstFolder =
								StringUtils.isBlank(dstFolderName) ? getFirstEmailFolder() : dstFolderMap.get(dstFolderName);
						if (dstFolder != null) {
							dstFolderName = dstFolder.getName();
							LOG.debug("Appending {0} to {1} folder", subject, dstFolderName);
							if (mailMovingMethod == MailMovingMethod.APPEND) {
								dstFolderMap.get(dstFolderName).appendMessages(new Message[] {current});
							} else {
								srcFolder.copyMessages(new Message[] {current}, dstFolderMap.get(dstFolderName));
							}
						}
					} finally {
						if (delete) {
							LOG.debug("Deleting {0}", MailStoreService.toString(current));
							current.setFlag(Flag.DELETED, true);
						}
					}
				}

				if (!hasNext()) {
					close();
				}
			} catch (Exception e) {
				LOG.error("Unable to handle email {0}", subject);
				throw buildError("handled").withCause(e).build();
			}
		}
	}

	/**
	 * Get the raw message data e.g. for saving.
	 * 
	 * @param message
	 * @return
	 */
	public static InputStream saveMessage(Message message) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			message.writeTo(bos);
		} catch (IOException | MessagingException e) {
			throw buildError("save").withCause(e).build();
		}
		return new ByteArrayInputStream(bos.toByteArray());
	}
	
	/**
	 * Create a mail from raw message data e.g. from loading.
	 * 
	 * @param stream
	 * @return
	 */
	public static Message loadMessage(InputStream stream) {
		try {
			return new MimeMessage(getSession(), stream);
		} catch (MessagingException e) {
			throw buildError("load").withCause(e).build();
		}
	}

	/**
	 * Get a mail store.
	 * 
	 * Note, that it is recommended to use {@link MessageIterator}.
	 * 
	 * @param storeName
	 * @return
	 * @throws MessagingException
	 */
	public static Store openStore(String storeName) throws Exception {
		Store store = null;

		String protocol = getVar(storeName, PROTOCOL_VAR);
		String host = getVar(storeName, HOST_VAR);
		String portString = getVar(storeName, PORT_VAR);
		
		UserPasswordProvider userPasswordProvider = userPasswordProviderRegister.get(storeName);
		// adapt exist project already use this connector, default is basic auth
		if (null == userPasswordProvider) {
			userPasswordProvider = new BasicUserPasswordProvider();
		}
		
		String user = userPasswordProvider.getUser(storeName);
		String password = userPasswordProvider.getPassword(storeName);

		String debugString = getVar(storeName, DEBUG_VAR);

		LOG.debug(
				"Creating mail store connection, protocol: {0} host: {1} port: {2} UserPasswordProvider: {3} user: {4} password: {5} debug: {6}",
				protocol, host, portString, userPasswordProvider.getClass().getSimpleName(), user,
				StringUtils.isNotBlank(password) ? "is set" : "is not set", debugString);

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		PrintStream debugStream = new PrintStream(stream);

		boolean debug = true;

		try {
			Session session = getSession(storeName);

			debug = Boolean.parseBoolean(debugString);
			int port = Integer.parseInt(portString);

			if(debug) {
				session.setDebug(debug);
				session.setDebugOut(debugStream);
			}
			store = session.getStore(protocol);
			store.connect(host, port, user, password);
		} catch(Exception e) {
			try {
				if (store != null) {
					store.close();
				}
			} catch (MessagingException closeEx) {
				LOG.error("Closing store caused another exception. Anyway the store is closed.", closeEx);
			}
			throw (e);
		}
		finally {
			if(debug) {
				LOG.debug("Debug output:\n {0}", stream.toString());
			}
		}
		return store;
	}

	private static Session getSession(String storeName) throws Exception {
		Properties properties = getProperties(storeName);
		if (SSLContextConfigure.get().isStartTLSEnabled(properties)) {
			SSLContextConfigure.get().addIvyTrustStoreToCurrentContext();
		}

		// Use getInstance instead of getDefaultInstance
		Session session = Session.getInstance(properties, null);
		return session;
	}
	
	private static Session getSession() {
		Properties properties = getProperties();
		Session session = Session.getDefaultInstance(properties, null);
		return session;
	}

	private static Folder openFolder(Store store, String folderName, int mode) throws MessagingException {
		LOG.debug("Opening folder {0}", folderName);
		Folder folder = store.getFolder(folderName);

		if(folder != null && folder.exists()) {
			LOG.debug("Message count: {0} new: {1} unread: {2} deleted: {3}",
					folder.getMessageCount(), folder.getNewMessageCount(),
					folder.getUnreadMessageCount(), folder.getDeletedMessageCount());

			folder.open(mode);
		}
		else {
			throw new MessagingException("Could not open folder " + folderName);
		}

		return folder;
	}

	public static String getVar(String store, String var) {
		return Ivy.var().get(String.format("%s.%s.%s", MAIL_STORE_VAR, store, var));
	}
	
	private static Properties getProperties() {
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
	private static Properties getProperties(String storeName) {
		Properties properties = new Properties();
		String propertiesPrefix = String.format("%s.%s.%s.", MAIL_STORE_VAR, storeName, PROPERTIES_VAR);

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

	public static BpmPublicErrorBuilder buildError(String code) {
		BpmPublicErrorBuilder builder = BpmError.create(ERROR_BASE + ":" + code);
		return builder;
	}

	private static Pattern createStandardPattern(String pattern) {
		return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	}

	private static String toString(Message m) {
		String subject = null;
		try {
			subject = m != null ? m.getSubject() : null;
		} catch (MessagingException e) {
			subject = "Exception while reading subject";
		}

		return String.format("Message[subject: '%s']", subject);
	}

	private static <T> T nullSafe(T unsafe, T def) {
		return unsafe != null ? unsafe : def;
	}
}
