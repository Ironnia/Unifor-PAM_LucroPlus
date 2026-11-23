CREATE TABLE IF NOT EXISTS tb_movimento_estoque (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    restaurante_id BIGINT NOT NULL,
    lote_id BIGINT NOT NULL,
    ingrediente_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    quantidade_g INT NOT NULL,
    ocorrido_em TIMESTAMP(6) NOT NULL,
    item_venda_id BIGINT NULL,
    promocao_id BIGINT NULL,
    CONSTRAINT chk_movimento_quantidade CHECK (quantidade_g > 0),
    CONSTRAINT fk_movimento_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id),
    CONSTRAINT fk_movimento_lote FOREIGN KEY (lote_id) REFERENCES tb_lote(id),
    CONSTRAINT fk_movimento_ingrediente FOREIGN KEY (ingrediente_id) REFERENCES tb_ingrediente(id),
    CONSTRAINT fk_movimento_item FOREIGN KEY (item_venda_id) REFERENCES tb_item_venda(id),
    CONSTRAINT fk_movimento_promocao FOREIGN KEY (promocao_id) REFERENCES tb_promocao(id),
    UNIQUE KEY uq_movimento_item_lote_tipo (item_venda_id, lote_id, tipo),
    UNIQUE KEY uq_movimento_descarte_promocao (promocao_id),
    KEY idx_movimento_lote (lote_id),
    KEY idx_movimento_restaurante_data (restaurante_id, ocorrido_em),
    KEY idx_movimento_ingrediente (ingrediente_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_sync_pdv_historico (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    restaurante_id BIGINT NOT NULL,
    executado_em TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    status VARCHAR(20) NOT NULL,
    vendas_importadas INT NOT NULL DEFAULT 0,
    compras_importadas INT NOT NULL DEFAULT 0,
    movimentos_gerados INT NOT NULL DEFAULT 0,
    tempo_execucao_ms INT NULL,
    mensagem TEXT NULL,
    CONSTRAINT fk_sync_pdv_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id),
    KEY idx_sync_restaurante_data (restaurante_id, executado_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
DROP PROCEDURE IF EXISTS migrar_movimentos_estoque_sync_estoque$$
CREATE PROCEDURE migrar_movimentos_estoque_sync_estoque()
BEGIN

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'tb_movimento_estoque'
          AND constraint_name = 'chk_movimento_quantidade'
          AND constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE tb_movimento_estoque
            ADD CONSTRAINT chk_movimento_quantidade CHECK (quantidade_g > 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'tb_movimento_estoque'
          AND index_name = 'uq_movimento_item_lote_tipo'
    ) THEN
        ALTER TABLE tb_movimento_estoque
            ADD UNIQUE KEY uq_movimento_item_lote_tipo (item_venda_id, lote_id, tipo);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'tb_movimento_estoque'
          AND index_name = 'uq_movimento_descarte_promocao'
    ) THEN
        ALTER TABLE tb_movimento_estoque
            ADD UNIQUE KEY uq_movimento_descarte_promocao (promocao_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'tb_movimento_estoque'
          AND index_name = 'idx_movimento_lote'
    ) THEN
        ALTER TABLE tb_movimento_estoque
            ADD INDEX idx_movimento_lote (lote_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'tb_movimento_estoque'
          AND index_name = 'idx_movimento_restaurante_data'
    ) THEN
        ALTER TABLE tb_movimento_estoque
            ADD INDEX idx_movimento_restaurante_data (restaurante_id, ocorrido_em);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'tb_movimento_estoque'
          AND index_name = 'idx_movimento_ingrediente'
    ) THEN
        ALTER TABLE tb_movimento_estoque
            ADD INDEX idx_movimento_ingrediente (ingrediente_id);
    END IF;
END$$
DELIMITER ;

CALL migrar_movimentos_estoque_sync_estoque();
DROP PROCEDURE IF EXISTS migrar_movimentos_estoque_sync_estoque;
