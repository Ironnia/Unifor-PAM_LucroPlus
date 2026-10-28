DELIMITER $$
DROP PROCEDURE IF EXISTS migrar_isolamento_isolamento$$
CREATE PROCEDURE migrar_isolamento_isolamento()
BEGIN
    IF DATABASE() IS NULL OR LEFT(DATABASE(),11) <> 'isolamento_' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='E1B autorizado somente em banco isolado isolamento_';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND column_name='quantidade' AND is_nullable='NO') THEN
        ALTER TABLE tb_lote MODIFY quantidade DECIMAL(10,3) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_produto' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_produto ADD COLUMN restaurante_id BIGINT NULL,
            ADD CONSTRAINT fk_tb_produto_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_produto' AND column_name='id_externo') THEN
        ALTER TABLE tb_produto ADD COLUMN id_externo VARCHAR(100) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_produto' AND index_name='uq_produtos_restaurante_externo') THEN
        ALTER TABLE tb_produto ADD UNIQUE KEY uq_produtos_restaurante_externo(restaurante_id,id_externo);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_ingrediente' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_ingrediente ADD COLUMN restaurante_id BIGINT NULL,
            ADD CONSTRAINT fk_tb_ingrediente_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_ingrediente' AND column_name='id_externo') THEN
        ALTER TABLE tb_ingrediente ADD COLUMN id_externo VARCHAR(100) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_ingrediente' AND index_name='uq_ingredientes_restaurante_externo') THEN
        ALTER TABLE tb_ingrediente ADD UNIQUE KEY uq_ingredientes_restaurante_externo(restaurante_id,id_externo);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_lote' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_lote ADD COLUMN restaurante_id BIGINT NULL,
            ADD CONSTRAINT fk_tb_lote_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_lote' AND column_name='id_externo') THEN
        ALTER TABLE tb_lote ADD COLUMN id_externo VARCHAR(100) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_lote' AND index_name='uq_lotes_restaurante_externo') THEN
        ALTER TABLE tb_lote ADD UNIQUE KEY uq_lotes_restaurante_externo(restaurante_id,id_externo);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_venda' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_venda ADD COLUMN restaurante_id BIGINT NULL,
            ADD CONSTRAINT fk_tb_venda_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_venda' AND column_name='id_externo') THEN
        ALTER TABLE tb_venda ADD COLUMN id_externo VARCHAR(100) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tb_venda' AND index_name='uq_vendas_restaurante_externo') THEN
        ALTER TABLE tb_venda ADD UNIQUE KEY uq_vendas_restaurante_externo(restaurante_id,id_externo);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_promocao' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_promocao ADD COLUMN restaurante_id BIGINT NULL,
            ADD CONSTRAINT fk_tb_promocao_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;
    CREATE TABLE IF NOT EXISTS tb_configuracao_restaurante (
    restaurante_id BIGINT NOT NULL,
    chave VARCHAR(100) NOT NULL,
    valor VARCHAR(255) NULL,
    PRIMARY KEY(restaurante_id,chave),
    CONSTRAINT fk_configuracao_restaurante FOREIGN KEY(restaurante_id) REFERENCES tb_restaurante(id)
);
END$$
CALL migrar_isolamento_isolamento()$$
DROP PROCEDURE migrar_isolamento_isolamento$$
DELIMITER ;
