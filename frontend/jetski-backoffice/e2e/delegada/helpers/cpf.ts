/**
 * CPF válido (dígitos verificadores corretos) e aleatório, formatado "000.000.000-00".
 * O balcão valida o CPF e busca o cliente por ele: cada execução precisa de um novo,
 * senão reaproveitaria o cliente de uma rodada anterior.
 */
export function gerarCpf(): string {
  const d = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10));
  // CPFs com todos os dígitos iguais são inválidos por regra.
  if (d.every((x) => x === d[0])) d[0] = (d[0] + 1) % 10;

  const verificador = (base: number[]) => {
    const soma = base.reduce((acc, x, i) => acc + x * (base.length + 1 - i), 0);
    const resto = (soma * 10) % 11;
    return resto === 10 ? 0 : resto;
  };
  d.push(verificador(d));
  d.push(verificador(d));

  const s = d.join('');
  return `${s.slice(0, 3)}.${s.slice(3, 6)}.${s.slice(6, 9)}-${s.slice(9)}`;
}
