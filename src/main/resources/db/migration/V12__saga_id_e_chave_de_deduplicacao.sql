-- sagaId no envelope das mensagens (Fase 4, contrato entre os serviços).
--
-- saga_ordem_servico.saga_id: nasce quando a OS entra na Saga (a cada novo pedido de orçamento, um
--   id novo) e acompanha todos os comandos e eventos daquele ciclo. Sagas já existentes recebem um id
--   gerado agora.
--
-- outbox_evento.saga_id: o mesmo id, para achar no outbox tudo o que saiu de uma Saga. É opcional
--   porque eventos informativos (OrdemServicoStatusAlterado) podem sair fora de uma Saga.
--
-- evento_processado: a chave de deduplicação deixa de ser só o messageId. Billing e Execução não são
--   obrigados a mandar messageId; sem ele a chave é sagaId + tipo + hash de "dados" (ver
--   ChaveDeduplicacao). Registros antigos viram chave = messageId, então as repetições que já eram
--   descartadas continuam sendo. A migration só acrescenta colunas e relaxa uma restrição: nada é
--   removido, e a versão anterior do código continua lendo a tabela.

ALTER TABLE saga_ordem_servico ADD COLUMN saga_id UUID NULL;
UPDATE saga_ordem_servico SET saga_id = gen_random_uuid() WHERE saga_id IS NULL;
ALTER TABLE saga_ordem_servico ALTER COLUMN saga_id SET NOT NULL;
ALTER TABLE saga_ordem_servico ADD CONSTRAINT uk_saga_ordem_servico_saga_id UNIQUE (saga_id);

ALTER TABLE outbox_evento ADD COLUMN saga_id UUID NULL;
CREATE INDEX idx_outbox_evento_saga ON outbox_evento (saga_id) WHERE saga_id IS NOT NULL;

ALTER TABLE evento_processado ADD COLUMN chave_deduplicacao VARCHAR(200) NULL;
ALTER TABLE evento_processado ADD COLUMN saga_id UUID NULL;
UPDATE evento_processado SET chave_deduplicacao = message_id::text WHERE chave_deduplicacao IS NULL;
ALTER TABLE evento_processado ALTER COLUMN chave_deduplicacao SET NOT NULL;
ALTER TABLE evento_processado DROP CONSTRAINT evento_processado_pkey;
ALTER TABLE evento_processado ADD CONSTRAINT pk_evento_processado PRIMARY KEY (chave_deduplicacao);
ALTER TABLE evento_processado ALTER COLUMN message_id DROP NOT NULL;
