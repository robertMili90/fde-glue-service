package com.example.fde.erp.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "idempotency_result", schema = "erp")
public class IdempotencyResult implements Persistable<String> {
  // Assigned IDs must still use insert semantics for new instances.
  @Transient private boolean newEntity = true;

  @Id
  @Column(length = 255)
  private String operationKey;

  @Lob
  @Column(nullable = false)
  private String payload;

  @Lob
  @Column(nullable = false)
  private String result;

  protected IdempotencyResult() {}

  IdempotencyResult(String operationKey, String payload, String result) {
    this.operationKey = operationKey;
    this.payload = payload;
    this.result = result;
  }

  boolean matches(String candidatePayload) {
    return payload.equals(candidatePayload);
  }

  String result() {
    return result;
  }

  @Override
  public String getId() {
    return operationKey;
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
