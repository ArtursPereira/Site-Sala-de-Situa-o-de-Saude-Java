package com.example.nss.analytics.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.nss.analytics.api.response.AnalyticsResponse;
import com.example.nss.analytics.api.response.MetadataResponse;
import com.example.nss.analytics.application.EpidemiologyService;
import com.example.nss.analytics.domain.Geography;
import java.util.List;
import org.junit.jupiter.api.Test;

class EpidemiologyControllerTest {
  private final EpidemiologyService service = mock(EpidemiologyService.class);
  private final EpidemiologyController controller = new EpidemiologyController(service);
  private final EpidemiologyController.FilterRequest request =
      new EpidemiologyController.FilterRequest(
          "DENG", 2024, 3, "F", "20_39", "3301009", "CG_DIST_SEDE");

  @Test
  void regionAndStateDelegateToAggregatedResponses() {
    AnalyticsResponse response = mock(AnalyticsResponse.class);
    when(service.query(Geography.REGION, request.toFilter())).thenReturn(response);
    when(service.query(Geography.STATE, request.toFilter())).thenReturn(response);

    assertEquals(response, controller.region(request));
    assertEquals(response, controller.state(request));
    verify(service).query(Geography.REGION, request.toFilter());
    verify(service).query(Geography.STATE, request.toFilter());
  }

  @Test
  void geographicEndpointsDelegateToTheMatchingServiceScope() {
    AnalyticsResponse response = mock(AnalyticsResponse.class);
    when(service.query(Geography.MUNICIPALITY, request.toFilter())).thenReturn(response);
    when(service.query(Geography.DISTRICT, request.toFilter())).thenReturn(response);
    when(service.query(Geography.NEIGHBORHOOD, request.toFilter())).thenReturn(response);

    assertEquals(response, controller.municipalities(request));
    assertEquals(response, controller.districts(request));
    assertEquals(response, controller.neighborhoods(request));
    verify(service).query(Geography.MUNICIPALITY, request.toFilter());
    verify(service).query(Geography.DISTRICT, request.toFilter());
    verify(service).query(Geography.NEIGHBORHOOD, request.toFilter());
  }

  @Test
  void diseasesAndMetadataUseTypedResponses() {
    MetadataResponse metadata =
        new MetadataResponse(
            List.of(2024), java.util.Map.of("2024", List.of(3)), List.of(), List.of(), List.of());
    when(service.diseases()).thenReturn(List.of("DENG"));
    when(service.metadata()).thenReturn(metadata);

    assertEquals(List.of("DENG"), controller.diseases().items());
    assertEquals(metadata, controller.metadata());
  }
}
