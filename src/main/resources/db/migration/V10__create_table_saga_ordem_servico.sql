-- Estado da Saga orquestrada de cada OS (o OS Service é o orquestrador).
--
-- etapa: em que ponto do fluxo distribuído a OS está (esperando orçamento, aprovação, pagamento,
--        execução ou estorno).
-- situacao: EM_ANDAMENTO, COMPENSANDO (desfazendo um passo, por exemplo estornando o pagamento) ou
--           um estado final: CONCLUIDA, CANCELADA (encerrada sem nada a desfazer), COMPENSADA
--           (encerrada depois de compensar) e FALHA_COMPENSACAO (precisa de intervenção manual).
-- prazo / tentativas / comando_message_id: se a resposta do outro serviço não chega até o prazo, o
--        verificador reenvia o mesmo comando (mesmo messageId, para o destino descartar a
--        repetição) e, esgotadas as tentativas, dispara a compensação.

CREATE TABLE saga_ordem_servico
(
    id                 BIGSERIAL PRIMARY KEY,
    ordem_servico_id   BIGINT       NOT NULL,
    etapa              VARCHAR(40)  NOT NULL,
    situacao           VARCHAR(30)  NOT NULL,
    prazo              TIMESTAMP    NULL,
    tentativas         INT          NOT NULL DEFAULT 0,
    comando_message_id UUID         NULL,
    motivo             VARCHAR(255) NULL,
    criado_em          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em      TIMESTAMP    NULL,
    encerrada_em       TIMESTAMP    NULL,
    CONSTRAINT uk_saga_ordem_servico UNIQUE (ordem_servico_id),
    CONSTRAINT fk_saga_ordem_servico FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id)
);

-- O verificador de prazos só olha Sagas ativas com prazo definido.
CREATE INDEX idx_saga_prazo_ativo ON saga_ordem_servico (prazo)
    WHERE situacao IN ('EM_ANDAMENTO', 'COMPENSANDO') AND prazo IS NOT NULL;
