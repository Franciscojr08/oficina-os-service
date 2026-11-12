-- Observabilidade do fluxo distribuído.
--
-- outbox_evento.rastreamento: cabeçalhos do trace (traceparent, tracestate, newrelic) da transação
-- que gravou o evento, em JSON. O publicador roda depois, em outra transação; sem guardar o contexto
-- aqui, o trace da requisição terminaria no banco e não continuaria nos outros serviços.
--
-- saga_ordem_servico.iniciada_em: início do ciclo atual da Saga (um novo pedido de orçamento reinicia
-- a Saga na mesma linha), base da duração enviada no evento de encerramento.

ALTER TABLE outbox_evento ADD COLUMN rastreamento TEXT NULL;

ALTER TABLE saga_ordem_servico ADD COLUMN iniciada_em TIMESTAMP NULL;
UPDATE saga_ordem_servico SET iniciada_em = criado_em WHERE iniciada_em IS NULL;
