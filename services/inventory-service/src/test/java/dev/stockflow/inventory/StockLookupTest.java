package dev.stockflow.inventory;

import static org.junit.jupiter.api.Assertions.*;

import dev.stockflow.inventory.application.*;
import dev.stockflow.inventory.domain.Stock;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StockLookupTest {
  static final Stock ITEM = new Stock("001234", "Test product", 17, 3, 12, 12);

  static final class Repository implements StockRepository {
    int calls;

    public Optional<Stock> find(String code) {
      calls++;
      return code.equals(ITEM.code()) ? Optional.of(ITEM) : Optional.empty();
    }

    public boolean ready() {
      return true;
    }
  }

  @Test
  void readsKnownProductWithoutLosingLeadingZeros() {
    var repo = new Repository();
    assertEquals(ITEM, new StockLookup(repo).find("001234").orElseThrow());
    assertEquals(1, repo.calls);
  }

  @Test
  void rejectsInvalidCodesBeforeDatabaseAccess() {
    var repo = new Repository();
    for (String invalid : new String[] {"", "abc", "1 OR 1=1", "1".repeat(33), "１２"})
      assertThrows(IllegalArgumentException.class, () -> new StockLookup(repo).find(invalid));
    assertEquals(0, repo.calls);
  }

  @Test
  void returnsEmptyForUnknownProduct() {
    assertTrue(new StockLookup(new Repository()).find("999").isEmpty());
  }
}
