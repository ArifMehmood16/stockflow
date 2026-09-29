package dev.stockflow.inventory.adapter;

import dev.stockflow.inventory.application.*;
import dev.stockflow.inventory.domain.Stock;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public final class JdbcStockRepository implements StockRepository {
  private final DataSource dataSource;

  public JdbcStockRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Optional<Stock> find(String code) {
    try (var connection = dataSource.getConnection();
        var query =
            connection.prepareStatement(
                "SELECT i.code,c.product_name,i.on_hand,i.version,i.tenant_id,i.bucket FROM"
                    + " stockflow.inventory i JOIN stockflow.catalog c ON c.code=i.code WHERE"
                    + " i.code=?")) {
      query.setQueryTimeout(2);
      query.setString(1, code);
      try (var row = query.executeQuery()) {
        if (!row.next()) return Optional.empty();
        return Optional.of(
            new Stock(
                row.getString(1),
                row.getString(2),
                row.getInt(3),
                row.getLong(4),
                row.getInt(5),
                row.getInt(6)));
      }
    } catch (SQLException error) {
      throw new StockUnavailable(error);
    }
  }

  @Override
  public boolean ready() {
    // Resolve the required columns and privileges, without scanning/counting the large catalog.
    try (var connection = dataSource.getConnection();
        var query =
            connection.prepareStatement(
                "SELECT i.code,c.product_name,i.on_hand,i.version,i.tenant_id,i.bucket FROM"
                    + " stockflow.inventory i JOIN stockflow.catalog c ON c.code=i.code LIMIT 1")) {
      query.setQueryTimeout(2);
      try (var result = query.executeQuery()) {
        return result.next();
      }
    } catch (SQLException error) {
      return false;
    }
  }
}
