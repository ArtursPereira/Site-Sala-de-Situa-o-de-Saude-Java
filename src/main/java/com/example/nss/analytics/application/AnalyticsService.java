package com.example.nss.analytics.application;

import com.example.nss.analytics.api.response.CasoResumoResponse;
import com.example.nss.analytics.infrastructure.repository.AnalyticsRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
  private final AnalyticsRepository repository;

  public List<CasoResumoResponse> buscarCasos(Integer ano, Integer mes) {
    return repository.buscarCasos(ano, mes).stream()
        .map(
            caso ->
                new CasoResumoResponse(
                    caso.codigoDoenca(),
                    caso.ano(),
                    caso.mes(),
                    caso.codigoMunicipio(),
                    caso.codigoUnidade(),
                    caso.classificacao(),
                    caso.evolucao(),
                    caso.sexo(),
                    caso.semanaNotificacao(),
                    caso.anoNascimento(),
                    caso.totalCasos()))
        .toList();
  }
}
