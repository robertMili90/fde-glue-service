package com.example.fde.glue.work;

import org.springframework.data.repository.Repository;

interface ReceivedEventRepository extends Repository<ReceivedEvent, String> {
  boolean existsById(String stripeEventId);

  ReceivedEvent save(ReceivedEvent receivedEvent);
}
