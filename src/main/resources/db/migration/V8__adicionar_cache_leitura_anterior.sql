ALTER TABLE medicoes
    ADD COLUMN cache_leitura_anterior NUMERIC(19,6),
    ADD CONSTRAINT ck_medicoes_cache_leitura_anterior
        CHECK (cache_leitura_anterior >= 0);

COMMENT ON COLUMN medicoes.cache_leitura_anterior IS
    'Copia da leitura da medicao imediatamente anterior da mesma instalacao para facilitar o calculo do consumo. Campo desnormalizado; NULL indica cache nao preenchido ou ausencia de medicao anterior.';
