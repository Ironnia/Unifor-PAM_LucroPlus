SET time_zone='+00:00';
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @fifo_cenario=CONVERT(@fifo_cenario USING utf8mb4) COLLATE utf8mb4_unicode_ci;
DELIMITER $$
DROP PROCEDURE IF EXISTS fifo_carregar$$
CREATE PROCEDURE fifo_carregar()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF DATABASE() IS NULL OR LEFT(DATABASE(),15) <> 'fifo_lucroplus_' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FIFO exige banco isolado com prefixo correto';
 END IF;
 IF @fifo_cenario IS NULL OR @fifo_cenario NOT IN ('parcial','integral','perda','travessia','inicio_efetivo','inicio_futuro','historico') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Escolha um cenario FIFO documentado';
 END IF;
 CREATE TABLE IF NOT EXISTS fifo_fixture (id INT PRIMARY KEY, referencia DATE NOT NULL, cenario VARCHAR(30) NOT NULL);
 IF EXISTS(SELECT 1 FROM fifo_fixture WHERE referencia <> '2026-10-01' OR cenario <> @fifo_cenario) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Outra referencia/cenario: use banco novo';
 END IF;
CREATE TABLE IF NOT EXISTS tb_restaurante (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nome VARCHAR(150) NOT NULL,
    PRIMARY KEY (id)
);
CREATE TABLE IF NOT EXISTS tb_usuario
(
    id         BIGINT                    NOT NULL AUTO_INCREMENT,
    nome       VARCHAR(100)              NOT NULL,
    email      VARCHAR(150)              NOT NULL,
    senha_hash VARCHAR(255)              NOT NULL,
    tipo       ENUM ('ADMIN', 'GERENTE') NOT NULL,
    ativo      BOOLEAN                   NOT NULL DEFAULT TRUE,
    restaurante_id BIGINT NULL,

    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uq_usuario_email UNIQUE (email),
    CONSTRAINT fk_usuario_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id)
);
CREATE TABLE IF NOT EXISTS tb_produto
(
    restaurante_id BIGINT NULL,
    CONSTRAINT fk_tb_produto_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id),
    id_externo VARCHAR(100) NULL,
    UNIQUE KEY uq_produtos_restaurante_externo(restaurante_id,id_externo),
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
CREATE TABLE IF NOT EXISTS tb_ingrediente
(
    restaurante_id BIGINT NULL,
    CONSTRAINT fk_tb_ingrediente_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id),
    id_externo VARCHAR(100) NULL,
    UNIQUE KEY uq_ingredientes_restaurante_externo(restaurante_id,id_externo),
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
CREATE TABLE IF NOT EXISTS tb_lote
(
    restaurante_id BIGINT NULL,
    CONSTRAINT fk_tb_lote_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id),
    id_externo VARCHAR(100) NULL,
    origem VARCHAR(20) NULL,
    compra_id_externo VARCHAR(100) NULL,
    quantidade_inicial_g INTEGER NULL,
    quantidade_origem DECIMAL(18,6) NULL,
    unidade_origem VARCHAR(20) NULL,
    custo_unidade_origem DECIMAL(18,8) NULL,
    gramas_por_unidade_origem INTEGER NULL,
    entrada_em TIMESTAMP(6) NULL,
    importado_em TIMESTAMP(6) NULL,
    UNIQUE KEY uq_lotes_restaurante_origem_externo(restaurante_id,origem,id_externo),
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    ingrediente_id BIGINT        NOT NULL,
    quantidade_g   INTEGER       NOT NULL,
    custo_unitario DECIMAL(18, 8) NOT NULL,
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
CREATE TABLE IF NOT EXISTS tb_venda
(
    restaurante_id BIGINT NULL,
    CONSTRAINT fk_tb_venda_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id),
    id_externo VARCHAR(100) NULL,
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    usuario_id  BIGINT,
    data_venda  DATE           NOT NULL,
    valor_total DECIMAL(10, 2) NOT NULL,
    origem      VARCHAR(20)    NOT NULL DEFAULT 'importado',

    data_venda_em TIMESTAMP(6) NULL,
    importado_em TIMESTAMP(6) NULL,
    UNIQUE KEY uq_vendas_restaurante_origem_externo(restaurante_id,origem,id_externo),
    CONSTRAINT pk_venda PRIMARY KEY (id),
    CONSTRAINT fk_venda_usuario FOREIGN KEY (usuario_id)
        REFERENCES tb_usuario (id) ON DELETE SET NULL,
    INDEX idx_venda_data (data_venda),
    INDEX idx_venda_origem (origem),
    INDEX idx_venda_usuario (usuario_id)
);
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
CREATE TABLE IF NOT EXISTS tb_promocao
(
    restaurante_id BIGINT NULL,
    CONSTRAINT fk_tb_promocao_restaurante FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id),
    id            BIGINT                                 NOT NULL AUTO_INCREMENT,
    produto_id    BIGINT                                 NOT NULL,
    desconto_pct  INTEGER                                NULL,
    motivo        TEXT                                   NOT NULL,
    status        VARCHAR(24)                            NOT NULL,
    data_sugestao DATE                                   NOT NULL,
    data_ativacao DATE,
    lote_id       BIGINT NULL,
    data_inicio   DATE NULL,
    data_fim      DATE NULL,
    data_confirmacao DATE NULL,
    quantidade_referencia_g INTEGER NULL,
    valor_em_risco DECIMAL(18, 2) NULL,
    valor_salvo   DECIMAL(18, 2) NOT NULL DEFAULT 0.00,

    ativada_em TIMESTAMP(6) NULL,
    inicio_efetivo_em TIMESTAMP(6) NULL,
    fim_exclusivo_em TIMESTAMP(6) NULL,
    custo_referencia_por_grama DECIMAL(18,8) NULL,
    quantidade_consumida_g INTEGER NULL,
    quantidade_descartada_g INTEGER NULL,
    valor_perdido DECIMAL(18,2) NULL,
    confirmada_em TIMESTAMP(6) NULL,
    confirmada_por BIGINT NULL,
    CONSTRAINT fk_promocao_confirmada_por FOREIGN KEY(confirmada_por) REFERENCES tb_usuario(id),
    CONSTRAINT pk_promocao PRIMARY KEY (id),
    CONSTRAINT fk_promocao_produto FOREIGN KEY (produto_id)
        REFERENCES tb_produto (id) ON DELETE CASCADE,
    CONSTRAINT fk_promocao_lote FOREIGN KEY (lote_id) REFERENCES tb_lote (id),
    UNIQUE KEY uq_promocao_lote_produto (lote_id, produto_id),
    INDEX idx_promocao_status (status),
    INDEX idx_promocao_data (data_sugestao),
    INDEX idx_promocao_produto (produto_id)
);
CREATE TABLE IF NOT EXISTS tb_alerta
(
    id          BIGINT                                NOT NULL AUTO_INCREMENT,
    lote_id     BIGINT                                NOT NULL,
    tipo        ENUM ('VENCIMENTO', 'ESTOQUE_MINIMO') NOT NULL,
    mensagem    TEXT                                  NOT NULL,
    data_alerta DATE                                  NOT NULL,
    visualizado BOOLEAN                               NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_alerta PRIMARY KEY (id),
    UNIQUE KEY uq_alerta_lote_tipo (lote_id, tipo),
    CONSTRAINT fk_alerta_lote FOREIGN KEY (lote_id)
        REFERENCES tb_lote (id) ON DELETE CASCADE,
    INDEX idx_alerta_tipo (tipo),
    INDEX idx_alerta_visualizado (visualizado),
    INDEX idx_alerta_data (data_alerta)
);
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
CREATE TABLE IF NOT EXISTS tb_configuracao
(
    chave VARCHAR(100) NOT NULL,
    valor VARCHAR(255),

    CONSTRAINT pk_configuracao PRIMARY KEY (chave)
);
CREATE TABLE IF NOT EXISTS tb_configuracao_restaurante (
    restaurante_id BIGINT NOT NULL,
    chave VARCHAR(100) NOT NULL,
    valor VARCHAR(255) NULL,
    PRIMARY KEY(restaurante_id,chave),
    CONSTRAINT fk_configuracao_restaurante FOREIGN KEY(restaurante_id) REFERENCES tb_restaurante(id)
);
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
CALL fifo_carregar()$$
DROP PROCEDURE fifo_carregar$$
DELIMITER ;
