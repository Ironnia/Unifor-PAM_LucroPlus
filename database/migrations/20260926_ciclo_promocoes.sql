-- Migracao aditiva do ciclo de promocoes

DELIMITER $$
DROP PROCEDURE IF EXISTS migrar_ciclo_promocoes$$
CREATE PROCEDURE migrar_ciclo_promocoes()
BEGIN
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Selecione o banco antes da migracao';
    END IF;

    ALTER TABLE tb_promocao MODIFY COLUMN status VARCHAR(24) NOT NULL,
        MODIFY COLUMN desconto_pct INTEGER NULL;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'tb_promocao' AND column_name = 'lote_id') THEN
        ALTER TABLE tb_promocao ADD COLUMN lote_id BIGINT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'tb_promocao' AND column_name = 'data_inicio') THEN
        ALTER TABLE tb_promocao ADD COLUMN data_inicio DATE NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'tb_promocao' AND column_name = 'data_fim') THEN
        ALTER TABLE tb_promocao ADD COLUMN data_fim DATE NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'tb_promocao' AND column_name = 'data_confirmacao') THEN
        ALTER TABLE tb_promocao ADD COLUMN data_confirmacao DATE NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'tb_promocao' AND column_name = 'quantidade_referencia_g') THEN
        ALTER TABLE tb_promocao ADD COLUMN quantidade_referencia_g INTEGER NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'tb_promocao' AND column_name = 'valor_em_risco') THEN
        ALTER TABLE tb_promocao ADD COLUMN valor_em_risco DECIMAL(18,2) NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'tb_promocao' AND column_name = 'valor_salvo') THEN
        ALTER TABLE tb_promocao ADD COLUMN valor_salvo DECIMAL(18,2) NOT NULL DEFAULT 0.00;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'tb_promocao' AND index_name = 'uq_promocao_lote_produto') THEN
        ALTER TABLE tb_promocao ADD UNIQUE KEY uq_promocao_lote_produto (lote_id, produto_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE() AND table_name = 'tb_promocao'
          AND column_name = 'lote_id' AND referenced_table_name = 'tb_lote') THEN
        ALTER TABLE tb_promocao ADD CONSTRAINT fk_promocao_lote
            FOREIGN KEY (lote_id) REFERENCES tb_lote(id);
    END IF;
END$$
CALL migrar_ciclo_promocoes()$$
DROP PROCEDURE migrar_ciclo_promocoes$$
DELIMITER ;
