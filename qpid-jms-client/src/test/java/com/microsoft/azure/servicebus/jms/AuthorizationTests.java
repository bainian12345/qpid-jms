package com.microsoft.azure.servicebus.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.Topic;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.microsoft.azure.servicebus.management.ManagementClientAsync;
import com.microsoft.azure.servicebus.management.QueueDescription;
import com.microsoft.azure.servicebus.management.SubscriptionDescription;
import com.microsoft.azure.servicebus.management.TopicDescription;

public class AuthorizationTests {
    private static ConnectionFactory CONNECTION_FACTORY;
    private static ConnectionFactory SEND_ONLY_CF;
    private static ConnectionFactory LISTEN_ONLY_CF;
    private static ConnectionFactory INVALID_SAS_CF;
    private static ManagementClientAsync MANAGEMENT_CLIENT;
    private static QueueDescription QUEUE_DESCRIPTION;
    private static TopicDescription TOPIC_DESCRIPTION;
    private static SubscriptionDescription SUBSCRIPTION_DESCRIPTION;
    private Connection connection;
    private Connection sendOnlyConnection;
    private Connection listenOnlyConnection;
    private Connection invalidSasConnection;
    private Session session;
    private Session sendOnlySession;
    private Session listenOnlySession;
    private Session invalidSasSession;

    @BeforeClass
    public static void initClass() {
        CONNECTION_FACTORY = new ServiceBusJmsConnectionFactory(TestUtils.CONNECTION_STRING_BUILDER, TestUtils.CONNECTION_SETTINGS);
        SEND_ONLY_CF = new ServiceBusJmsConnectionFactory(TestUtils.SEND_ONLY_CONNECTION_STRING_BUILDER, TestUtils.CONNECTION_SETTINGS);
        LISTEN_ONLY_CF = new ServiceBusJmsConnectionFactory(TestUtils.LISTEN_ONLY_CONNECTION_STRING_BUILDER, TestUtils.CONNECTION_SETTINGS);
        INVALID_SAS_CF = new ServiceBusJmsConnectionFactory(TestUtils.BAD_CONNECTION_STRING_BUILDER, TestUtils.CONNECTION_SETTINGS);
        MANAGEMENT_CLIENT = new ManagementClientAsync(TestUtils.CONNECTION_STRING_BUILDER);
        QUEUE_DESCRIPTION = MANAGEMENT_CLIENT.createQueueAsync("ExistingQueue" + UUID.randomUUID().toString()).join();
        TOPIC_DESCRIPTION = MANAGEMENT_CLIENT.createTopicAsync("ExistingTopic" + UUID.randomUUID().toString()).join();
        SUBSCRIPTION_DESCRIPTION = AuthorizationTests.MANAGEMENT_CLIENT.createSubscriptionAsync(TOPIC_DESCRIPTION.getPath(), "ExistingSub" + UUID.randomUUID().toString()).join();
    }

    @AfterClass
    public static void suiteCleanup() throws IOException {
        CompletableFuture.allOf(
            MANAGEMENT_CLIENT.deleteQueueAsync(QUEUE_DESCRIPTION.getPath()),
            MANAGEMENT_CLIENT.deleteTopicAsync(TOPIC_DESCRIPTION.getPath())
        ).join();
        MANAGEMENT_CLIENT.close();
    }

    @Before
    public void initTest() throws JMSException {
        connection = CONNECTION_FACTORY.createConnection();
        sendOnlyConnection = SEND_ONLY_CF.createConnection();
        listenOnlyConnection = LISTEN_ONLY_CF.createConnection();
        invalidSasConnection = INVALID_SAS_CF.createConnection();
        
        connection.setClientID(UUID.randomUUID().toString());
        sendOnlyConnection.setClientID(UUID.randomUUID().toString());
        listenOnlyConnection.setClientID(UUID.randomUUID().toString());
        invalidSasConnection.setClientID(UUID.randomUUID().toString());
        
        session = connection.createSession();
        sendOnlySession = sendOnlyConnection.createSession();
        listenOnlySession = listenOnlyConnection.createSession();
        invalidSasSession = invalidSasConnection.createSession();
    }

