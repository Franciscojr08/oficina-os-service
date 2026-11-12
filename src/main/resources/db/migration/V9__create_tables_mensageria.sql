-- Mensageria confiável.
--
-- outbox_evento (Transactional Outbox): o evento é gravado na mesma transação que muda a OS e um
-- publicador agendado envia depois. Se o broker estiver fora, nada se perde: o evento fica pendente
-- (publicado_em nulo) e é reenviado na próxima rodada.
--
-- evento_processado (inbox): id das mensagens já consumidas. A entrega do RabbitMQ é "pelo menos
-- uma vez"; uma mensagem repetida encontra o próprio id aqui e é ignorada.

CREATE TABLE outbox_evento
(
    id               BIGSERIAL PRIMARY KEY,
    message_id       UUID         NOT NULL,
    tipo             VARCHAR(60)  NOT NULL,
    exchange         VARCHAR(100) NOT NULL,
    routing_key      VARCHAR(100) NOT NULL,
    ordem_servico_id BIGINT       NULL,
    correlation_id   VARCHAR(128) NOT NULL,
    payload          TEXT         NOT NULL,
    criado_em        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    publicado_em     TIMESTAMP    NULL,
    tentativas       INT          NOT NULL DEFAULT 0,
    ultimo_erro      VARCHAR(500) NULL,
    CONSTRAINT uk_outbox_evento_message_id UNIQUE (message_id)
);

-- O publicador só lê pendentes, em ordem de criação: índice parcial fica pequeno mesmo com o
-- histórico de eventos já publicados.
CREATE INDEX idx_outbox_evento_pendente ON outbox_evento (id) WHERE publicado_em IS NULL;

CREATE TABLE evento_processado
(
    message_id       UUID        PRIMARY KEY,
    tipo             VARCHAR(60) NOT NULL,
    ordem_servico_id BIGINT      NULL,
    processado_em    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evento_processado_data ON evento_processado (processado_em);
