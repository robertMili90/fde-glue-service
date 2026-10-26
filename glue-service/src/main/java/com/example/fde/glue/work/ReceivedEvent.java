package com.example.fde.glue.work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "received_event", schema = "glue")
class ReceivedEvent implements Persistable<String> {
  // Assigned IDs must still use insert semantics for new instances.
  @Transient private boolean newEntity = true;

  @Id
  @Column(length = 255)
  private String id;

  @Column(nullable = false, length = 100)
  private String eventType;

  @Column(nullable = false, length = 255)
  private String paymentIntentId;

  @Column(nullable = false)
  private Instant receivedAt;

  @Lob
  @Column(nullable = false)
  private String payload;

  protected ReceivedEvent() {}

  ReceivedEvent(
      String stripeEventId,
      String eventType,
      String paymentIntentId,
      String rawBody,
      Instant receivedAt) {
    this.id = stripeEventId;
    this.eventType = eventType;
    this.paymentIntentId = paymentIntentId;
    this.payload = rawBody;
    this.receivedAt = receivedAt;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  private void markPersisted() {
    newEntity = false;
  }
}