    @After
    public void testCleanup() throws JMSException {
        if (session != null) session.close();
        if (sendOnlySession != null) sendOnlySession.close();
        if (listenOnlySession != null) listenOnlySession.close();
        if (invalidSasSession != null) invalidSasSession.close();
        if (connection != null) connection.close();
        if (sendOnlyConnection != null) sendOnlyConnection.close();
        if (listenOnlyConnection != null) listenOnlyConnection.close();
        if (invalidSasConnection != null) invalidSasConnection.close();
    }

    @Test
    public void QueueAuthorizationTests() throws JMSException {
        Queue existingSendOnlyQueue = sendOnlySession.createQueue(QUEUE_DESCRIPTION.getPath());
        Queue existingListenOnlyQueue = listenOnlySession.createQueue(QUEUE_DESCRIPTION.getPath());
        Queue existingInvalidSasQueue = invalidSasSession.createQueue(QUEUE_DESCRIPTION.getPath());
        Queue newSendOnlyQueue = sendOnlySession.createQueue("SendOnlyTestQueue");
        Queue newListenOnlyQueue = listenOnlySession.createQueue("ListenOnlyTestQueue");
        Queue newInvalidSasQueue = invalidSasSession.createQueue("InvalidSasQueue");
        
        assertCreateLinkSucceeds("Creating JMS producer on existing destination with send only SAS key.", 
                sendOnlySession, existingSendOnlyQueue, true);
        
        assertCreateLinkFails("Creating JMS producer on non-existing destination with send only SAS key.", 
                sendOnlySession, newSendOnlyQueue, true);
        assertFalse("Queue should not have been created.", MANAGEMENT_CLIENT.queueExistsAsync(newSendOnlyQueue.getQueueName()).join());
        
//        TODO: this will cause the gateway swtPrincipal authorization to hang instead of throwing unauthorized exception
//        assertCreateLinkFails("Creating JMS producer on existing destination with listen only SAS key.", 
//                listenOnlySession, existingListenOnlyQueue, true);
        
        assertCreateLinkFails("Creating JMS producer on non-existing destination with listen only SAS key.", 
                listenOnlySession, newListenOnlyQueue, true);
        assertFalse("Queue should not have been created.", MANAGEMENT_CLIENT.queueExistsAsync(newListenOnlyQueue.getQueueName()).join());
        
        assertCreateLinkFails("Creating JMS producer on existing destination with invalid SAS key.", 
                invalidSasSession, existingInvalidSasQueue, true);
        
        assertCreateLinkFails("Creating JMS producer on non-existing destination with invalid SAS key.", 
                invalidSasSession, newInvalidSasQueue, true);
        assertFalse("Queue should not have been created.", MANAGEMENT_CLIENT.queueExistsAsync(newInvalidSasQueue.getQueueName()).join());
        
//      TODO: this will cause the gateway swtPrincipal authorization to hang instead of throwing unauthorized exception
//        assertCreateLinkFails("Creating JMS consumer on existing destination with send only SAS key.", 
//                sendOnlySession, existingSendOnlyQueue, false);
        
        assertCreateLinkFails("Creating JMS consumer on non-existing destination with send only SAS key.", 
                sendOnlySession, newSendOnlyQueue, false);
        assertFalse("Queue should not have been created.", MANAGEMENT_CLIENT.queueExistsAsync(newSendOnlyQueue.getQueueName()).join());
      
        assertCreateLinkSucceeds("Creating JMS consumer on existing destination with listen only SAS key.", 
                listenOnlySession, existingListenOnlyQueue, false);
        
        assertCreateLinkFails("Creating JMS consumer on non-existing destination with listen only SAS key.", 
                listenOnlySession, newListenOnlyQueue, false);
        assertFalse("Queue should not have been created.", MANAGEMENT_CLIENT.queueExistsAsync(newListenOnlyQueue.getQueueName()).join());
        
        assertCreateLinkFails("Creating JMS consumer on existing destination with invalid SAS key.", 
                invalidSasSession, existingInvalidSasQueue, false);
        
        assertCreateLinkFails("Creating JMS consumer on non-existing destination with invalid SAS key.", 
                invalidSasSession, newInvalidSasQueue, false);
        assertFalse("Queue should not have been created.", MANAGEMENT_CLIENT.queueExistsAsync(newInvalidSasQueue.getQueueName()).join());
    }
    
