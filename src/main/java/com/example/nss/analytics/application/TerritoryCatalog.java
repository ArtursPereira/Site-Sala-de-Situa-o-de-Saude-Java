package com.example.nss.analytics.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class TerritoryCatalog {
  private static final String DISTRICT = "DISTRICT";
  private static final String NEIGHBORHOOD = "NEIGHBORHOOD";
  private static final String MUNICIPALITY = "MUNICIPALITY";
  private static final String NEIGHBORHOOD_OR_LOCALITY = "NEIGHBORHOOD_OR_LOCALITY";
  private static final int MINIMUM_TERRITORY_COLUMNS = 6;
  private static final int MINIMUM_MUNICIPALITIES = 5570;
  private final Map<String, Entry> entries;
  private final Map<String, String> municipalities;

  public TerritoryCatalog() {
    this.municipalities = loadMunicipalities();
    this.entries = load();
  }

  public List<Entry> forGeography(String municipalityCode, String districtCode, String geography) {
    if (DISTRICT.equals(geography))
      return entries.values().stream()
          .filter(
              e ->
                  DISTRICT.equals(e.type())
                      && Objects.equals(municipalityCode, e.municipalityCode()))
          .toList();
    if (NEIGHBORHOOD.equals(geography))
      return entries.values().stream()
          .filter(
              e ->
                  NEIGHBORHOOD_OR_LOCALITY.equals(e.type())
                      && Objects.equals(municipalityCode, e.municipalityCode())
                      && Objects.equals(districtCode, e.parentDistrictId()))
          .toList();
    if (MUNICIPALITY.equals(geography))
      return municipalities.entrySet().stream()
          .filter(e -> municipalityCode == null || municipalityCode.equals(e.getKey()))
          .map(
              e -> new Entry(e.getKey(), e.getValue(), MUNICIPALITY, e.getKey(), null, "AVAILABLE"))
          .toList();
    return List.of();
  }

  public Optional<Entry> find(String id) {
    return Optional.ofNullable(entries.get(id));
  }

  private Map<String, Entry> load() {
    Map<String, Entry> result = new LinkedHashMap<>();
    readTsv("territories/campos.tsv", result);
    readTsv("territories/national-intramunicipal.tsv", result);
    return Map.copyOf(result);
  }

  private Map<String, String> loadMunicipalities() {
    Map<String, String> result = new LinkedHashMap<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new ClassPathResource("territories/national-municipalities.tsv").getInputStream(),
                StandardCharsets.UTF_8))) {
      reader.readLine();
      while (true) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        String[] columns = line.split("\\t", -1);
        if (columns.length >= 2 && columns[0].matches("\\d{7}") && !columns[1].isBlank()) {
          result.put(columns[0], columns[1]);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Catálogo nacional de municípios não disponível", e);
    }
    if (result.size() < MINIMUM_MUNICIPALITIES) {
      throw new IllegalStateException(
          "Catálogo nacional de municípios incompleto: " + result.size());
    }
    return Map.copyOf(result);
  }

  private void readTsv(String resource, Map<String, Entry> result) {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8))) {
      reader.readLine();
      while (true) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        String[] p = line.split("\\t", -1);
        if (p.length >= MINIMUM_TERRITORY_COLUMNS && !p[0].isBlank()) {
          result.putIfAbsent(
              p[0], new Entry(p[0], p[1], p[2], p[3], p[4].isBlank() ? null : p[4], p[5]));
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Catálogo territorial não disponível: " + resource, e);
    }
  }

  public record Entry(
      String territoryId,
      String name,
      String type,
      String municipalityCode,
      String parentDistrictId,
      String geometryStatus) {}
}
