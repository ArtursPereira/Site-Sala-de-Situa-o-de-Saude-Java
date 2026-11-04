package com.example.nss.analytics.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.example.nss.analytics.exception.InvalidFilterException;
import org.junit.jupiter.api.Test;

class EpidemiologyFilterTest {
  @Test
  void omittedSexMeansAllIsRepresentable() {
    assertNull(new EpidemiologyFilter("DENG", null, null, null, null, null, null).sex());
  }

  @Test
  void acceptsAgeBandAndPeriodOmission() {
    assertDoesNotThrow(
        () -> new EpidemiologyFilter("DENG", null, null, "F", "20_39", "3301009", null));
  }

  @Test
  void acceptsACompleteValidFilter() {
    assertDoesNotThrow(
        () -> new EpidemiologyFilter("DENG", 2026, 1, "M", "20_39", "3301009", "CG_DIST_SEDE"));
  }

  @Test
  void acceptsNationalAreaScopes() {
    var filter = new EpidemiologyFilter("DENG", 2026, 1, null, null, null, null, "33", "SE");

    assertEquals("33", filter.ufCode());
    assertEquals("SE", filter.regionCode());
  }

  @Test
  void rejectsInvalidMonth() {
    assertThrows(
        InvalidFilterException.class,
        () -> new EpidemiologyFilter("DENG", 2026, 13, null, null, null, null));
  }

  @Test
  void rejectsNonPositiveYear() {
    assertThrows(
        InvalidFilterException.class,
        () -> new EpidemiologyFilter("DENG", 0, null, null, null, null, null));
  }

  @Test
  void rejectsInvalidSex() {
    assertThrows(
        InvalidFilterException.class,
        () -> new EpidemiologyFilter("DENG", 2026, 1, "I", null, null, null));
  }

  @Test
  void rejectsInvalidAgeBand() {
    assertThrows(
        InvalidFilterException.class,
        () -> new EpidemiologyFilter("DENG", 2026, 1, null, "UNKNOWN", null, null));
  }

  @Test
  void rejectsBlankDiseaseAndDistrict() {
    assertThrows(
        InvalidFilterException.class,
        () -> new EpidemiologyFilter(" ", 2026, 1, null, null, null, null));
    assertThrows(
        InvalidFilterException.class,
        () -> new EpidemiologyFilter("DENG", 2026, 1, null, null, null, " "));
  }

  @Test
  void rejectsInvalidMunicipalityCode() {
    assertThrows(
        InvalidFilterException.class,
        () -> new EpidemiologyFilter("DENG", 2026, 1, null, null, "x", null));
  }

  @Test
  void rejectsInvalidNationalAreaScopes() {
    assertThrows(
        InvalidFilterException.class,
        () -> new EpidemiologyFilter("DENG", 2026, 1, null, null, null, null, "3", null));
    assertThrows(
        InvalidFilterException.class,
        () -> new EpidemiologyFilter("DENG", 2026, 1, null, null, null, null, null, "SUDESTE"));
  }
}
