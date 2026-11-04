package com.example.nss.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.nss.analytics.domain.Geography;
import com.example.nss.analytics.infrastructure.repository.DiseaseRow;
import com.example.nss.analytics.infrastructure.repository.EpidemiologyRepository;
import com.example.nss.analytics.infrastructure.repository.EpidemiologyTotals;
import com.example.nss.analytics.infrastructure.repository.MetadataRow;
import com.example.nss.analytics.infrastructure.repository.TerritoryAggregate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EpidemiologyControllerIntegrationIT {
  @Autowired MockMvc mockMvc;

  @MockitoBean EpidemiologyRepository repository;

  @Test
  void returnsMunicipalityAnalyticsThroughTheRealService() throws Exception {
    when(repository.totals(any(), eq(Geography.MUNICIPALITY), any()))
        .thenReturn(new EpidemiologyTotals(8L, 5L));
    when(repository.aggregate(any(), eq(Geography.MUNICIPALITY), any()))
        .thenReturn(List.of(new TerritoryAggregate("3301009", 5L)));

    mockMvc
        .perform(
            get("/api/v1/epidemiology/municipalities")
                .with(user("analyst@nss.test").roles("USER"))
                .param("disease", "DENG")
                .param("year", "2026")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalNotifications").value(8))
        .andExpect(jsonPath("$.coverage.status").value("PARTIAL"))
        .andExpect(jsonPath("$.coverage.unmappedNotificationsTotal").value(3));
  }

  @Test
  void returnsUnavailableForAnUnsupportedNeighborhoodDrilldown() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/epidemiology/neighborhoods")
                .with(user("analyst@nss.test").roles("USER"))
                .param("municipalityCode", "3302403"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalNotifications").doesNotExist())
        .andExpect(jsonPath("$.coverage.status").value("UNAVAILABLE"));

    verifyNoInteractions(repository);
  }

  @Test
  void exposesDiseasesAndMetadataThroughTheRealController() throws Exception {
    when(repository.diseases()).thenReturn(List.of(new DiseaseRow("DENG", "Dengue")));
    when(repository.metadata()).thenReturn(List.of(new MetadataRow(2026, 1)));

    mockMvc
        .perform(get("/api/v1/diseases").with(user("analyst@nss.test").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0]").value("DENG"));
    mockMvc
        .perform(get("/api/v1/metadata").with(user("analyst@nss.test").roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableYears[0]").value(2026));
  }
}
