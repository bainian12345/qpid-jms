package com.microsoft.azure.servicebus.jms;

import com.microsoft.azure.servicebus.management.ManagementClientAsync;
import com.microsoft.azure.servicebus.management.SubscriptionDescription;
import com.microsoft.azure.servicebus.management.TopicDescription;
import javax.jms.*;

import org.junit.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class SubscriptionTests {
    private static ConnectionFactory CONNECTION_FACTORY;
    private static ManagementClientAsync managementClient;
    private static String entityName;
    private Connection connection = null;
    private Session session = null;
    private MessageProducer producer = null;
    private MessageConsumer consumer = null;
    private Topic topic = null;
    private String clientID;

    @BeforeClass
    public static void initConnectionFactory() {
        //CONNECTION_FACTORY = new JmsConnectionFactory("admin", "admin", "amqp://localhost:5673?amqp.traceFrames=true");
        CONNECTION_FACTORY = new ServiceBusJmsConnectionFactory(TestUtils.CONNECTION_STRING, TestUtils.CONNECTION_SETTINGS);
        managementClient = new ManagementClientAsync(TestUtils.CONNECTION_STRING_BUILDER);
    }

    @Before
    public void init() throws JMSException {
        connection = CONNECTION_FACTORY.createConnection();
        clientID = "client1";
        System.out.println("Setting connection clientID = " + clientID);
        connection.setClientID(clientID);
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
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

    // Creating DurableSubscriber will send AMQP ATTACH frame to broker
    // If the subscription entity does not exist yet, it will be created before link is created
    @Test
    public void createAndDeleteDurableSubscriberTest() throws JMSException, InterruptedException {
        TopicDescription topicDescription = managementClient.createTopicAsync(entityName).join();
        String subscriberSuffix = UUID.randomUUID().toString().substring(0, 5);
        String subscriptionName = "sub1" + subscriberSuffix;
        String serviceSubscriptionName = formatSubscriptionNameForManagementClient(subscriptionName, clientID, true);

        System.out.println("Creating durable subscriber.");
        TopicSubscriber subscriber = session.createDurableSubscriber(topic, subscriptionName);
        subscriber.close();
        assertTrue("The durable subscriber should still exist after close.", managementClient.subscriptionExistsAsync(entityName, serviceSubscriptionName).join());

        System.out.println("The subscription should continue to live after closing the subscriber. Send a message now that gets collected to subscriber");
        producer = session.createProducer(topic);
        TextMessage message = session.createTextMessage("createSubscriptionOnAttachTest");
        producer.send(message);

        System.out.println("Creating the subscriber again which should reopen the same subscription");
        consumer = session.createDurableSubscriber(topic, subscriptionName);

        message = session.createTextMessage("createSubscriptionOnAttachTest-2");
        producer.send(message);

        for (int i = 0; i < 2; i++) {
            Message msg = consumer.receive(10000);
            assertNotNull("Expected to receive a message from consumer.", msg);
            System.out.println("Message received = " + ((TextMessage) msg).getText());
        }

        producer.close();
        consumer.close();
        assertTrue("The durable subscriber should still exist after close.", managementClient.subscriptionExistsAsync(entityName, serviceSubscriptionName).join());
        
        System.out.println("UnSubscribing " + subscriptionName);
        session.unsubscribe(subscriptionName);

        // TODO - Unfortunately, gateway responds back with detach without waiting for broker to finish deleting the subscription.
        // Leaving it as-is now and will have to be fixed later.
        Thread.sleep(2000);

        assertFalse("The durable subscriber should be deleted after unsubscribe.", managementClient.subscriptionExistsAsync(entityName, serviceSubscriptionName).join());
    }

    /**
     * In JMS, multiple connections (with different clientID) can create subscribers to a given topic with same name.
     * They have different clientIDs, hence should work differently.
     */
    @Test
    public void multipleClientIdsCanCreateSameSubscriptionNameUnderSameTopicTest() throws JMSException {
        Connection con2 = null;
        Session session2 = null;
        String subscriberSuffix = UUID.randomUUID().toString().substring(0, 5);
        String sub1Name = "sub1" + subscriberSuffix;
        String sub2Name = "sub2" + subscriberSuffix;
        
        System.out.println("Creating topic by creating a producer");
        producer = session.createProducer(topic);
        
        try {
            System.out.println("Creating durable consumer - client1-sub1");
            MessageConsumer client1sub1Consumer = session.createDurableConsumer(topic, sub1Name);
    
            System.out.println("Creating durable consumer - client1-sub2");
            TopicSubscriber client1sub2Subscriber = session.createDurableSubscriber(topic, sub2Name);

            con2 = CONNECTION_FACTORY.createConnection();
            con2.setClientID("client2");
            con2.start();
            session2 = con2.createSession(false, Session.AUTO_ACKNOWLEDGE);

            System.out.println("Creating durable consumer - client2-sub1");
            TopicSubscriber client2sub1Subscriber = session2.createDurableSubscriber(topic, sub1Name);

            System.out.println("Creating durable consumer - client2-sub2");
            MessageConsumer client2sub2Consumer = session2.createDurableConsumer(topic, sub2Name);

            String requestText = "multipleClientIdsCanCreateSameSubscriptionNameUnderSameTopicTest";
            TextMessage message = session.createTextMessage(requestText);
            producer.send(message);

            TextMessage msg = (TextMessage)client1sub1Consumer.receive(10000);
            TextMessage msg2 = (TextMessage)client1sub2Subscriber.receive(10000);
            TextMessage msg3 = (TextMessage)client2sub1Subscriber.receive(10000);
            TextMessage msg4 = (TextMessage)client2sub2Consumer.receive(10000);

            // If all the messages above are not null, since the consumers are on an AUTO_ACKNOWLEDGE session, they
            // must be coming from different subscribers.
            assertNotNull(msg);
            assertNotNull(msg2);
            assertNotNull(msg3);
            assertNotNull(msg4);
        } finally {
            if (session2 != null) session2.close();
            if (con2 != null) con2.close();
        }
    }

    @Test
    public void createAndDeleteNonDurableSubscriberTest() throws JMSException {
        TopicDescription topicDescription = managementClient.createTopicAsync(entityName).join();
        System.out.println("Creating a producer");
        producer = session.createProducer(topic);

        List<SubscriptionDescription> subscriptions = managementClient.getSubscriptionsAsync(entityName).join();
        assertTrue("There should be no subscribers under this topic right now.", subscriptions.isEmpty());
        
        System.out.println("Creating temporary subscriber.");
        MessageConsumer consumer = session.createConsumer(topic);

        System.out.println("Retrieving list of subscriptions.");
        // TODO Neeraj: Uncommenting this fails. Needs to be investigated and fixed.
        //subscriptions = managementClient.getSubscriptionsAsync(entityName).join();
        //assertFalse("The temporary subscriber should now exist.", subscriptions.isEmpty());

        System.out.println("Sending a message");
        String requestText = "createTemporarySubscriptionWhileCreatingConsumer";
        TextMessage message = session.createTextMessage(requestText);
        producer.send(message);

        TextMessage msg = (TextMessage)consumer.receive(10000);
        assertNotNull(msg);

        System.out.println("Deleting subscription");
        consumer.close();

        // TODO Neeraj: Uncommenting this fails. Needs to be investigated and fixed.
        //subscriptions = managementClient.getSubscriptionsAsync(entityName).join();
        //assertTrue("The subscriber should be deleted by now.", subscriptions.isEmpty());
    }

    @Test
    public void createNonDurableSubscriberWithDefaultClientId() throws JMSException {
        Connection connection = CONNECTION_FACTORY.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(UUID.randomUUID().toString());
        entityName = topic.getTopicName();

        TopicDescription topicDescription = managementClient.createTopicAsync(entityName).join();

        System.out.println("Creating a producer");
        producer = session.createProducer(topic);

        System.out.println("Creating temporary subscriber.");
        MessageConsumer consumer = session.createConsumer(topic);

        System.out.println("Sending a message");
        String requestText = "createTemporarySubscriptionWhileCreatingConsumer";
        TextMessage message = session.createTextMessage(requestText);
        producer.send(message);

        TextMessage msg = (TextMessage)consumer.receive(10000);
        assertNotNull(msg);

        System.out.println("Deleting subscription");
        consumer.close();
        // todo neeraj - Assert that subscription was deleted.

        session.close();
        connection.close();
    }

    @Test
    public void deleteSubscriberUsingRestShouldReflectInClientManager() throws JMSException {
        TopicDescription topicDescription = managementClient.createTopicAsync(entityName).join();

        System.out.println("Creating a producer");
        producer = session.createProducer(topic);
        System.out.println("Sending message1");
        String requestText = "deleteSubscriberUsingRestShouldReflectInClientManager_msg1";
        TextMessage message = session.createTextMessage(requestText);
        producer.send(message);


        String subscriberSuffix = UUID.randomUUID().toString().substring(0, 5);
        String subscriptionName = "sub1" + subscriberSuffix;

        System.out.println("Creating subscriber.");
        TopicSubscriber subscriber = session.createDurableSubscriber(topic, subscriptionName);

        String subscriberNameForManagementClient = formatSubscriptionNameForManagementClient(subscriptionName, this.clientID, true);

        assertTrue("The durable subscriber should exist.", managementClient.subscriptionExistsAsync(entityName, subscriberNameForManagementClient).join());

        subscriber.close();

        System.out.println("Deleting subscription - " + subscriberNameForManagementClient);

        managementClient.deleteSubscriptionAsync(entityName, subscriberNameForManagementClient).join();
        assertFalse("The durable subscriber should not exist.", managementClient.subscriptionExistsAsync(entityName, subscriberNameForManagementClient).join());

        System.out.println("Creating subscriber again. This should create a new subscriber");
        subscriber = session.createDurableSubscriber(topic, subscriptionName);

        assertTrue("The durable subscriber should be recreated.", managementClient.subscriptionExistsAsync(entityName, subscriberNameForManagementClient).join());

        System.out.println("Sending message2");
        requestText = "deleteSubscriberUsingRestShouldReflectInClientManager_msg2";
        message = session.createTextMessage(requestText);
        producer.send(message);

        TextMessage msg = (TextMessage)subscriber.receive(10000);
        assertNotNull(msg);
        assertEquals("Only the second message should be received.", requestText, msg.getText());
    }

    @Test
    public void subscriberSelectorFilterTest() throws JMSException {
        TopicDescription topicDescription = managementClient.createTopicAsync(entityName).join();

        System.out.println("Creating a producer");
        producer = session.createProducer(topic);

        String subscriberSuffix = UUID.randomUUID().toString().substring(0, 5);
        String subscriptionName = "sub1" + subscriberSuffix;

        System.out.println("Creating subscriber.");
        TopicSubscriber subscriber = session.createDurableSubscriber(topic, subscriptionName, "year < 1980", false);
        System.out.println("Created subscriber.");
        subscriber.close();

        System.out.println("Sending message");
        String requestText = "subscriberSelectorFilterTest_msg1";
        Message message = session.createTextMessage(requestText);
        message.setIntProperty("year", 1900);
        producer.send(message);

        System.out.println("Sending message2");
        String requestText2 = "subscriberSelectorFilterTest_msg2";
        Message message2 = session.createTextMessage(requestText);
        message2.setIntProperty("year", 2000);
        producer.send(message2);

        System.out.println("Creating subscriber again.");
        subscriber = session.createDurableSubscriber(topic, subscriptionName, "year < 1980", false);
        System.out.println("Created subscriber.");

        TextMessage msg = (TextMessage)subscriber.receive(10000);
        assertNotNull(msg);
        assertEquals("The filter should be honored, and the subscription should not be recreated on second attach.", requestText, msg.getText());

        msg = (TextMessage)subscriber.receive(1000);
        assertNull(msg);
    }

    @Test
    public void subscriberWithNullClientIdTest() throws JMSException, InterruptedException {
        Connection connection = CONNECTION_FACTORY.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        topic = session.createTopic(UUID.randomUUID().toString());
        entityName = topic.getTopicName();

        TopicDescription topicDescription = managementClient.createTopicAsync(entityName).join();

        System.out.println("Creating a producer");
        producer = session.createProducer(topic);

        String subscriberSuffix = UUID.randomUUID().toString().substring(0, 5);
        String subscriptionName = "sub1" + subscriberSuffix;

        System.out.println("Creating SharedDurableConsumer");
        consumer = session.createSharedDurableConsumer(topic, subscriptionName);
        String subscriberNameForManagementClient = formatSubscriptionNameForManagementClient(subscriptionName, "", true);
        assertTrue("The shared durable subscriber should be recreated.",
                managementClient.subscriptionExistsAsync(entityName, subscriberNameForManagementClient).join());
        consumer.close();
        System.out.println("Unsubscribing SharedDurableConsumer");
        session.unsubscribe(subscriptionName);
        Thread.sleep(2000);
        assertFalse("The subscriber should be cleaned up.",
                managementClient.subscriptionExistsAsync(entityName, subscriberNameForManagementClient).join());

        System.out.println("Creating non-durable SharedConsumer");
        consumer = session.createSharedConsumer(topic, subscriptionName);
        subscriberNameForManagementClient = formatSubscriptionNameForManagementClient(subscriptionName, "", false);
        assertTrue("The shared subscriber should be recreated.",
                managementClient.subscriptionExistsAsync(entityName, subscriberNameForManagementClient).join());
        consumer.close();
        Thread.sleep(2000);
        assertFalse("The subscriber should be cleaned up.",
                managementClient.subscriptionExistsAsync(entityName, subscriberNameForManagementClient).join());

        System.out.println("Creating non-durable Consumer");
        consumer = session.createConsumer(topic);
        subscriberNameForManagementClient = formatSubscriptionNameForManagementClient(subscriptionName, "", false);
        assertEquals("The subscriber should be recreated.",
                1,
                managementClient.getSubscriptionsAsync(entityName).join().size());
        consumer.close();
        Thread.sleep(2000);
        assertEquals("The subscriber should be cleaned up.",
                0,
                managementClient.getSubscriptionsAsync(entityName).join().size());
    }

    String formatSubscriptionNameForManagementClient(String subscriptionName, String clientId, boolean isDurable) {
        if (isDurable) {
            return subscriptionName + "$" + clientId + "$D";
        } else {
            return subscriptionName + "$" + clientId + "$ND";
        }
    }
}
