package br.com.oficina.os.apipublica;

import static br.com.oficina.os.shared.exception.ExceptionMessage.SERVICE_ORDER_NOT_FOUND;

import br.com.oficina.os.ordemservico.application.gateway.OrdemServicoGateway;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.shared.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AcompanharOrdemServicoPublicaService {

    private final OrdemServicoGateway ordemServicoGateway;

    public AcompanharOrdemServicoPublicaResponse acompanharPorCodigo(String codigo) {
        OrdemServico ordemServico = ordemServicoGateway.findByCodigo(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException(SERVICE_ORDER_NOT_FOUND));

        return toResponse(ordemServico);
    }

    private AcompanharOrdemServicoPublicaResponse toResponse(OrdemServico ordemServico) {
        return new AcompanharOrdemServicoPublicaResponse(
                ordemServico.getCodigo(),
                ordemServico.getDescricaoProblema(),
                ordemServico.getStatus(),
                ordemServico.getDataCadastro(),
                ordemServico.getDataInicioExecucao(),
                ordemServico.getDataFimExecucao(),
                ordemServico.getDataEntregue(),
                ordemServico.getDataCancelada()
        );
    }
}
