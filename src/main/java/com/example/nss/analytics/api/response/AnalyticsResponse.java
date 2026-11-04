package com.example.nss.analytics.api.response;

import com.example.nss.analytics.domain.CoverageStatus;
import com.example.nss.analytics.domain.Geography;
import java.time.Instant;
import java.util.List;

public record AnalyticsResponse(
    String metric,
    Geography geography,
    FilterResponse filters,
    Long totalNotifications,
    Coverage coverage,
    List<TerritoryItem> items,
    Instant sourceExtractedAt,
    Instant addedAt) {
  public record FilterResponse(
      String disease,
      Integer year,
      Integer month,
      String sex,
      String ageBand,
      String municipalityCode,
      String districtCode,
      String ufCode,
      String regionCode) {
    public FilterResponse(
        String disease,
        Integer year,
        Integer month,
        String sex,
        String ageBand,
        String municipalityCode,
        String districtCode) {
      this(disease, year, month, sex, ageBand, municipalityCode, districtCode, null, null);
    }
  }

  public record Coverage(
      CoverageStatus status, Long mappedNotificationsTotal, Long unmappedNotificationsTotal) {}

  public record TerritoryItem(
      String code, String name, Long notificationsTotal, String parentDistrictId) {}
}
