package demo;

import org.apache.activemq.ActiveMQXAConnectionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    MappingJackson2MessageConverter mappingJackson2MessageConverter() {
        return new MappingJackson2MessageConverter();
    }

    @Bean
    JmsMessagingTemplate jmsMessagingTemplate(
            JmsTemplate jmsTemplate, MappingJackson2MessageConverter mappingJackson2MessageConverter) {
        JmsMessagingTemplate jmsMessagingTemplate = new JmsMessagingTemplate(jmsTemplate);
        jmsMessagingTemplate.setJmsMessageConverter(mappingJackson2MessageConverter);
        return jmsMessagingTemplate;
    }

    @Bean
    JdbcDataSource xaDataSource() {
        JdbcDataSource xaDataSource = new JdbcDataSource();
        xaDataSource.setURL("jdbc:h2:tcp://localhost/~/javaone");
        xaDataSource.setUser("sa");
        xaDataSource.setPassword("");
        return xaDataSource;
    }

    @Bean
    ActiveMQXAConnectionFactory xaConnectionFactory() {
        return new ActiveMQXAConnectionFactory("tcp://127.0.0.1:61616");
    }

    @Configuration
    @EnableWebSocketMessageBroker
    static class WebSocketConfiguration extends AbstractWebSocketMessageBrokerConfigurer {

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            registry.addEndpoint("/monitor").withSockJS();
        }

        @Override
        public void configureMessageBroker(MessageBrokerRegistry registry) {
            registry.enableStompBrokerRelay("/topic");
        }
    }
}

@Transactional
@Controller
class ReservationController {

    private final ReservationRepository reservationRepository;

    @Autowired
    public ReservationController(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @RequestMapping("/")
    public String reservations(Model model) {
        model.addAttribute("reservations", this.reservationRepository.findAll());
        return "reservations";
    }
}

/**
 * Post data like <CODE> {  "firstName" : "Josh" , "lastName" : "Long" }</CODE>
 */
@Transactional
@RestController
@RequestMapping("/reservations")
class ReservationRestController {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final JmsMessagingTemplate jmsTemplate;
    private final ReservationRepository reservationRepository;

    @Autowired
    public ReservationRestController(JmsMessagingTemplate jmsTemplate,
                                     SimpMessagingTemplate simpMessagingTemplate,
                                     ReservationRepository reservationRepository) {
        this.jmsTemplate = jmsTemplate;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.reservationRepository = reservationRepository;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{reservationId}")
    public Reservation findByReservationId(@PathVariable Long reservationId) {
        return this.reservationRepository.findOne(reservationId);
    }

    @RequestMapping(method = RequestMethod.GET)
    public Collection<Reservation> findByFirstName(@RequestParam Optional<String> firstName) {
        return firstName.map(reservationRepository::findByFirstName).orElseGet(reservationRepository::findAll);
    }

    @RequestMapping(method = RequestMethod.DELETE)
    public void delete() {
        reservationRepository.deleteAllInBatch();
    }

    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<Void> create(@RequestBody Reservation r) {
        Reservation reservation = this.reservationRepository.save(r);
        this.jmsTemplate.convertAndSend("reservation", reservation);
        this.simpMessagingTemplate.convertAndSend("/topic/alarms", reservation);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/reservations/{id}").buildAndExpand(r.getId()).toUri();
        return ResponseEntity.created(location).build();
    }
}

interface ReservationRepository extends JpaRepository<Reservation, Long> {
    Collection<Reservation> findByFirstName(String fn);
}

@Entity
class Reservation {

    Reservation() { // JPA
    }

    public Reservation(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    public String toString() {
        return "Reservation{" + "id=" + id + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + '}';
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    @Id
    @GeneratedValue
    private Long id;

    private String firstName, lastName;
}