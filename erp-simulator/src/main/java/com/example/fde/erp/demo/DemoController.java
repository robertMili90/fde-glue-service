package com.example.fde.erp.demo;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DemoController {
  private final DemoControls controls;

  DemoController(DemoControls controls) {
    this.controls = controls;
  }

  @PostMapping("/demo/faults")
  ResponseEntity<Void> fault(@Valid @RequestBody FaultRequest request) {
    controls.configure(request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/demo/state")
  DemoState state() {
    return controls.state();
  }

  @PostMapping("/demo/reset")
  ResponseEntity<Void> reset() {
    controls.reset();
    return ResponseEntity.noContent().build();
  }
}
