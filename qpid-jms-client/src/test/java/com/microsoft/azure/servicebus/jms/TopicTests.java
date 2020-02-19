package com.microsoft.azure.servicebus.jms;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.UUID;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Topic;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.microsoft.azure.servicebus.management.ManagementClientAsync;

public class TopicTests {
    private static ConnectionFactory CONNECTION_FACTORY;
    private static ManagementClientAsync managementClient;
    private Connection connection = null;
    private Session session = null;
    private MessageProducer producer = null;
    private Topic topic = null;
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
        topic = session.createTopic(UUID.randomUUID().toString());
        entityName = topic.getTopicName();
    }
    
    @After
    public void testCleanup() throws JMSException {
        if (producer != null) producer.close();
        if (session != null) session.close();
        if (connection != null) connection.close();
        if (managementClient.topicExistsAsync(entityName).join()) managementClient.deleteTopicAsync(entityName).join();
    }
    
    @AfterClass
    public static void suiteCleanup() throws IOException {
        managementClient.close();
    }
    
    // Creating producer will send AMQP ATTACH frame to broker
    // If the topic entity does not exist yet, it will be created before link is created
    @Test
    public void createTopicOnAttach() throws JMSException {
        assertFalse("Entity should not exit before the test. Entity name: " + entityName, managementClient.topicExistsAsync(entityName).join());
        System.out.println("Attaching producer to topic when it does not exist yet.");
        producer = session.createProducer(topic);
        assertTrue("Entity should now exit after the AMQP Attach. Entity name: " + entityName, managementClient.topicExistsAsync(entityName).join());
        producer.close();
        
        assertTrue("Entity should still exist after the AMQP Attach. Entity name: " + entityName, managementClient.topicExistsAsync(entityName).join());
        System.out.println("Attaching producer to topic after it's created.");
        producer = session.createProducer(topic);
        System.out.println("Producer attached.");
    }
    
    @Test
    public void sendTest() throws JMSException {
        producer = session.createProducer(topic);
        System.out.println("Sending link created.");
        
        String requestText = "This is the request message.";
        TextMessage message = session.createTextMessage(requestText);
        System.out.println("Sending message...");
        producer.send(message);
        System.out.println("Message sent.");
    }
}
