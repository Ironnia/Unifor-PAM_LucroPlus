CREATE DATABASE IF NOT EXISTS lucroplus_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE lucroplus_db;

-- 1. Usuários do Sistema (Administrador LucroPlus e Gerentes dos Restaurantes)
CREATE TABLE IF NOT EXISTS tb_usuario
(
    id         BIGINT                    NOT NULL AUTO_INCREMENT,
    nome       VARCHAR(100)              NOT NULL,
    email      VARCHAR(150)              NOT NULL,
    senha_hash VARCHAR(255)              NOT NULL,
    tipo       ENUM ('ADMIN', 'GERENTE') NOT NULL,
    ativo      BOOLEAN                   NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uq_usuario_email UNIQUE (email)
);

-- Carga inicial de usuários de teste
INSERT INTO tb_usuario (nome, email, senha_hash, tipo, ativo)
VALUES ('Administrador', 'admin@lucroplus.com', '$2a$12$CM.GgCRTnE/R8bdrBTUyLez0dOztEHzbsOACYBvVeQwxcG0Scvawu', 'ADMIN', TRUE),
       ('Gerente Teste', 'gerente@lucroplus.com', '$2a$12$tHAF6hTUBZTwhobYs7.RQ.SkmmR327d/0l8t1WHLp/cpecKZb9sea', 'GERENTE', TRUE)
ON DUPLICATE KEY UPDATE nome = VALUES(nome);

-- 2. Produtos do Cardápio
CREATE TABLE IF NOT EXISTS tb_produto
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    nome          VARCHAR(150)  NOT NULL,
    descricao     TEXT,
    preco         DECIMAL(8, 2) NOT NULL,
    categoria     VARCHAR(80)   NOT NULL,
    ativo         BOOLEAN       NOT NULL DEFAULT TRUE,
    data_cadastro TIMESTAMP              DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_produto PRIMARY KEY (id),
    INDEX idx_produto_categoria (categoria),
    INDEX idx_produto_ativo (ativo)
);

-- 3. Ingredientes e Insumos (com peso_por_unidade_g para conversão limpa)
CREATE TABLE IF NOT EXISTS tb_ingrediente
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    nome               VARCHAR(100) NOT NULL,
    unidade            VARCHAR(20)  NOT NULL,
    peso_por_unidade_g INTEGER      NOT NULL DEFAULT 1000,
    estoque_minimo     INTEGER      NOT NULL DEFAULT 0,
    data_cadastro      TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_ingrediente PRIMARY KEY (id),
    INDEX idx_ingrediente_nome (nome),
    INDEX idx_ingrediente_unidade (unidade)
);

-- 4. Lotes de Estoque com Validade e Custo (quantidade armazenada estritamente em gramas)
CREATE TABLE IF NOT EXISTS tb_lote
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    ingrediente_id BIGINT        NOT NULL,
    quantidade_g   INTEGER       NOT NULL,
    custo_unitario DECIMAL(8, 4) NOT NULL,
    data_validade  DATE          NOT NULL,
    data_entrada   DATE          NOT NULL,
    numero_lote    VARCHAR(50),
    observacao     TEXT,

    CONSTRAINT pk_lote PRIMARY KEY (id),
    CONSTRAINT fk_lote_ingrediente FOREIGN KEY (ingrediente_id)
        REFERENCES tb_ingrediente (id) ON DELETE RESTRICT,
    INDEX idx_lote_validade (data_validade),
    INDEX idx_lote_entrada (data_entrada),
    INDEX idx_lote_ingrediente (ingrediente_id)
);

-- 5. Fichas Técnicas (Receitas dos Produtos)
CREATE TABLE IF NOT EXISTS tb_ficha_tecnica
(
    produto_id       BIGINT         NOT NULL,
    ingrediente_id   BIGINT         NOT NULL,
    quantidade_usada DECIMAL(10, 4) NOT NULL,
    unidade          VARCHAR(20)    NOT NULL,

    CONSTRAINT pk_ficha_tecnica PRIMARY KEY (produto_id, ingrediente_id),
    CONSTRAINT fk_ficha_produto FOREIGN KEY (produto_id)
        REFERENCES tb_produto (id) ON DELETE CASCADE,
    CONSTRAINT fk_ficha_ingrediente FOREIGN KEY (ingrediente_id)
        REFERENCES tb_ingrediente (id) ON DELETE RESTRICT,
    INDEX idx_ficha_produto (produto_id),
    INDEX idx_ficha_ingrediente (ingrediente_id)
);

