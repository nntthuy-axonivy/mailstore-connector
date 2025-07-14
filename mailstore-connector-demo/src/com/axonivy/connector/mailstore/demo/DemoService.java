package com.axonivy.connector.mailstore.demo;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Part;

import com.axonivy.connector.mailstore.MailStoreService;
import com.axonivy.connector.mailstore.MailStoreService.MessageIterator;
import com.axonivy.connector.mailstore.provider.UserPasswordProvider;
import com.axonivy.connector.mailstore.MessageService;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.workflow.document.IDocument;
import ch.ivyteam.log.Logger;

public class DemoService {
	private static final Logger LOG = Ivy.log();
	private static final String INBOX = "INBOX";
	private static final String USER_PASSWORD_PROVIDER = "userPasswordProvider";
	private static final String LOCALHOST_IMAP = "localhostImap";
	private static final String LOCALHOST_IMAP_BASIC_AUTHENTICATION = "localhostImapBasicAuthentication";
	private static final String LOCALHOST_IMAP_AZURE_OAUTH2_AUTHENTICATION = "localhostImapAzureOauth2Authentication";

	public static void handleMessages() throws MessagingException, IOException {
		MessageIterator iterator = MailStoreService.messageIterator(LOCALHOST_IMAP, INBOX, null, false, MailStoreService.subjectMatches(".*test [0-9]+.*"), new MessageComparator());

		while (iterator.hasNext()) {
			Message message = iterator.next();

			boolean handled = handleMessage(message);
			iterator.handledMessage(handled);
		}
	}

	public static boolean handleMessage(Message message) throws MessagingException, IOException {
		LOG.info("Working on message {0} received at {1} type {2}", message.getSubject(), message.getReceivedDate(), message.getContent().getClass());

		Predicate<Part> collectPredicate = MessageService.isImage("*");
		Collection<Part> parts = MessageService.getAllParts(message, false, collectPredicate);

		// For demonstration, save the message to a case document.
		IDocument doc = Ivy.wfCase().documents().add(UUID.randomUUID().toString() + ".eml");
		doc.write().withContentFrom(MailStoreService.saveMessage(message));

		for (Part part : parts) {
			LOG.info("  Part: Filename: {0} Description: {1} ContentType: {2} Disposition: {3} Content Class: {4}",
					part.getFileName(), part.getDescription(), part.getContentType(), part.getDisposition(), part.getContent().getClass());
		}
		return true;
	}
	
	public static void handleMessagesMultiDestinationFolder() throws MessagingException, IOException {
		MessageIterator iterator = MailStoreService.messageIterator(LOCALHOST_IMAP, INBOX, true, MailStoreService.subjectMatches(".*test [0-9]+.*"), new MessageComparator(), Arrays.asList("Processed", "ErrorFolder"));
		int runner = 0;
		
		while (iterator.hasNext()) {
			Message message = iterator.next();

			boolean handled = handleMessage(message);
			iterator.handledMessage(handled, runner % 2 == 0 ? "Processed" : "ErrorFolder");
			runner = runner + 1;
		}
	}
	
	public static void connectMailStoreWithBasicAuth() throws MessagingException, IOException {
		// get from variable mailstoreConnector.localhostImap.userPasswordProvider
		String authProviderPath = MailStoreService.getVar(LOCALHOST_IMAP_BASIC_AUTHENTICATION, USER_PASSWORD_PROVIDER);
		initAuthProvider(LOCALHOST_IMAP_BASIC_AUTHENTICATION, authProviderPath);
		
		MessageIterator iterator = MailStoreService.messageIterator(LOCALHOST_IMAP_BASIC_AUTHENTICATION, INBOX, null, false, MailStoreService.subjectMatches(".*"), new MessageComparator());

		while (iterator.hasNext()) {
			Message message = iterator.next();
			boolean handled = handleMessage(message);
			iterator.handledMessage(handled);
		}
	}
	
	public static void connectMailStoreWithAzureOauth2() throws MessagingException, IOException {
		// get from variable mailstoreConnector.localhostImap.userPasswordProvider
		String authProviderPath = MailStoreService.getVar(LOCALHOST_IMAP_AZURE_OAUTH2_AUTHENTICATION, USER_PASSWORD_PROVIDER);
		initAuthProvider(LOCALHOST_IMAP_AZURE_OAUTH2_AUTHENTICATION, authProviderPath);
		
		MessageIterator iterator = MailStoreService.messageIterator(LOCALHOST_IMAP_AZURE_OAUTH2_AUTHENTICATION, INBOX, null, false, MailStoreService.subjectMatches(".*"), new MessageComparator());

		while (iterator.hasNext()) {
			Message message = iterator.next();
			boolean handled = handleMessage(message);
			iterator.handledMessage(handled);
		}
	}

	public static void handleAttachmentMessages() throws MessagingException, IOException {
		MessageIterator iterator = MailStoreService.messageIterator(
				LOCALHOST_IMAP,
				INBOX,
				null,
				false,
				null);

		while (iterator.hasNext()) {
			Message message = iterator.next();

			List<Part> parts = MessageService.getAllParts(message, true, null);

			for (Part part : parts) {
				LOG.info("Part Disposition: {0}", part.getDisposition());
			}

			boolean handled = logMessage(message);
			iterator.handledMessage(handled);
		}
	}
	
	private static void initAuthProvider(String storeName, String authProviderPath) {
		try {
			Class<?> clazz = Class.forName(authProviderPath);
			UserPasswordProvider userPasswordProvider = (UserPasswordProvider) clazz.getDeclaredConstructor().newInstance();
	        MailStoreService.registerUserPasswordProvider(storeName, userPasswordProvider);
		} catch(Exception ex) {
			LOG.error("Exception during instatiation of UserPasswordProvider ''{0}''.",ex, authProviderPath);
		}
	}

	private static boolean logMessage(Message message) throws MessagingException, IOException {
		LOG.info("Working on message {0} received at {1} type {2}", message.getSubject(), message.getReceivedDate(), message.getContent().getClass());
		Collection<Part> parts = MessageService.getAllParts(message, false, null);
		for (Part part : parts) {
			LOG.info("    - Part: Filename: {0} Description: {1} ContentType: {2} Disposition: {3} Content Class: {4}",
					part.getFileName(), part.getDescription(), part.getContentType(), part.getDisposition(), part.getContent().getClass());
		}
		return false;
	}
}
