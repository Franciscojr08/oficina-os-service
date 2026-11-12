package br.com.oficina.os.apipublica.usecase;

import br.com.oficina.os.apipublica.AcompanharOrdemServicoPublicaResponse;
import br.com.oficina.os.apipublica.AcompanharOrdemServicoPublicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AcompanharOrdemServicoPublicaUseCase {

	private final AcompanharOrdemServicoPublicaService service;

	public AcompanharOrdemServicoPublicaResponse acompanharPorCodigo(String codigo) {
		return service.acompanharPorCodigo(codigo);
	}
}
