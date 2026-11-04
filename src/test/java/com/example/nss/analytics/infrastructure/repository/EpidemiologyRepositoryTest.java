package com.example.nss.analytics.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.example.nss.analytics.application.TerritoryCatalog;
import com.example.nss.analytics.domain.EpidemiologyFilter;
import com.example.nss.analytics.domain.Geography;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EpidemiologyRepositoryTest {
  @Test
  void rejectsUnsafeConfigurableColumnNames() {
    var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()));

    assertThrows(
        IllegalArgumentException.class,
        () -> new EpidemiologyRepository(jdbc, "year;DROP TABLE fato_casos", "month"));
  }

  @Test
  void invalidIdsAndStatusesNeverCountAsMapped() {
    var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
    jdbc.execute("CREATE SCHEMA analytics");
    jdbc.execute(
        "CREATE TABLE analytics.fato_casos (cd_mun VARCHAR, notification_district_id VARCHAR, notification_neighborhood_id VARCHAR, notification_territory_status VARCHAR, cases_total INT, cd_sexo VARCHAR, batch_id VARCHAR, disease_codigo VARCHAR, ano INT, mes INT)");
    jdbc.execute(
        "CREATE TABLE analytics.pipeline_current_publication (disease_codigo VARCHAR, ano INT, cd_mun VARCHAR, batch_id VARCHAR, source_extracted_timestamp TIMESTAMP, added_at TIMESTAMP)");
    jdbc.update(
        "INSERT INTO analytics.pipeline_current_publication VALUES ('DENG', 2024, '3301009', 'test-batch', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    var catalog = new TerritoryCatalog();
    String district = "CG_DIST_SEDE";
    String neighborhood =
        catalog.forGeography("3301009", district, "NEIGHBORHOOD").getFirst().territoryId();
    Object[][] rows = {
      {district, neighborhood, "NOTIFICATION_NEIGHBORHOOD", 3},
      {district, null, "NOTIFICATION_DISTRICT_ONLY", 2},
      {"NaN", "NaN", "UNMAPPED_NOTIFICATION_UNIT", 7},
      {"UNKNOWN", null, "NOTIFICATION_DISTRICT_ONLY", 11},
      {district, neighborhood, "UNMAPPED_NOTIFICATION_UNIT", 13},
      {null, null, "UNMAPPED_NOTIFICATION_UNIT", 17}
    };
    for (Object[] row : rows)
      jdbc.update(
          "INSERT INTO analytics.fato_casos VALUES ('3301009', ?, ?, ?, ?, 'F', 'test-batch', 'DENG', 2024, 3)",
          row);
    var repository = new EpidemiologyRepository(jdbc, "ano", "mes");
    var filter = new EpidemiologyFilter(null, null, null, null, null, "3301009", null);
    var districtIds =
        catalog.forGeography("3301009", null, "DISTRICT").stream()
            .map(TerritoryCatalog.Entry::territoryId)
            .toList();
    var totals = repository.totals(filter, Geography.DISTRICT, districtIds);
    assertEquals(53L, totals.total());
    assertEquals(5L, totals.mapped());
    assertEquals(1, repository.aggregate(filter, Geography.DISTRICT, districtIds).size());
    assertEquals(0, repository.aggregate(filter, Geography.DISTRICT, List.of()).size());
    var neighborhoods = new EpidemiologyFilter(null, null, null, null, null, "3301009", district);
    var neighborhoodIds =
        catalog.forGeography("3301009", district, "NEIGHBORHOOD").stream()
            .map(TerritoryCatalog.Entry::territoryId)
            .toList();
    assertEquals(
        3L, repository.totals(neighborhoods, Geography.NEIGHBORHOOD, neighborhoodIds).mapped());

    assertEquals(1, repository.aggregate(filter, Geography.REGION, List.of("N")).size());
    assertEquals(1, repository.aggregate(filter, Geography.STATE, List.of("33")).size());
    for (String region : List.of("N", "NE", "SE", "S", "CO")) {
      var nationalState = new EpidemiologyFilter(null, null, null, null, null, null, null, null, region);
      assertEquals(region.equals("SE") ? 1 : 0, repository.aggregate(nationalState, Geography.STATE, List.of("33")).size());
    }
    var scopedMunicipalities = new EpidemiologyFilter(null, null, null, null, null, null, null, "33", null);
    assertEquals(1, repository.aggregate(scopedMunicipalities, Geography.MUNICIPALITY, List.of("3301009")).size());
  }
}
