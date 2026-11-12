package br.com.oficina.os.mensageria.outbox;

import br.com.oficina.os.mensageria.config.MensageriaProperties;
import br.com.oficina.os.mensageria.consumo.EventoProcessadoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Remove, uma vez por dia, eventos já publicados e ids de mensagens consumidas mais antigos que a
 * retenção configurada. Pendentes nunca são removidos.
 */
@Component
@RequiredArgsConstructor
public class LimpezaMensageria {

	private static final Logger LOG = LoggerFactory.getLogger(LimpezaMensageria.class);

	private final OutboxEventoRepository outboxRepository;
	private final EventoProcessadoRepository eventoProcessadoRepository;
	private final MensageriaProperties propriedades;

	@Scheduled(cron = "${oficina.mensageria.retencao.cron:0 30 3 * * *}")
	@Transactional
	public void limpar() {
		LocalDateTime agora = LocalDateTime.now();
		int outbox = outboxRepository.removerPublicadosAntesDe(agora.minusDays(propriedades.retencao().outboxDias()));
		int processados = eventoProcessadoRepository.removerProcessadosAntesDe(
				agora.minusDays(propriedades.retencao().eventosProcessadosDias()));
		LOG.info("mensageria_limpeza outboxRemovidos={} eventosProcessadosRemovidos={}", outbox, processados);
	}
}
