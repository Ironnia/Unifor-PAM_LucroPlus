-- Tabela para registro de acoes tomadas sobre alertas de lotes
CREATE TABLE IF NOT EXISTS tb_acao_alerta
(
    lote_id   BIGINT      NOT NULL,
    alerta_id BIGINT      NOT NULL,
    acao      VARCHAR(20) NOT NULL,
    data_acao DATE        NOT NULL,

    CONSTRAINT pk_acao_alerta PRIMARY KEY (lote_id),
    CONSTRAINT uq_acao_alerta_alerta UNIQUE (alerta_id),
    CONSTRAINT fk_acao_alerta_lote FOREIGN KEY (lote_id)
        REFERENCES tb_lote (id) ON DELETE RESTRICT,
    CONSTRAINT fk_acao_alerta_alerta FOREIGN KEY (alerta_id)
        REFERENCES tb_alerta (id) ON DELETE RESTRICT
);
