-- Ordem de serviço do OS Service.
--
-- Diferenças em relação ao monólito:
--   * status com até 30 caracteres, para os status novos da Saga (AGUARDANDO_ORCAMENTO etc.);
--   * datas das etapas novas: solicitação de orçamento e pagamento;
--   * orcamento_id e pagamento_id guardam os ids do Billing. São referências lógicas, sem FK,
--     porque o orçamento e o pagamento vivem no banco de outro serviço.

-- 1. Contador anual do código da OS
CREATE TABLE os_contador
(
    ano          INTEGER PRIMARY KEY,
    ultimo_valor INTEGER NOT NULL DEFAULT 0
);

-- 2. Próximo código no formato OS-AAAA-NNNNNN, com reinício automático a cada ano
CREATE OR REPLACE FUNCTION calcular_proximo_codigo_os() RETURNS TEXT AS
$$
DECLARE
    ano_atual  INTEGER := EXTRACT(YEAR FROM CURRENT_DATE);
    novo_valor INTEGER;
BEGIN
    INSERT INTO os_contador (ano, ultimo_valor)
    VALUES (ano_atual, 1)
    ON CONFLICT (ano) DO UPDATE
        SET ultimo_valor = os_contador.ultimo_valor + 1
    RETURNING ultimo_valor INTO novo_valor;

    RETURN 'OS-' || ano_atual || '-' || LPAD(novo_valor::text, 6, '0');
END;
$$ LANGUAGE plpgsql;

-- 3. Trigger que atribui o código quando ele não vem no INSERT
CREATE OR REPLACE FUNCTION trigger_atribuir_codigo_os()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NEW.codigo IS NULL THEN
        NEW.codigo := calcular_proximo_codigo_os();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 4. Tabela
CREATE TABLE ordem_servico
(
    id                            BIGSERIAL PRIMARY KEY,
    codigo                        VARCHAR(20)    NOT NULL UNIQUE,
    descricao_problema            TEXT           NULL,
    observacoes_gerais            VARCHAR(500)   NULL,
    descricao_servicos_executados TEXT           NOT NULL,
    status                        VARCHAR(30)    NOT NULL,
    valor_total_servicos          DECIMAL(10, 2) NOT NULL,
    valor_total_itens             DECIMAL(10, 2) NOT NULL,
    data_cadastro                 TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_solicitacao_orcamento    TIMESTAMP      NULL,
    data_envio_aprovacao          TIMESTAMP      NULL,
    data_aprovacao                TIMESTAMP      NULL,
    data_pagamento                TIMESTAMP      NULL,
    data_inicio_execucao          TIMESTAMP      NULL,
    data_fim_execucao             TIMESTAMP      NULL,
    data_entregue                 TIMESTAMP      NULL,
    data_cancelada                TIMESTAMP      NULL,
    orcamento_id                  BIGINT         NULL,
    pagamento_id                  BIGINT         NULL,
    cliente_id                    BIGINT         NOT NULL,
    veiculo_id                    BIGINT         NOT NULL,
    CONSTRAINT fk_os_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_os_veiculo FOREIGN KEY (veiculo_id) REFERENCES veiculo (id)
);

CREATE TRIGGER trigger_os_codigo_insert
    BEFORE INSERT
    ON ordem_servico
    FOR EACH ROW
EXECUTE FUNCTION trigger_atribuir_codigo_os();

-- 5. Índices para busca e relatórios
CREATE INDEX idx_os_cliente_status ON ordem_servico (cliente_id, status);
CREATE INDEX idx_os_status ON ordem_servico (status);
CREATE INDEX idx_os_data_cadastro ON ordem_servico (data_cadastro);
