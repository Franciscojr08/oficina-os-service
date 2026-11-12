-- Tabelas auxiliares da OS.
--
-- Diferenças em relação ao monólito:
--   * historico_ordem_servico ganha "origem": quem provocou a mudança ("API" ou o nome do evento
--     da Saga, por exemplo "OrcamentoAprovado");
--   * servico_ordem_servico perde status, data_inicio e data_fim: o andamento da execução de cada
--     serviço passou a ser responsabilidade do serviço de Execução.

CREATE TABLE historico_ordem_servico
(
    id               BIGSERIAL PRIMARY KEY,
    status           VARCHAR(30)  NOT NULL,
    observacao       VARCHAR(150) NULL,
    origem           VARCHAR(60)  NOT NULL DEFAULT 'API',
    data_cadastro    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP    NULL,
    ordem_servico_id BIGINT       NOT NULL,
    CONSTRAINT fk_historico_ordem_servico FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id)
);

CREATE INDEX idx_historico_os ON historico_ordem_servico (ordem_servico_id, data_cadastro);

CREATE TABLE item_ordem_servico
(
    id               BIGSERIAL PRIMARY KEY,
    quantidade       INTEGER        NOT NULL CHECK (quantidade > 0),
    valor_unitario   NUMERIC(10, 2) NOT NULL,
    item_id          BIGINT         NOT NULL,
    ordem_servico_id BIGINT         NOT NULL,
    CONSTRAINT fk_item_os FOREIGN KEY (item_id) REFERENCES item (id),
    CONSTRAINT fk_item_ordem_servico FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id)
);

CREATE INDEX idx_item_os_ordem ON item_ordem_servico (ordem_servico_id);

CREATE TABLE servico_ordem_servico
(
    id               BIGSERIAL PRIMARY KEY,
    valor_unitario   NUMERIC(10, 2) NOT NULL,
    data_cadastro    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    servico_id       BIGINT         NOT NULL,
    ordem_servico_id BIGINT         NOT NULL,
    CONSTRAINT fk_servico_os FOREIGN KEY (servico_id) REFERENCES servico (id),
    CONSTRAINT fk_servico_ordem_servico FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id)
);

CREATE INDEX idx_servico_os_ordem ON servico_ordem_servico (ordem_servico_id);
