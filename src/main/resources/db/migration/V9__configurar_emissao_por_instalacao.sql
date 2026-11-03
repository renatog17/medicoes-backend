ALTER TABLE instalacoes
    ADD COLUMN dia_emissao INTEGER NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_instalacoes_dia_emissao CHECK (dia_emissao BETWEEN 1 AND 31);

ALTER TABLE medicoes
    ADD COLUMN falha_emissao_fatura BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE faturas
    DROP CONSTRAINT uk_fatura_competencia,
    ADD CONSTRAINT uk_faturas_medicao_atual UNIQUE (medicao_atual_id);
