package com.streamchat.messaging.integration;

import com.streamchat.chat.StreamChatApplication;
import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.port.out.ActivityEventPublisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Clase base para pruebas de integración de mensajería.
 *
 * Levanta contenedores reales de PostgreSQL y RabbitMQ con Testcontainers.
 * Los contenedores son estáticos (Singleton): arrancan una vez y se comparten
 * entre todos los tests para reducir el tiempo de ejecución.
 *
 * Kafka se mockea: estas pruebas verifican RabbitMQ y el relay de outbox.
 * Las pruebas de Kafka van en su propia clase con EmbeddedKafka.
 */
@SpringBootTest(
        classes = StreamChatApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("integration-test")
@Testcontainers
public abstract class MessagingIntegrationBase {

    // Mock de Kafka para que el contexto Spring arranque sin broker Kafka real
    @MockBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, UserActivityEvent> kafkaTemplate;

    @MockBean
    @SuppressWarnings("unused")
    private ActivityEventPublisher activityEventPublisher;

    // ClientNotifier mockeado en la base: los tests que lo necesiten lo inyectan con @Autowired
    // Los que no lo usan simplemente lo ignoran
    @MockBean
    @SuppressWarnings("unused")
    protected com.streamchat.chat.application.port.out.ClientNotifier clientNotifier;

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("streamchat_test")
                    .withUsername("streamchat")
                    .withPassword("streamchat");

    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
                    .withVhost("streamchat")
                    .withUser("streamchat", "streamchat")
                    .withPermission("streamchat", "streamchat", ".*", ".*", ".*");

    static {
        POSTGRES.start();
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",  POSTGRES::getUsername);
        registry.add("spring.datasource.password",  POSTGRES::getPassword);

        // RabbitMQ
        registry.add("spring.rabbitmq.host",         RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port",         RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username",     () -> "streamchat");
        registry.add("spring.rabbitmq.password",     () -> "streamchat");
        registry.add("spring.rabbitmq.virtual-host", () -> "streamchat");

        // Kafka — broker ficticio, no se usa en estas pruebas
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
    }
}