-- 6. Cabeçalho de Vendas
CREATE TABLE IF NOT EXISTS tb_venda
(
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    usuario_id  BIGINT,
    data_venda  DATE           NOT NULL,
    valor_total DECIMAL(10, 2) NOT NULL,
    origem      VARCHAR(20)    NOT NULL DEFAULT 'importado',

    CONSTRAINT pk_venda PRIMARY KEY (id),
    CONSTRAINT fk_venda_usuario FOREIGN KEY (usuario_id)
        REFERENCES tb_usuario (id) ON DELETE SET NULL,
    INDEX idx_venda_data (data_venda),
    INDEX idx_venda_origem (origem),
    INDEX idx_venda_usuario (usuario_id)
);

-- 7. Itens da Venda
CREATE TABLE IF NOT EXISTS tb_item_venda
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    venda_id       BIGINT        NOT NULL,
    produto_id     BIGINT        NOT NULL,
    quantidade     INTEGER       NOT NULL,
    preco_unitario DECIMAL(8, 2) NOT NULL,

    CONSTRAINT pk_item_venda PRIMARY KEY (id),
    CONSTRAINT fk_item_venda FOREIGN KEY (venda_id)
        REFERENCES tb_venda (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_produto FOREIGN KEY (produto_id)
        REFERENCES tb_produto (id) ON DELETE RESTRICT,
    INDEX idx_item_venda (venda_id),
    INDEX idx_item_produto (produto_id)
);

-- 8. Promoções Sugeridas pelo Motor de Inteligência
CREATE TABLE IF NOT EXISTS tb_promocao
(
    id            BIGINT                                 NOT NULL AUTO_INCREMENT,
    produto_id    BIGINT                                 NOT NULL,
    desconto_pct  INTEGER                                NOT NULL,
    motivo        TEXT                                   NOT NULL,
    status        ENUM ('SUGESTAO', 'ATIVA', 'RECUSADA') NOT NULL,
    data_sugestao DATE                                   NOT NULL,
    data_ativacao DATE,

    CONSTRAINT pk_promocao PRIMARY KEY (id),
    CONSTRAINT fk_promocao_produto FOREIGN KEY (produto_id)
        REFERENCES tb_produto (id) ON DELETE CASCADE,
    INDEX idx_promocao_status (status),
    INDEX idx_promocao_data (data_sugestao),
    INDEX idx_promocao_produto (produto_id)
);

-- 9. Alertas de Validade
CREATE TABLE IF NOT EXISTS tb_alerta
(
    id          BIGINT                                NOT NULL AUTO_INCREMENT,
    lote_id     BIGINT                                NOT NULL,
    tipo        ENUM ('VENCIMENTO', 'ESTOQUE_MINIMO') NOT NULL,
    mensagem    TEXT                                  NOT NULL,
    data_alerta DATE                                  NOT NULL,
    visualizado BOOLEAN                               NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_alerta PRIMARY KEY (id),
    CONSTRAINT fk_alerta_lote FOREIGN KEY (lote_id)
        REFERENCES tb_lote (id) ON DELETE CASCADE,
    INDEX idx_alerta_tipo (tipo),
    INDEX idx_alerta_visualizado (visualizado),
    INDEX idx_alerta_data (data_alerta)
);

-- 10. Configurações de Conexão com PDV Externo
CREATE TABLE IF NOT EXISTS tb_configuracao
(
    chave VARCHAR(100) NOT NULL,
    valor VARCHAR(255),

    CONSTRAINT pk_configuracao PRIMARY KEY (chave)
);

INSERT INTO tb_configuracao (chave, valor)
VALUES ('pdv_url', 'jdbc:mysql://localhost:3306/pdv_ficticio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true'),
       ('pdv_username', 'root'),
       ('pdv_password', 'root')
ON DUPLICATE KEY UPDATE valor = VALUES(valor);

-- ============================================================================
-- SEED DATA: Carga Inicial do Restaurante Demonstrativo
-- ============================================================================

-- Produtos do Cardápio
INSERT INTO tb_produto (nome, descricao, preco, categoria, ativo)
VALUES ('X-Burguer Clássico', 'Hambúrguer artesanal com queijo, alface e tomate', 28.90, 'Lanches', TRUE),
       ('Pizza Margherita', 'Pizza tradicional com molho, mozzarella e manjericão', 45.00, 'Pizzas', TRUE),
       ('Salada Caesar', 'Alface americana, croutons, queijo parmesão e molho Caesar', 22.00, 'Saladas', TRUE),
       ('Suco Natural de Laranja', 'Suco natural feito na hora (500ml)', 9.50, 'Bebidas', TRUE),
       ('Torta Holandesa', 'Fatia de torta holandesa tradicional', 14.00, 'Sobremesas', TRUE)
