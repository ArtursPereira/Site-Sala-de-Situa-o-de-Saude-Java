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
import java.util.Set;
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
    List<TerritoryCatalog.Entry> territories = geography == Geography.REGION || geography == Geography.STATE
        ? areaEntries(geography, filter)
        : catalog.forGeography(filter.municipalityCode(), filter.districtCode(), geography.name());
    if (geography == Geography.MUNICIPALITY && filter.ufCode() != null) {
      territories = territories.stream().filter(entry -> entry.territoryId().startsWith(filter.ufCode())).toList();
    }
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
    if (geography == Geography.MUNICIPALITY && filter.municipalityCode() == null && filter.ufCode() == null) {
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
    return response(
        geography,
        filter,
        total,
        status,
        mapped,
        total - mapped,
        items,
        repository.publicationMetadata(filter));
  }

  public AnalyticsResponse unavailable(Geography geography, EpidemiologyFilter filter) {
    return response(geography, filter, null, CoverageStatus.UNAVAILABLE, 0L, 0L, List.of(), null);
  }

  private List<TerritoryCatalog.Entry> areaEntries(Geography geography, EpidemiologyFilter filter) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("N", "Norte"); values.put("NE", "Nordeste"); values.put("CO", "Centro-Oeste"); values.put("SE", "Sudeste"); values.put("S", "Sul");
    if (geography == Geography.REGION) {
      return values.entrySet().stream().map(e -> new TerritoryCatalog.Entry(e.getKey(), e.getValue(), "REGION", e.getKey(), null, "AVAILABLE")).toList();
    }
    Map<String, String> states = Map.ofEntries(
        Map.entry("11", "Rondônia"), Map.entry("12", "Acre"), Map.entry("13", "Amazonas"), Map.entry("14", "Roraima"), Map.entry("15", "Pará"), Map.entry("16", "Amapá"), Map.entry("17", "Tocantins"),
        Map.entry("21", "Maranhão"), Map.entry("22", "Piauí"), Map.entry("23", "Ceará"), Map.entry("24", "Rio Grande do Norte"), Map.entry("25", "Paraíba"), Map.entry("26", "Pernambuco"), Map.entry("27", "Alagoas"), Map.entry("28", "Sergipe"), Map.entry("29", "Bahia"),
        Map.entry("31", "Minas Gerais"), Map.entry("32", "Espírito Santo"), Map.entry("33", "Rio de Janeiro"), Map.entry("35", "São Paulo"), Map.entry("41", "Paraná"), Map.entry("42", "Santa Catarina"), Map.entry("43", "Rio Grande do Sul"),
        Map.entry("50", "Mato Grosso do Sul"), Map.entry("51", "Mato Grosso"), Map.entry("52", "Goiás"), Map.entry("53", "Distrito Federal"));
    return states.entrySet().stream().filter(e -> filter.regionCode() == null || regionOf(e.getKey()).equals(filter.regionCode())).map(e -> new TerritoryCatalog.Entry(e.getKey(), e.getValue(), "STATE", e.getKey(), null, "AVAILABLE")).toList();
  }

  private String regionOf(String uf) {
    if (uf.startsWith("1")) return "N";
    if (uf.startsWith("2")) return "NE";
    if (Set.of("31", "32", "33", "35").contains(uf)) return "SE";
    if (Set.of("41", "42", "43").contains(uf)) return "S";
    return "CO";
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
            f.districtCode(),
            f.ufCode(),
            f.regionCode()),
        total,
        new AnalyticsResponse.Coverage(status, mapped, unmapped),
        items,
        publication == null ? null : publication.sourceExtractedAt(),
        publication == null ? null : publication.addedAt());
  }
}
