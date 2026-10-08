-- Adiciona indice unico para evitar duplicacao de alertas
DELIMITER $$
DROP PROCEDURE IF EXISTS migrar_alertas_unicos$$
CREATE PROCEDURE migrar_alertas_unicos()
BEGIN
    DECLARE removidos INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    START TRANSACTION;
    IF EXISTS (SELECT 1 FROM tb_alerta GROUP BY lote_id,tipo HAVING COUNT(*)>1
        AND NOT (lote_id=19 AND tipo='VENCIMENTO' AND COUNT(*)=2 AND MIN(id)=27 AND MAX(id)=28)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicacao fora do par diagnosticado; nenhuma limpeza permitida';
    END IF;
    IF EXISTS (SELECT 1 FROM tb_alerta WHERE id=28) THEN
        IF NOT EXISTS (
            SELECT 1 FROM tb_alerta a JOIN tb_alerta b ON b.id=28
            WHERE a.id=27 AND a.lote_id=19 AND b.lote_id=19
                AND a.tipo='VENCIMENTO' AND b.tipo='VENCIMENTO'
                AND BINARY a.mensagem=BINARY b.mensagem
                AND a.data_alerta='2026-08-18' AND b.data_alerta=a.data_alerta
                AND a.visualizado=0 AND b.visualizado=0
        ) OR EXISTS (SELECT 1 FROM tb_acao_alerta WHERE lote_id=19 OR alerta_id IN (27,28)) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Estado ou referencias de 27/28 mudou; abortado';
        END IF;
        DELETE FROM tb_alerta WHERE id=28 AND lote_id=19 AND tipo='VENCIMENTO';
        SET removidos=ROW_COUNT();
    END IF;
    COMMIT;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='tb_alerta' AND index_name='uq_alerta_lote_tipo') THEN
        ALTER TABLE tb_alerta ADD UNIQUE KEY uq_alerta_lote_tipo (lote_id,tipo);
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='tb_alerta' AND index_name='uq_alerta_lote_tipo' AND non_unique=0
        AND ((seq_in_index=1 AND column_name='lote_id' AND sub_part IS NULL)
          OR (seq_in_index=2 AND column_name='tipo' AND sub_part IS NULL))) <> 2
        OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
          AND table_name='tb_alerta' AND index_name='uq_alerta_lote_tipo') <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Indice existente incompativel; investigar';
    END IF;
    SELECT removidos AS alertas_duplicados_removidos;
END$$
CALL migrar_alertas_unicos()$$
DROP PROCEDURE migrar_alertas_unicos$$
DELIMITER ;
