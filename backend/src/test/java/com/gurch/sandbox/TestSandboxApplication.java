package com.gurch.sandbox;

import org.springframework.boot.SpringApplication;

public class TestSandboxApplication {

  public static void main(String[] args) {
    SpringApplication.from(SandboxApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
