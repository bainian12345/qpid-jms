package com.microsoft.azure.servicebus.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.microsoft.azure.servicebus.management.ManagementClientAsync;

public class TemporaryQueueTests {
    private static final long DEFAULT_TIMEOUT = 3000;
    private static ConnectionFactory CONNECTION_FACTORY;
    private static ManagementClientAsync managementClient;
    private Connection connection = null;
    private Session session = null;
    private MessageConsumer consumer = null;
    private MessageProducer producer = null;
    private TemporaryQueue tempQueue = null;
    private String entityName;
    
    @BeforeClass
    public static void initConnectionFactory() {
        CONNECTION_FACTORY = new ServiceBusJmsConnectionFactory(TestUtils.CONNECTION_STRING_BUILDER, TestUtils.CONNECTION_SETTINGS);
        managementClient = new ManagementClientAsync(TestUtils.CONNECTION_STRING_BUILDER);
    }
    
    @Before
    public void init() throws JMSException {
        connection = CONNECTION_FACTORY.createConnection();
        connection.start();
        session = connection.createSession(false, 1);
        tempQueue = session.createTemporaryQueue();
        entityName = tempQueue.getQueueName();
    }
    
    @After
    public void testCleanup() throws JMSException {
        if (consumer != null) consumer.close();
        if (producer != null) producer.close();
        if (session != null) session.close();
        if (connection != null) connection.close();
    }
    
    @AfterClass
    public static void suiteCleanup() throws IOException {
        managementClient.close();
    }
    
    @Test
    public void createTempQueue() throws JMSException {
        assertTrue("Entity should now exit. Entity name: " + entityName, managementClient.queueExistsAsync(entityName).join());
        consumer = session.createConsumer(tempQueue);
        producer = session.createProducer(tempQueue);
        System.out.println("Successfully created links to TemporaryQueue.");
    }
    
    @Test
    public void createMultipleTemporaryQueue() throws JMSException {
        int numOfTempQueues = 255; // 255 is the current limit of links per session set by our gateway
        CompletableFuture<?>[] tempQueues = new CompletableFuture<?>[numOfTempQueues];
        Instant start = Instant.now();
        
        for (int i = 0; i < numOfTempQueues; i++) {
            tempQueues[i] = CompletableFuture.runAsync(() -> {
                try {
                    TemporaryQueue tempQueue = session.createTemporaryQueue();
                    assertTrue("Temporary queues should now exist after its creation.", managementClient.queueExistsAsync(tempQueue.getQueueName()).join());
                } catch (JMSException e) {
                    fail("Creating multiple temporary queues failed: " + e.getMessage());
                }
            });
        }
        
        CompletableFuture.allOf(tempQueues).join();
        long timeTaken = Instant.now().getEpochSecond() - start.getEpochSecond();
        System.out.println("Created and verified " + numOfTempQueues + " temporary queues in " + timeTaken + " seconds.");
    }
    
    // TemporaryQueue should be deleted implicitly after connection closes
    @Test
    public void deleteTempQueueOnConnectionClose() throws JMSException, InterruptedException {
        assertTrue("Entity should now exit. Entity name: " + entityName, managementClient.queueExistsAsync(entityName).join());
        consumer = session.createConsumer(tempQueue);
        connection.close();
        connection = null; // stop connection from closing again in cleanup
        Thread.sleep(1000); // deleting temp queue on connection close is fire and forget (as of Oct 16, 2019), wait for it to finish
        assertFalse("TemporaryQueue should be deleted implicitly. Entity name: " + entityName, managementClient.queueExistsAsync(entityName).join());
    }
    
    @Test
    public void deleteTempQueue() throws JMSException, InterruptedException {
        assertTrue("Entity should now exit. Entity name: " + entityName, managementClient.queueExistsAsync(entityName).join());
        tempQueue.delete();
        assertFalse("TemporaryQueue should be deleted. Entity name: " + entityName, managementClient.queueExistsAsync(entityName).join());
    }
    
    @Test
    public void sendReceiveTest() throws JMSException {
        consumer = session.createConsumer(tempQueue);
        producer = session.createProducer(tempQueue);
        System.out.println("Sending and receiving links created.");
        
        String requestText = "This is the request message.";
        TextMessage message = session.createTextMessage(requestText);
        System.out.println("Sending message...");
        producer.send(message);
        System.out.println("Message sent.");
        System.out.println("Receiving message...");
        TextMessage receivedMessage = (TextMessage) consumer.receive(DEFAULT_TIMEOUT);
        assertEquals(message, receivedMessage);
        System.out.println("Message received");
    }
}