    @Test
    public void TopicAuthorizationTests() throws JMSException {
        Topic existingSendOnlyTopic = sendOnlySession.createTopic(TOPIC_DESCRIPTION.getPath());
        Topic existingListenOnlyTopic = listenOnlySession.createTopic(TOPIC_DESCRIPTION.getPath());
        Topic existingInvalidSasTopic = invalidSasSession.createTopic(TOPIC_DESCRIPTION.getPath());
        Topic newSendOnlyTopic = sendOnlySession.createTopic("SendOnlyTestTopic");
        Topic newListenOnlyTopic = listenOnlySession.createTopic("ListenOnlyTestTopic");
        Topic newInvalidSasTopic = invalidSasSession.createTopic("InvalidSasTopic");
        
        assertCreateLinkSucceeds("Creating JMS producer on existing destination with send only SAS key.", 
                sendOnlySession, existingSendOnlyTopic, true);
        
        assertCreateLinkFails("Creating JMS producer on non-existing destination with send only SAS key.", 
                sendOnlySession, newSendOnlyTopic, true);
        assertFalse("Topic should not have been created.", MANAGEMENT_CLIENT.topicExistsAsync(newSendOnlyTopic.getTopicName()).join());
        
//        TODO: this will cause the gateway swtPrincipal authorization to hang instead of throwing unauthorized exception
//        assertCreateLinkFails("Creating JMS producer on existing destination with listen only SAS key.", 
//                listenOnlySession, existingListenOnlyTopic, true);
        
        assertCreateLinkFails("Creating JMS producer on non-existing destination with listen only SAS key.", 
                listenOnlySession, newListenOnlyTopic, true);
        assertFalse("Topic should not have been created.", MANAGEMENT_CLIENT.topicExistsAsync(newListenOnlyTopic.getTopicName()).join());
        
        assertCreateLinkFails("Creating JMS producer on existing destination with invalid SAS key.", 
                invalidSasSession, existingInvalidSasTopic, true);
        
        assertCreateLinkFails("Creating JMS producer on non-existing destination with invalid SAS key.", 
                invalidSasSession, newInvalidSasTopic, true);
        assertFalse("Topic should not have been created.", MANAGEMENT_CLIENT.topicExistsAsync(newInvalidSasTopic.getTopicName()).join());
    }
    
    @Test
    public void TemporaryQueueAuthorizationTests() throws JMSException { 
        assertCreateTemporaryQueueFails("Creating TemporaryQueue with send only SAS key.", sendOnlySession);
        assertCreateTemporaryQueueFails("Creating TemporaryQueue with listen only SAS key.", sendOnlySession);
        assertCreateTemporaryQueueFails("Creating TemporaryQueue with invalid SAS key.", invalidSasSession);
        
        System.out.println("Creating a TemporaryQueue with valid SAS key.");
        TemporaryQueue tempQueue = session.createTemporaryQueue();
        
        assertCreateLinkSucceeds("Creating TemporaryQueue producer with send only SAS key",
                sendOnlySession, tempQueue, true);
        
//      TODO: this will cause the gateway swtPrincipal authorization to hang instead of throwing unauthorized exception
//        assertCreateLinkFails("Creating TemporaryQueue producer with listen only SAS key", 
//                listenOnlySession, tempQueue, true);
        
        assertCreateLinkFails("Creating TemporaryQueue producer with invalid SAS key", 
                invalidSasSession, tempQueue, true);
        
        tempQueue.delete();
    }
    
