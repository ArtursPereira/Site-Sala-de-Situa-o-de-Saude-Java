package com.example.nss.analytics.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.nss.analytics.api.response.CasoResumoResponse;
import com.example.nss.analytics.infrastructure.repository.AnalyticsRepository;
import com.example.nss.analytics.infrastructure.repository.CasoResumo;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {
  @Test
  void delegatesCaseSearchToTheRepository() {
    AnalyticsRepository repository = mock(AnalyticsRepository.class);
    AnalyticsService service = new AnalyticsService(repository);
    List<CasoResumo> repositoryResult =
        List.of(
            new CasoResumo(
                "DENG", 2024, 3, "3301009", "UNIT-1", "CONFIRMED", "OPEN", "F", 10, 1980, 2));
    List<CasoResumoResponse> expected =
        List.of(
            new CasoResumoResponse(
                "DENG", 2024, 3, "3301009", "UNIT-1", "CONFIRMED", "OPEN", "F", 10, 1980, 2));
    when(repository.buscarCasos(2024, 3)).thenReturn(repositoryResult);

    assertEquals(expected, service.buscarCasos(2024, 3));
    verify(repository).buscarCasos(2024, 3);
  }
}
