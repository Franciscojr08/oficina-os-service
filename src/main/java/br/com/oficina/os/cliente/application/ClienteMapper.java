package br.com.oficina.os.cliente.application;

import br.com.oficina.os.cliente.application.dto.ClienteResponse;
import br.com.oficina.os.cliente.domain.Cliente;
import org.springframework.stereotype.Component;

@Component
public class ClienteMapper {

	public ClienteResponse toResponse(Cliente cliente) {
		return new ClienteResponse(
				cliente.getId(),
				cliente.getNome(),
				cliente.getCpfCnpj(),
				cliente.getTelefone(),
				cliente.getEmail(),
				cliente.getCep(),
				cliente.getLogradouro(),
				cliente.getBairro(),
				cliente.getCidade(),
				cliente.getUf(),
				cliente.getDataNascimento(),
				cliente.getAtivo(),
				cliente.getDataCriacao(),
				cliente.getDataAtualizacao()
		);
	}
}
