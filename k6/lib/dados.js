// Geradores de dados sintéticos para os cenários.
//
// Regra: nada aqui pode parecer dado de pessoa real. Nome, e-mail e telefone
// são obviamente de teste, e o CPF é gerado com dígito verificador válido
// porque o backend valida — mas a partir de uma faixa reservada, para não
// colidir com CPF de gente de verdade.

/** Prefixo em tudo que o teste cria, para a limpeza saber o que apagar. */
export const MARCA = 'CARGA';

export function inteiro(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function escolher(lista) {
  return lista[inteiro(0, lista.length - 1)];
}

/**
 * CPF sintético com DV correto.
 *
 * Os 9 primeiros dígitos saem de 900.000.000–999.999.999: essa faixa não é
 * emitida pela Receita para pessoa física, então um CPF daqui é válido na
 * conta mas não pertence a ninguém.
 */
export function cpf() {
  const base = String(inteiro(900000000, 999999999)).padStart(9, '0');
  const digitos = base.split('').map(Number);
  const dv = (nums) => {
    const peso = nums.length + 1;
    const soma = nums.reduce((acc, n, i) => acc + n * (peso - i), 0);
    const resto = (soma * 10) % 11;
    return resto === 10 ? 0 : resto;
  };
  const d1 = dv(digitos);
  const d2 = dv([...digitos, d1]);
  return `${base}${d1}${d2}`;
}

const NOMES = ['Ana', 'Bruno', 'Carla', 'Diego', 'Elisa', 'Fabio', 'Gabi', 'Heitor', 'Iara', 'Joao'];
const SOBRENOMES = ['Teste', 'Carga', 'Sintetico', 'Simulado', 'Fake'];

export function cliente() {
  const nome = `${escolher(NOMES)} ${escolher(SOBRENOMES)} ${MARCA}`;
  const id = `${Date.now()}${inteiro(100, 999)}`;
  return {
    nome,
    documento: cpf(),
    documentoTipo: 'CPF',
    // Domínio .invalid é reservado pela RFC 2606: nunca resolve, então nem por
    // acidente um e-mail do teste chega a uma caixa real.
    email: `carga.${id}@exemplo.invalid`,
    telefone: `11${inteiro(900000000, 999999999)}`,
    dataNascimento: `19${inteiro(70, 99)}-0${inteiro(1, 9)}-1${inteiro(0, 8)}`,
    termoAceite: true,
    observacoes: `${MARCA} — cliente sintético de teste de carga`,
  };
}

/** Horímetro plausível: jetskis de locadora vivem entre 50 e 900 horas. */
export function horimetro() {
  return Number((inteiro(5000, 90000) / 100).toFixed(1));
}

/** Duração em blocos de 15 min, como a cobrança (RN01). */
export function duracaoPrevista() {
  return escolher([30, 45, 60, 90, 120]);
}

/** Checklist de check-out no formato que o backend espera (string JSON). */
export function checklist() {
  return JSON.stringify({
    casco: 'OK',
    combustivel: 'OK',
    colete: 'OK',
    avarias: 'nenhuma',
    origem: MARCA,
  });
}
