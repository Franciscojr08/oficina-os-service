package br.com.oficina.os.shared.observabilidade;

import com.newrelic.api.agent.HeaderType;
import com.newrelic.api.agent.Headers;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ponte entre um mapa simples e a interface de cabeçalhos do agente New Relic (tipo mensagem). */
final class CabecalhosRastreamento implements Headers {

	private final Map<String, String> valores;

	CabecalhosRastreamento(Map<String, String> valores) {
		this.valores = new LinkedHashMap<>(valores);
	}

	Map<String, String> comoMapa() {
		return Map.copyOf(valores);
	}

	@Override
	public HeaderType getHeaderType() {
		return HeaderType.MESSAGE;
	}

	@Override
	public String getHeader(String nome) {
		return valores.get(nome);
	}

	@Override
	public Collection<String> getHeaders(String nome) {
		String valor = valores.get(nome);
		return valor == null ? List.of() : List.of(valor);
	}

	@Override
	public void setHeader(String nome, String valor) {
		valores.put(nome, valor);
	}

	@Override
	public void addHeader(String nome, String valor) {
		valores.put(nome, valor);
	}

	@Override
	public Collection<String> getHeaderNames() {
		return List.copyOf(valores.keySet());
	}

	@Override
	public boolean containsHeader(String nome) {
		return valores.containsKey(nome);
	}
}