ON DUPLICATE KEY UPDATE preco = VALUES(preco);

-- Ingredientes do Estoque (com peso_por_unidade_g)
INSERT INTO tb_ingrediente (nome, unidade, peso_por_unidade_g, estoque_minimo)
VALUES ('Pão de Hambúrguer', 'un', 80, 20),
       ('Carne Moída (Blend)', 'kg', 1000, 5000),
       ('Queijo Mussarela', 'kg', 1000, 3000),
       ('Alface Americana', 'kg', 1000, 2000),
       ('Tomate', 'kg', 1000, 3000),
       ('Massa de Pizza', 'un', 300, 10),
       ('Molho de Tomate', 'kg', 1000, 2000),
       ('Manjericão Fresco', 'kg', 1000, 500),
       ('Laranja', 'kg', 1000, 10000)
ON DUPLICATE KEY UPDATE unidade = VALUES(unidade);

-- Lotes Ativos (Valores de peso estritamente em gramas: quantidade_g)
-- 11 lotes cadastrados com prazos variados (crítico <= 2 dias, atenção 3-5 dias, seguro > 5 dias)
INSERT INTO tb_lote (ingrediente_id, quantidade_g, custo_unitario, data_validade, data_entrada, numero_lote, observacao)
VALUES (1, 4000, 0.0150, DATE_ADD(CURDATE(), INTERVAL 15 DAY), CURDATE(), 'LOT-PAO-01', 'Lote de pão fresco'),
       (2, 10000, 0.0325, DATE_ADD(CURDATE(), INTERVAL 2 DAY), CURDATE(), 'LOT-CARNE-01', 'Carne em risco de validade'),
       (3, 8000, 0.0380, DATE_ADD(CURDATE(), INTERVAL 3 DAY), CURDATE(), 'LOT-QUEIJO-01', 'Queijo mussarela'),
       (4, 3500, 0.0080, DATE_ADD(CURDATE(), INTERVAL 1 DAY), CURDATE(), 'LOT-ALFACE-01', 'Alface em vencimento crítico'),
       (5, 5000, 0.0065, DATE_ADD(CURDATE(), INTERVAL 9 DAY), CURDATE(), 'LOT-TOMATE-01', 'Tomate maduro'),
       (6, 6000, 0.0133, DATE_ADD(CURDATE(), INTERVAL 16 DAY), CURDATE(), 'LOT-MASSA-01', 'Massa pré-assada'),
       (7, 4000, 0.0120, DATE_ADD(CURDATE(), INTERVAL 20 DAY), CURDATE(), 'LOT-MOLHO-01', 'Molho caseiro'),
       (8, 1000, 0.0250, DATE_ADD(CURDATE(), INTERVAL 2 DAY), CURDATE(), 'LOT-MANJ-01', 'Ervas frescas'),
       (9, 25000, 0.0045, DATE_ADD(CURDATE(), INTERVAL 8 DAY), CURDATE(), 'LOT-LARANJA-01', 'Frutas da estação'),
       (2, 5000, 0.0330, DATE_ADD(CURDATE(), INTERVAL 10 DAY), CURDATE(), 'LOT-CARNE-02', 'Lote reserva carne'),
       (3, 4000, 0.0375, DATE_ADD(CURDATE(), INTERVAL 12 DAY), CURDATE(), 'LOT-QUEIJO-02', 'Lote reserva queijo');

-- Fichas Técnicas
INSERT INTO tb_ficha_tecnica (produto_id, ingrediente_id, quantidade_usada, unidade)
VALUES (1, 1, 1.0000, 'un'),
       (1, 2, 0.1800, 'kg'),
       (1, 3, 0.0500, 'kg'),
       (1, 4, 0.0300, 'kg'),
       (1, 5, 0.0400, 'kg'),
       (2, 6, 1.0000, 'un'),
       (2, 7, 0.1500, 'kg'),
       (2, 3, 0.2000, 'kg'),
       (2, 8, 0.0200, 'kg'),
       (3, 4, 0.1500, 'kg'),
       (3, 3, 0.0300, 'kg'),
       (4, 9, 0.5000, 'kg')
ON DUPLICATE KEY UPDATE quantidade_usada = VALUES(quantidade_usada);
