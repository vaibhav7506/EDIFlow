package com.vaibhav.ediflow.order.consumer;

import com.vaibhav.ediflow.events.*;import com.vaibhav.ediflow.order.domain.OrderStatus;import com.vaibhav.ediflow.order.repository.PurchaseOrderRepository;import com.vaibhav.ediflow.order.service.DeadLetterPublisher;import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.kafka.core.KafkaTemplate;import org.springframework.kafka.test.context.EmbeddedKafka;import org.springframework.test.annotation.DirtiesContext;import java.math.BigDecimal;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.*;import static org.awaitility.Awaitility.await;

@SpringBootTest(properties={"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}","spring.kafka.listener.auto-startup=true","spring.datasource.url=jdbc:h2:mem:ediflow-kafka;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@EmbeddedKafka(partitions=3,topics={"ediflow.po.parsed.v1","ediflow.invoice.parsed.v1","ediflow.dlq.v1","ediflow.ingestion.retry.v1"})
@DirtiesContext
class KafkaPipelineTest {
 @Autowired KafkaTemplate<String,Object> kafka;@Autowired PurchaseOrderRepository orders;@MockBean DeadLetterPublisher deadLetterPublisher;
 @Test void canonicalKafkaEventIsConsumedAndPersisted(){String code="SENDERID";UUID partner=UUID.nameUUIDFromBytes(("ediflow-partner:"+code).getBytes(StandardCharsets.UTF_8));var lines=List.of(new CanonicalLine(1,"SKU-KAFKA",new BigDecimal("2"),"EA",new BigDecimal("5.00")));var payload=new CanonicalPayload("PO-KAFKA",LocalDate.of(2026,8,28),null,null,null,null,new BigDecimal("10.00"),lines);var event=new CanonicalEdiEvent(UUID.randomUUID(),partner,code,DocumentType.PO_850,"000009999","9999","9999",Instant.now(),payload);kafka.send("ediflow.po.parsed.v1",event.partitionKey(),event);await().atMost(java.time.Duration.ofSeconds(10)).until(()->orders.findFirstByPoNumberAndStatusOrderByCreatedAtDesc("PO-KAFKA",OrderStatus.PERSISTED).isPresent());}
}
