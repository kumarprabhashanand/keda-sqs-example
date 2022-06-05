package com.keda.example.receiver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.keda.example.model.MessageModel;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@ApplicationScoped
public class SqsSampleMessageReceiver implements Runnable {

    Logger log = Logger.getLogger(SqsSampleMessageReceiver.class);

    static ObjectReader MESSAGE_READER = new ObjectMapper().readerFor(MessageModel.class);

    private final ExecutorService scheduler = Executors.newSingleThreadExecutor();

    @Inject
    private SqsClient sqsClient;

    @ConfigProperty(name = "queue.url")
    private String queueUrl;

    void onStart(@Observes StartupEvent ev) {
        log.info("onStart : "+ev);
        scheduler.submit(this);
    }

    void onStop(@Observes ShutdownEvent ev) {
        log.info("onStop : "+ev);
        scheduler.shutdown();
    }

    @Override
    public void run() {
        log.info("running listener");
        while(true) {
            List<Message> messages = sqsClient.receiveMessage(m -> m.maxNumberOfMessages(1).queueUrl(queueUrl)).messages();
            messages.stream().map(s -> {
                try {
                    String messageBody = s.body();
                    MessageModel m = toModel(messageBody);
                    sqsClient.deleteMessage(d -> d.queueUrl(queueUrl).receiptHandle(s.receiptHandle()));
                    return m;
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    return null;
                }
            }).collect(Collectors.toList());
        }
    }

    private MessageModel toModel(String s) throws JsonProcessingException {
        log.info("message to be parsed : "+s);
        MessageModel  m = MESSAGE_READER.readValue(s);
        return m;
    }
}