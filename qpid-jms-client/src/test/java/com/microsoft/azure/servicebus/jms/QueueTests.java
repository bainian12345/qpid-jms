package com.microsoft.azure.servicebus.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.UUID;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.microsoft.azure.servicebus.management.ManagementClientAsync;

public class QueueTests {
    private static final long DEFAULT_TIMEOUT = 3000;
    private static ConnectionFactory CONNECTION_FACTORY;
    private static ManagementClientAsync managementClient;
    private Connection connection = null;
    private Session session = null;
    private MessageConsumer consumer = null;
    private MessageProducer producer = null;
    private Queue queue = null;
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
        queue = session.createQueue(UUID.randomUUID().toString());
        entityName = queue.getQueueName();
    }
    
    @After
    public void testCleanup() throws JMSException {
        if (consumer != null) consumer.close();
        if (producer != null) producer.close();
        if (session != null) session.close();
        if (connection != null) connection.close();
        if (managementClient.queueExistsAsync(entityName).join()) managementClient.deleteQueueAsync(entityName).join();
    }
    
    @AfterClass
    public static void suiteCleanup() throws IOException {
        managementClient.close();
    }
    
    // Creating consumer or producer will send AMQP ATTACH frame to broker
    // If the queue entity does not exist yet, it will be created before link is created
    @Test
    public void createQueueOnAttach() throws JMSException {
        assertFalse("Entity should not exit before the test. Entity name: " + entityName, managementClient.queueExistsAsync(entityName).join());
        System.out.println("Attaching consumer and producer to queue when it does not exist yet.");
        consumer = session.createConsumer(queue);
        producer = session.createProducer(queue);
        assertTrue("Entity should now exit after the AMQP Attach. Entity name: " + entityName, managementClient.queueExistsAsync(entityName).join());
        consumer.close();
        producer.close();
        
        assertTrue("Entity should still exit after the AMQP Attach. Entity name: " + entityName, managementClient.queueExistsAsync(entityName).join());
        System.out.println("Attaching consumer and producer to queue after it's created.");
        consumer = session.createConsumer(queue);
        producer = session.createProducer(queue);
        System.out.println("Consumer and Producer attached.");
    }
    
    @Test
    public void sendReceiveTest() throws JMSException {
        consumer = session.createConsumer(queue);
        producer = session.createProducer(queue);
        System.out.println("Sending and receiving links created.");
        
        String requestText = "This is the request message.";
        TextMessage message = session.createTextMessage(requestText);
        System.out.println("Sending message...");
        producer.send(message);
        System.out.println("Message sent.");
        System.out.println("Receiving message...");
        TextMessage receivedMessage = (TextMessage) consumer.receive(DEFAULT_TIMEOUT);
        assertEquals(message, receivedMessage);
    }
    
    // Queue object created from different connections with the same name should point to the same queue within the broker
    @Test
    public void receiveFromDifferentConnectionTest() throws JMSException {
        assertFalse("Entity should not exit before the test. Entity name: " + entityName, managementClient.queueExistsAsync(entityName).join());
        System.out.println("Attaching consumer and producer to queue when it does not exist yet.");
        producer = session.createProducer(queue);
        assertTrue("Entity should now exit after the AMQP Attach. Entity name: " + entityName, managementClient.queueExistsAsync(entityName).join());
        
        System.out.println("Sending message to the queue created in first connection.");
        TextMessage sentMsg = session.createTextMessage("My Message");
        producer.send(sentMsg);
        
        Connection connection2 = null;
        Session session2 = null;
        MessageConsumer consumer2 = null;
        try {
            connection2 = CONNECTION_FACTORY.createConnection();
            session2 = connection2.createSession();
            
            System.out.println("Creating a queue object with the same name from another connection.");
            Queue queue2 = session2.createQueue(entityName);
            consumer2 = session.createConsumer(queue2);
            
            System.out.println("Receiving message from a queue object with the same name from another connection.");
            Message receivedMsg = consumer2.receive(1000);
            assertEquals("The message should have been received by consumer in second connection attached to the same queue", sentMsg, receivedMsg);
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            consumer2.close();
            session2.close();
            connection2.close();
        }
    }
}
