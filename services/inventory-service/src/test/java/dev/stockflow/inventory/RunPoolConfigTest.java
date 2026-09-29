package dev.stockflow.inventory;

import dev.stockflow.inventory.adapter.JdbcRunInventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunPoolConfigTest {
  @Test void rejectsUnboundedPoolSizesBeforeConnecting() {
    assertThrows(IllegalArgumentException.class,
        () -> new JdbcRunInventory("", "", "", "", "", 0));
    assertThrows(IllegalArgumentException.class,
        () -> new JdbcRunInventory("", "", "", "", "", 17));
  }
}
