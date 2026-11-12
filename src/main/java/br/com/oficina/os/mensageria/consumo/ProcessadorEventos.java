package br.com.oficina.os.mensageria.consumo;

import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;
import br.com.oficina.os.shared.observabilidade.Telemetria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Processa uma mensagem recebida exatamente uma vez: registra a {@link ChaveDeduplicacao} no inbox e
 * chama o manipulador do tipo, na mesma transação. Mensagem repetida é ignorada.
 */
@Service
public class ProcessadorEventos {

	public static final String CONTADOR_PROCESSADAS = "Custom/Mensageria/Processadas";
	public static final String CONTADOR_DUPLICADAS = "Custom/Mensageria/Duplicadas";

	private static final Logger LOG = LoggerFactory.getLogger(ProcessadorEventos.class);

	private final Map<String, ManipuladorEvento> manipuladores;
	private final EventoProcessadoRepository eventoProcessadoRepository;
	private final Telemetria telemetria;

	public ProcessadorEventos(
			ObjectProvider<ManipuladorEvento> manipuladores,
			EventoProcessadoRepository eventoProcessadoRepository,
			Telemetria telemetria
	) {
		// toMap falha na subida se dois manipuladores disputarem o mesmo tipo
		this.manipuladores = manipuladores.orderedStream()
				.collect(Collectors.toMap(ManipuladorEvento::tipo, Function.identity()));
		this.eventoProcessadoRepository = eventoProcessadoRepository;
		this.telemetria = telemetria;
	}

	/** Devolve {@code true} se processou e {@code false} se a mensagem já tinha sido processada. */
	@Transactional
	public boolean processar(EnvelopeMensagem envelope) {
		ManipuladorEvento manipulador = manipuladores.get(envelope.tipo());
		if (manipulador == null) {
			throw new MensagemInvalidaException("Nenhum manipulador para o evento " + envelope.tipo());
		}

		if (!eventoProcessadoRepository.registrar(ChaveDeduplicacao.de(envelope), envelope.messageId(),
				envelope.sagaId(), envelope.tipo(), envelope.ordemServicoId())) {
			LOG.info("mensagem_duplicada_ignorada tipo={} sagaId={} messageId={} ordemServicoId={}",
					envelope.tipo(), envelope.sagaId(), envelope.messageId(), envelope.ordemServicoId());
			telemetria.incrementarContador(CONTADOR_DUPLICADAS);
			return false;
		}

		manipulador.manipular(envelope);
		telemetria.incrementarContador(CONTADOR_PROCESSADAS);
		LOG.info("mensagem_processada tipo={} sagaId={} messageId={} ordemServicoId={}",
				envelope.tipo(), envelope.sagaId(), envelope.messageId(), envelope.ordemServicoId());
		return true;
	}
}