    @Test
    public void TemporaryTopicAuthorizationTests() throws JMSException { 
        assertCreateTemporaryQueueFails("Creating TemporaryQueue with send only SAS key.", sendOnlySession);
        assertCreateTemporaryQueueFails("Creating TemporaryQueue with listen only SAS key.", sendOnlySession);
        assertCreateTemporaryQueueFails("Creating TemporaryQueue with invalid SAS key.", invalidSasSession);
        
        TemporaryQueue tempQueue = session.createTemporaryQueue();
        
        assertCreateLinkSucceeds("Creating TemporaryQueue producer with send only SAS key",
                sendOnlySession, tempQueue, true);
        
//      TODO: this will cause the gateway swtPrincipal authorization to hang instead of throwing unauthorized exception
//        assertCreateLinkFails("Creating TemporaryQueue producer with listen only SAS key", 
//                listenOnlySession, tempQueue, true);
        
        assertCreateLinkFails("Creating TemporaryQueue producer with invalid SAS key", 
                invalidSasSession, tempQueue, true);
        
        tempQueue.delete();
    }
    
    @Test
    public void DurableSubscriptionAuthorizationTests() throws JMSException {
//        TODO: this will cause the gateway swtPrincipal authorization to hang instead of throwing unauthorized exception
//        assertCreateDurableSubscriptionFails("Creating a durable subscriber with send only SAS key", sendOnlySession);
//        assertCreateDurableSubscriptionFails("Creating a durable subscriber with listen only SAS key", listenOnlySession);
        assertCreateDurableSubscriptionFails("Creating a durable subscriber with invalid SAS key", invalidSasSession);
    }
    
    @Test
    public void NonDurableSubscriptionAuthorizationTests() throws JMSException {
        Topic topic = session.createTopic(TOPIC_DESCRIPTION.getPath());
//        TODO: this will cause the gateway swtPrincipal authorization to hang instead of throwing unauthorized exception
//        assertCreateLinkFails("Creating a non-durable subscriber with send only SAS key", sendOnlySession, topic, false);
//        assertCreateLinkFails("Creating a non-durable subscriber with listen only SAS key", listenOnlySession, topic, false);
        assertCreateLinkFails("Creating a non-durable subscriber with invalid SAS key", invalidSasSession, topic, false);
    }
    
    private void assertCreateLinkSucceeds(String message, Session session, Destination dest, boolean isProducer) throws JMSException {
        System.out.println(message);
        if (isProducer) {
            MessageProducer producer = session.createProducer(dest);
            producer.close();
        } else {
            MessageConsumer consumer = session.createConsumer(dest);
            consumer.close();
        }
    }
    
    private void assertCreateLinkFails(String message, Session session, Destination dest, boolean isProducer) {
        System.out.println(message);
        try {
            if (isProducer) {
                session.createProducer(dest);
            } else {
                session.createConsumer(dest);
            }
            fail("Test failed: " + message);
        } catch (Exception e) {
            assertEquals(JMSSecurityException.class, e.getClass());
        }
    }
    
    private void assertCreateTemporaryQueueFails(String message, Session session) {
        System.out.println(message);
        try {
            session.createTemporaryQueue();
            fail("Test failed: " + message);
        } catch (Exception e) {
            assertEquals(JMSSecurityException.class, e.getClass());
        }
    }
    
    private void assertCreateDurableSubscriptionFails(String message, Session session) {
        System.out.println(message);
        try {
            Topic topic = session.createTopic(TOPIC_DESCRIPTION.getPath());
            session.createDurableConsumer(topic, UUID.randomUUID().toString());
            fail("Test failed: " + message);
        } catch (Exception e) {
            assertEquals(JMSSecurityException.class, e.getClass());
        }
    }
}
