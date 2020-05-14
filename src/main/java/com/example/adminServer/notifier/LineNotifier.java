package com.example.adminServer.notifier;

import com.example.adminServer.config.LineProperties;
import de.codecentric.boot.admin.server.domain.entities.Instance;
import de.codecentric.boot.admin.server.domain.entities.InstanceRepository;
import de.codecentric.boot.admin.server.domain.events.InstanceEvent;
import de.codecentric.boot.admin.server.domain.events.InstanceStatusChangedEvent;
import de.codecentric.boot.admin.server.notify.AbstractEventNotifier;
import de.codecentric.boot.admin.server.notify.LoggingNotifier;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


@Slf4j
@Component
public class LineNotifier extends AbstractEventNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingNotifier.class);

    @Autowired
    private LineProperties lineProperties;

    private static final String DEFAULT_MESSAGE = "#{application.name} (#{application.id}) is #{to.status}";
    private final SpelExpressionParser parser = new SpelExpressionParser();

    private Expression message;
    private List<String> notifyStatuses = Arrays.asList("UP", "DOWN", "OFFLINE");


    public LineNotifier(InstanceRepository repository) {
        super(repository);
    }



    @Override
    protected Mono<Void> doNotify(InstanceEvent event, Instance instance) {
        if (lineProperties.isEnabled() == false) {
            return null;
        }

        this.message = parser.parseExpression(DEFAULT_MESSAGE, ParserContext.TEMPLATE_EXPRESSION);
        return Mono.fromRunnable(() -> {
//            if ( event instanceof InstanceRegisteredEvent ) {
//                InstanceRegisteredEvent registeredEvent = (InstanceRegisteredEvent) event;
//            }

            if (event instanceof InstanceStatusChangedEvent) {

                LOGGER.info("Instance {} ({}) is {}", instance.getRegistration().getName(), event.getInstance(),
                        ((InstanceStatusChangedEvent) event).getStatusInfo().getStatus());
                String input = instance.getRegistration().getName() + " - " + event.getInstance() + " is " +((InstanceStatusChangedEvent) event).getStatusInfo().getStatus() ;
                pushNotification( input );
            } else {
                LOGGER.info("Instance {} ({}) {}", instance.getRegistration().getName(), event.getInstance(),
                        event.getType());
            }
        });


    }



    private void pushNotification(String input) {

//        String msg = message.getValue(event, String.class); // boot-test (2a87974b) is UP
        String msg = input ;
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        //headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
        headers.add("Authorization", String.format("%s %s", "Bearer", lineProperties.getChannelToken()));



        HashMap object = new HashMap<>();
        object.put("to", lineProperties.getTo());
        List messages = new ArrayList();
        HashMap message = new HashMap<>();
        message.put("type", "text");
        message.put("text", msg);
        messages.add(message);
        object.put("messages", messages);

        HttpEntity<HashMap> entity = new HttpEntity<HashMap>(object, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "https://api.line.me/v2/bot/message/push",
                HttpMethod.POST, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            System.out.println(response.getBody());
        }
    }
}
