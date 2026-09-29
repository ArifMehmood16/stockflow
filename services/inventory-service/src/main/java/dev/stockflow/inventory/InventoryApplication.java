package dev.stockflow.inventory;

import dev.stockflow.inventory.application.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InventoryApplication {
  public static void main(String[] args) {
    SpringApplication.run(InventoryApplication.class, args);
  }

  @Bean
  StockLookup stockLookup(StockRepository repository) {
    return new StockLookup(repository);
  }
}
