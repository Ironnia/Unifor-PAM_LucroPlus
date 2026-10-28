USE lucroplus_db;

SELECT
    l.id AS lote_id,
    i.nome AS ingrediente,
    l.data_validade,
    DATEDIFF(l.data_validade, CURDATE()) AS dias_para_vencer,
    CASE
        WHEN DATEDIFF(l.data_validade, CURDATE()) <= 7 THEN 'CRITICO'
        ELSE 'ATENCAO'
    END AS criticidade,
    l.quantidade_g
FROM tb_lote l
JOIN tb_ingrediente i ON l.ingrediente_id = i.id
WHERE l.data_validade BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 14 DAY)
  AND l.quantidade_g > 0
ORDER BY l.data_validade ASC;

INSERT INTO tb_alerta (lote_id, tipo, mensagem, data_alerta, visualizado)
SELECT
    l.id,
    'VENCIMENTO',
    CONCAT('O lote do ingrediente ', i.nome, ' vence em ', DATEDIFF(l.data_validade, CURDATE()),
           ' dia(s). ', CASE WHEN DATEDIFF(l.data_validade, CURDATE()) <= 7 THEN 'CRITICO' ELSE 'ATENCAO' END, '.'),
    CURDATE(),
    FALSE
FROM tb_lote l
JOIN tb_ingrediente i ON l.ingrediente_id = i.id
WHERE l.data_validade BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 14 DAY)
  AND l.quantidade_g > 0
  AND NOT EXISTS (
      SELECT 1 FROM tb_alerta a
      WHERE a.lote_id = l.id
        AND a.tipo = 'VENCIMENTO'
  )

ON DUPLICATE KEY UPDATE id = tb_alerta.id;
