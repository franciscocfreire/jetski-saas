-- Migration V032: Add payment type and bonus tracking
-- Adds tipo_pagamento to pagamento_vendedor and links bonus to payments

-- 1. Add tipo_pagamento column to pagamento_vendedor
ALTER TABLE pagamento_vendedor
ADD COLUMN tipo_pagamento VARCHAR(20) DEFAULT 'PIX' NOT NULL;

-- 2. Add bonus tracking columns to pagamento_vendedor
ALTER TABLE pagamento_vendedor
ADD COLUMN valor_bonus NUMERIC(10,2) DEFAULT 0 NOT NULL,
ADD COLUMN qtd_bonus INTEGER DEFAULT 0 NOT NULL;

-- 3. Add payment tracking to bonus_vendedor
ALTER TABLE bonus_vendedor
ADD COLUMN pagamento_id UUID REFERENCES pagamento_vendedor(id);

-- 4. Add constraint for tipo_pagamento values
ALTER TABLE pagamento_vendedor ADD CONSTRAINT pagamento_vendedor_tipo_pagamento_check
CHECK (tipo_pagamento IN ('PIX', 'DINHEIRO'));

-- 5. Create index for bonus payment lookup
CREATE INDEX idx_bonus_vendedor_pagamento ON bonus_vendedor(pagamento_id) WHERE pagamento_id IS NOT NULL;
