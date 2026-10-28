DELIMITER $$
CREATE PROCEDURE corrigir_nome_produto_1_avisos()
BEGIN
    DECLARE nome_anterior TEXT DEFAULT NULL;
    DECLARE afetadas INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF COALESCE(@avisos_backup_verificado, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Backup restaurado e verificado obrigatorio';
    END IF;
    START TRANSACTION;
    SELECT HEX(nome) INTO nome_anterior FROM tb_produto WHERE id = 1 FOR UPDATE;
    IF nome_anterior IS NULL OR nome_anterior NOT IN (
        '582D4275726775657220436CE2949CC3AD737369636F',
        '582D4275726775657220436CC3A1737369636F'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto 1 ausente ou nome divergente; nenhuma correcao aplicada';
    END IF;
    IF nome_anterior = '582D4275726775657220436CE2949CC3AD737369636F' THEN
        UPDATE tb_produto SET nome = CONVERT(UNHEX('582D4275726775657220436CC3A1737369636F') USING utf8mb4)
        WHERE id = 1 AND HEX(nome) = nome_anterior;
        SET afetadas = ROW_COUNT();
        IF afetadas <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Quantidade de linhas inesperada';
        END IF;
    END IF;
    COMMIT;
    SELECT afetadas AS nomes_corrigidos;
END$$
DELIMITER ;
CALL corrigir_nome_produto_1_avisos();
DROP PROCEDURE corrigir_nome_produto_1_avisos;
