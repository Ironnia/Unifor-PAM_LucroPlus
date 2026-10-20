
-- 1. Tabela de Restaurantes
CREATE TABLE IF NOT EXISTS tb_restaurante (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(150) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO tb_restaurante (id, nome) VALUES 
(1, 'Restaurante Leroliro'),
(2, 'Restaurante Gula');

DELIMITER $$
DROP PROCEDURE IF EXISTS migrar_multi_restaurante_s4$$
CREATE PROCEDURE migrar_multi_restaurante_s4()
BEGIN
    -- tb_usuario
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_usuario' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_usuario ADD COLUMN restaurante_id BIGINT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='tb_usuario' AND column_name='restaurante_id' AND referenced_table_name='tb_restaurante' AND referenced_column_name='id') THEN
        ALTER TABLE tb_usuario ADD CONSTRAINT fk_usuario_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;

    -- tb_produto
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_produto' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_produto ADD COLUMN restaurante_id BIGINT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='tb_produto' AND column_name='restaurante_id' AND referenced_table_name='tb_restaurante' AND referenced_column_name='id') THEN
        ALTER TABLE tb_produto ADD CONSTRAINT fk_produto_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_produto' AND column_name='id_externo') THEN
        ALTER TABLE tb_produto ADD COLUMN id_externo VARCHAR(100) NULL;
    END IF;
    

    -- tb_ingrediente
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_ingrediente' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_ingrediente ADD COLUMN restaurante_id BIGINT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='tb_ingrediente' AND column_name='restaurante_id' AND referenced_table_name='tb_restaurante' AND referenced_column_name='id') THEN
        ALTER TABLE tb_ingrediente ADD CONSTRAINT fk_ingrediente_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_ingrediente' AND column_name='id_externo') THEN
        ALTER TABLE tb_ingrediente ADD COLUMN id_externo VARCHAR(100) NULL;
    END IF;
    

    -- tb_lote
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_lote' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_lote ADD COLUMN restaurante_id BIGINT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='tb_lote' AND column_name='restaurante_id' AND referenced_table_name='tb_restaurante' AND referenced_column_name='id') THEN
        ALTER TABLE tb_lote ADD CONSTRAINT fk_lote_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_lote' AND column_name='id_externo') THEN
        ALTER TABLE tb_lote ADD COLUMN id_externo VARCHAR(100) NULL;
    END IF;
    

    -- tb_venda
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_venda' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_venda ADD COLUMN restaurante_id BIGINT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='tb_venda' AND column_name='restaurante_id' AND referenced_table_name='tb_restaurante' AND referenced_column_name='id') THEN
        ALTER TABLE tb_venda ADD CONSTRAINT fk_venda_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_venda' AND column_name='id_externo') THEN
        ALTER TABLE tb_venda ADD COLUMN id_externo VARCHAR(100) NULL;
    END IF;
    

    -- tb_promocao
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_promocao' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_promocao ADD COLUMN restaurante_id BIGINT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='tb_promocao' AND column_name='restaurante_id' AND referenced_table_name='tb_restaurante' AND referenced_column_name='id') THEN
        ALTER TABLE tb_promocao ADD CONSTRAINT fk_promocao_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;

    -- tb_configuracao_restaurante (configurações independentes por loja)
    CREATE TABLE IF NOT EXISTS tb_configuracao_restaurante (
        restaurante_id BIGINT NOT NULL,
        chave VARCHAR(100) NOT NULL,
        valor VARCHAR(255) NULL,
        PRIMARY KEY (restaurante_id, chave),
        CONSTRAINT fk_configuracao_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

END$$
DELIMITER ;

CALL migrar_multi_restaurante_s4();
DROP PROCEDURE IF EXISTS migrar_multi_restaurante_s4;