package kafka.tutorial1;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ConsumerDemoWithThread {
    public static void main(String[] args) {
        ConsumerDemoWithThread consumerDemoWithThread = new ConsumerDemoWithThread();
        consumerDemoWithThread.run();
    }

    private ConsumerDemoWithThread() {

    }

    private void run() {
        System.out.println("Working");
        Logger logger = LoggerFactory.getLogger(ConsumerThread.class.getName());
        String groupId = "my_third_group";
        String topic = "first_topic";
        String bootstrapServer = "127.0.0.1:9092";
        CountDownLatch latch = new CountDownLatch(1);
        Runnable myConsumerRunnable = new ConsumerThread(latch, bootstrapServer, groupId, topic);
        Thread myThread = new Thread(myConsumerRunnable);
        myThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            logger.info("Caught shutdown hook" );
            ((ConsumerThread) myConsumerRunnable).shutDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Application exited");
        }));
        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.info("Application got interrupted", e);
        }  finally {
            logger.info("Shut down application");
        }
    }

    public class ConsumerThread implements Runnable {
        private CountDownLatch latch;
        private KafkaConsumer<String, String> consumer;
        private Logger logger = LoggerFactory.getLogger(ConsumerThread.class.getName());
        public ConsumerThread(CountDownLatch latch,
                              String bootstrapServer,
                              String groupId,
                              String topic) {
            this.latch = latch;
            Properties properties = new Properties();
            properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
            properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
            consumer = new KafkaConsumer<String, String>(properties);
        }
        @Override
        public void run() {
            try {
                while(true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record: records) {
                        logger.info("Key: " + record.key() + "\n" +
                                "Value: " + record.value() + "\n" +
                                "Partition: " + record.partition() + "\n" +
                                "Offset: " + record.offset() + "\n"
                        );
                    }
                }
            } catch (WakeupException e) {
                logger.info("Received shutdown signal");
            } finally {
                consumer.close();
                // tell main code that we are done with the consumer!
                latch.countDown();
            }
        }

        public void shutDown() {
            // this wakeup() is a special method to interrupt the consumer.poll()
            // it throws WakeUpException
            consumer.wakeup();
        }
    }
}
