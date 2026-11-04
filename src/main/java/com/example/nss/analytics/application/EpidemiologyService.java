package com.example.nss.analytics.application;

import com.example.nss.analytics.api.response.AnalyticsResponse;
import com.example.nss.analytics.api.response.MetadataResponse;
import com.example.nss.analytics.domain.CoverageStatus;
import com.example.nss.analytics.domain.EpidemiologyFilter;
import com.example.nss.analytics.domain.Geography;
import com.example.nss.analytics.infrastructure.repository.EpidemiologyRepository;
import com.example.nss.analytics.infrastructure.repository.EpidemiologyTotals;
import com.example.nss.analytics.infrastructure.repository.MetadataRow;
import com.example.nss.analytics.infrastructure.repository.PublicationMetadataRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EpidemiologyService {
  private final EpidemiologyRepository repository;
  private final TerritoryCatalog catalog;

  public AnalyticsResponse query(Geography geography, EpidemiologyFilter filter) {
    if (geography == Geography.NEIGHBORHOOD && filter.districtCode() == null) {
      return response(geography, filter, null, CoverageStatus.UNAVAILABLE, 0L, 0L, List.of(), null);
    }
    List<TerritoryCatalog.Entry> territories =
        catalog.forGeography(filter.municipalityCode(), filter.districtCode(), geography.name());
    List<String> mappedIds = territories.stream().map(TerritoryCatalog.Entry::territoryId).toList();
    EpidemiologyTotals totals = repository.totals(filter, geography, mappedIds);
    long total = totals.total();
    long mapped = totals.mapped();
    List<AnalyticsResponse.TerritoryItem> items = new ArrayList<>();
    Map<String, Long> aggregates = new HashMap<>();
    repository
        .aggregate(filter, geography, mappedIds)
        .forEach(row -> aggregates.put(row.code(), row.total()));
    for (TerritoryCatalog.Entry entry : territories) {
      items.add(
          new AnalyticsResponse.TerritoryItem(
              entry.territoryId(),
              entry.name(),
              aggregates.getOrDefault(entry.territoryId(), 0L),
              entry.parentDistrictId()));
    }
    if (geography == Geography.MUNICIPALITY && filter.municipalityCode() == null) {
      items = new ArrayList<>();
      for (TerritoryCatalog.Entry entry : catalog.forGeography(null, null, geography.name()))
        items.add(
            new AnalyticsResponse.TerritoryItem(
                entry.territoryId(),
                entry.name(),
                aggregates.getOrDefault(entry.territoryId(), 0L),
                null));
    }
    items.sort(Comparator.comparing(AnalyticsResponse.TerritoryItem::code));
    CoverageStatus status =
        total == 0 || mapped == total ? CoverageStatus.AVAILABLE : CoverageStatus.PARTIAL;
    return response(geography, filter, total, status, mapped, total - mapped, items, repository.publicationMetadata(filter));
  }

  public AnalyticsResponse unavailable(Geography geography, EpidemiologyFilter filter) {
    return response(geography, filter, null, CoverageStatus.UNAVAILABLE, 0L, 0L, List.of(), null);
  }

  public List<String> diseases() {
    return repository.diseases().stream().map(row -> row.code()).toList();
  }

  public MetadataResponse metadata() {
    Map<Integer, List<Integer>> grouped =
        repository.metadata().stream()
            .collect(
                Collectors.groupingBy(
                    MetadataRow::year,
                    LinkedHashMap::new,
                    Collectors.mapping(MetadataRow::month, Collectors.toList())));
    return new MetadataResponse(
        new ArrayList<>(grouped.keySet()),
        grouped.entrySet().stream()
            .collect(
                Collectors.toMap(
                    entry -> String.valueOf(entry.getKey()),
                    Map.Entry::getValue,
                    (left, right) -> left,
                    LinkedHashMap::new)),
        List.of("MUNICIPALITY", "DISTRICT", "NEIGHBORHOOD"),
        new ArrayList<>(EpidemiologyFilter.AGE_BANDS),
        List.of("M", "F", "ALL"));
  }

  private AnalyticsResponse response(
      Geography geography,
      EpidemiologyFilter f,
      Long total,
      CoverageStatus status,
      long mapped,
      long unmapped,
      List<AnalyticsResponse.TerritoryItem> items,
      PublicationMetadataRow publication) {
    return new AnalyticsResponse(
        "notifications",
        geography,
        new AnalyticsResponse.FilterResponse(
            f.disease(),
            f.year(),
            f.month(),
            f.sex(),
            f.ageBand(),
            f.municipalityCode(),
            f.districtCode()),
        total,
        new AnalyticsResponse.Coverage(status, mapped, unmapped),
        items,
        publication == null ? null : publication.sourceExtractedAt(),
        publication == null ? null : publication.addedAt());
  }
}
