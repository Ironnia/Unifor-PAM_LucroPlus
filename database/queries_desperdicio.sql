USE lucroplus_db;

SELECT
    i.nome AS ingrediente,
    SUM(l.quantidade_g) AS quantidade_perdida_g,
    i.unidade,
    SUM((l.quantidade_g / 1000.0) * (l.custo_unitario * 1000.0)) AS valor_perdido_rs
FROM tb_lote l
JOIN tb_ingrediente i ON l.ingrediente_id = i.id
WHERE l.data_validade < CURDATE()
  AND MONTH(l.data_validade) = MONTH(CURDATE())
  AND YEAR(l.data_validade) = YEAR(CURDATE())
GROUP BY i.id, i.nome, i.unidade
ORDER BY valor_perdido_rs DESC;

SELECT
    DATE_FORMAT(l.data_validade, '%Y-%m') AS mes_ano,
    SUM(l.quantidade_g * l.custo_unitario) AS valor_perdido_rs
FROM tb_lote l
WHERE l.data_validade < CURDATE()
  AND l.data_validade >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
GROUP BY DATE_FORMAT(l.data_validade, '%Y-%m')
ORDER BY mes_ano ASC;
