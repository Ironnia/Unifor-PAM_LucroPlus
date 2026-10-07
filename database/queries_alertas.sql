USE lucroplus_db;

SELECT
    l.id AS lote_id,
    i.nome AS ingrediente,
    l.data_validade,
    DATEDIFF(DATE_SUB(l.data_validade, INTERVAL 1 DAY), CURDATE()) AS dias_para_prazo,
    CASE
        WHEN DATEDIFF(DATE_SUB(l.data_validade, INTERVAL 1 DAY), CURDATE()) <= 7 THEN 'CRITICO'
        ELSE 'ATENCAO'
    END AS criticidade,
    l.quantidade_g
FROM tb_lote l
JOIN tb_ingrediente i ON l.ingrediente_id = i.id
WHERE l.data_validade BETWEEN DATE_ADD(CURDATE(), INTERVAL 1 DAY) AND DATE_ADD(CURDATE(), INTERVAL 15 DAY)
  AND l.quantidade_g > 0
ORDER BY l.data_validade ASC;

INSERT INTO tb_alerta (lote_id, tipo, mensagem, data_alerta, visualizado)
SELECT
    l.id,
    'VENCIMENTO',
    CONCAT('O lote do ingrediente ', i.nome, ' atinge o prazo limite de venda em ', DATEDIFF(DATE_SUB(l.data_validade, INTERVAL 1 DAY), CURDATE()),
           ' dia(s). ', CASE WHEN DATEDIFF(DATE_SUB(l.data_validade, INTERVAL 1 DAY), CURDATE()) <= 7 THEN 'CRITICO' ELSE 'ATENCAO' END, '.'),
    CURDATE(),
    FALSE
FROM tb_lote l
JOIN tb_ingrediente i ON l.ingrediente_id = i.id
WHERE l.data_validade BETWEEN DATE_ADD(CURDATE(), INTERVAL 1 DAY) AND DATE_ADD(CURDATE(), INTERVAL 15 DAY)
  AND l.quantidade_g > 0
  AND NOT EXISTS (
      SELECT 1 FROM tb_alerta a
      WHERE a.lote_id = l.id
        AND a.tipo = 'VENCIMENTO'
  );
