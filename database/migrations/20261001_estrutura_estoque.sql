DELIMITER $$
DROP PROCEDURE IF EXISTS migrar_estrutura_estoque$$
CREATE PROCEDURE migrar_estrutura_estoque()
BEGIN
    IF DATABASE() IS NULL OR LEFT(DATABASE(),8) <> 'estoque_' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FIFO autorizado somente em banco isolado estoque_';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND column_name='origem') THEN
        ALTER TABLE tb_lote ADD COLUMN origem VARCHAR(20) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND column_name='compra_id_externo') THEN
        ALTER TABLE tb_lote ADD COLUMN compra_id_externo VARCHAR(100) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND column_name='quantidade_inicial_g') THEN
        ALTER TABLE tb_lote ADD COLUMN quantidade_inicial_g INTEGER NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND column_name='quantidade_origem') THEN
        ALTER TABLE tb_lote ADD COLUMN quantidade_origem DECIMAL(18,6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND column_name='unidade_origem') THEN
        ALTER TABLE tb_lote ADD COLUMN unidade_origem VARCHAR(20) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND column_name='custo_unidade_origem') THEN
        ALTER TABLE tb_lote ADD COLUMN custo_unidade_origem DECIMAL(18,8) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND column_name='gramas_por_unidade_origem') THEN
        ALTER TABLE tb_lote ADD COLUMN gramas_por_unidade_origem INTEGER NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND column_name='entrada_em') THEN
        ALTER TABLE tb_lote ADD COLUMN entrada_em TIMESTAMP(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND column_name='importado_em') THEN
        ALTER TABLE tb_lote ADD COLUMN importado_em TIMESTAMP(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_venda' AND column_name='data_venda_em') THEN
        ALTER TABLE tb_venda ADD COLUMN data_venda_em TIMESTAMP(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_venda' AND column_name='importado_em') THEN
        ALTER TABLE tb_venda ADD COLUMN importado_em TIMESTAMP(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_promocao' AND column_name='ativada_em') THEN
        ALTER TABLE tb_promocao ADD COLUMN ativada_em TIMESTAMP(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_promocao' AND column_name='inicio_efetivo_em') THEN
        ALTER TABLE tb_promocao ADD COLUMN inicio_efetivo_em TIMESTAMP(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_promocao' AND column_name='fim_exclusivo_em') THEN
        ALTER TABLE tb_promocao ADD COLUMN fim_exclusivo_em TIMESTAMP(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_promocao' AND column_name='custo_referencia_por_grama') THEN
        ALTER TABLE tb_promocao ADD COLUMN custo_referencia_por_grama DECIMAL(18,8) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_promocao' AND column_name='quantidade_consumida_g') THEN
        ALTER TABLE tb_promocao ADD COLUMN quantidade_consumida_g INTEGER NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_promocao' AND column_name='quantidade_descartada_g') THEN
        ALTER TABLE tb_promocao ADD COLUMN quantidade_descartada_g INTEGER NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_promocao' AND column_name='valor_perdido') THEN
        ALTER TABLE tb_promocao ADD COLUMN valor_perdido DECIMAL(18,2) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_promocao' AND column_name='confirmada_em') THEN
        ALTER TABLE tb_promocao ADD COLUMN confirmada_em TIMESTAMP(6) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_promocao' AND column_name='confirmada_por') THEN
        ALTER TABLE tb_promocao ADD COLUMN confirmada_por BIGINT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND column_name='custo_unitario' AND
        (numeric_precision <> 18 OR numeric_scale <> 8)) THEN
        ALTER TABLE tb_lote MODIFY custo_unitario DECIMAL(18,8) NOT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
        AND table_name='tb_promocao' AND constraint_name='fk_promocao_confirmada_por') THEN
        ALTER TABLE tb_promocao ADD CONSTRAINT fk_promocao_confirmada_por
            FOREIGN KEY(confirmada_por) REFERENCES tb_usuario(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND index_name='uq_lotes_restaurante_origem_externo') THEN
        ALTER TABLE tb_lote ADD UNIQUE KEY uq_lotes_restaurante_origem_externo(restaurante_id,origem,id_externo);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='tb_lote' AND index_name='uq_lotes_restaurante_externo') THEN
        ALTER TABLE tb_lote DROP INDEX uq_lotes_restaurante_externo;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='tb_venda' AND index_name='uq_vendas_restaurante_origem_externo') THEN
        ALTER TABLE tb_venda ADD UNIQUE KEY uq_vendas_restaurante_origem_externo(restaurante_id,origem,id_externo);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='tb_venda' AND index_name='uq_vendas_restaurante_externo') THEN
        ALTER TABLE tb_venda DROP INDEX uq_vendas_restaurante_externo;
    END IF;
    CREATE TABLE IF NOT EXISTS tb_movimento_estoque (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    restaurante_id BIGINT NOT NULL,
    lote_id BIGINT NOT NULL,
    ingrediente_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    quantidade_g INTEGER NOT NULL,
    ocorrido_em TIMESTAMP(6) NOT NULL,
    item_venda_id BIGINT NULL,
    promocao_id BIGINT NULL,
    CONSTRAINT fk_movimento_restaurante FOREIGN KEY(restaurante_id) REFERENCES tb_restaurante(id),
    CONSTRAINT fk_movimento_lote FOREIGN KEY(lote_id) REFERENCES tb_lote(id),
    CONSTRAINT fk_movimento_ingrediente FOREIGN KEY(ingrediente_id) REFERENCES tb_ingrediente(id),
    CONSTRAINT fk_movimento_item FOREIGN KEY(item_venda_id) REFERENCES tb_item_venda(id),
    CONSTRAINT fk_movimento_promocao FOREIGN KEY(promocao_id) REFERENCES tb_promocao(id),
    UNIQUE KEY uq_movimento_item_lote_tipo(item_venda_id,lote_id,tipo),
    UNIQUE KEY uq_movimento_descarte_promocao(promocao_id)
);
END$$
CALL migrar_estrutura_estoque()$$
DROP PROCEDURE migrar_estrutura_estoque$$
DELIMITER ;
