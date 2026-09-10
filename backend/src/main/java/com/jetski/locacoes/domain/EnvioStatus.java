package com.jetski.locacoes.domain;

/**
 * Estado do envio por e-mail de um documento emitido, por destino (V065).
 *
 * <p>Antes só existiam {@code marinha_enviado_em}/{@code cliente_enviado_em}, e
 * {@code NULL} confundia quatro situações diferentes. Com o envio saindo do request,
 * a tela do balcão precisa distinguir "ainda enviando" de "falhou" — e o operador
 * precisa saber se o remédio é reenviar, completar a documentação ou cadastrar um
 * e-mail.
 *
 * <p>Estados terminais: todos, menos {@link #PENDENTE}.
 */
public enum EnvioStatus {

    /** Não há o que enviar a esse destino — ex.: habilitação por CHA não gera ofício à Capitania. */
    NAO_APLICAVEL,

    /** Documentação incompleta: a Marinha não pode ser notificada até as pendências saírem. */
    BLOQUEADO,

    /** Destino sem endereço cadastrado. Remédio diferente de falha: cadastrar o e-mail. */
    SEM_DESTINATARIO,

    /** Enfileirado para envio fora do request; ainda não tentado. */
    PENDENTE,

    /** Servidor SMTP aceitou a mensagem. */
    ENVIADO,

    /** Tentado e falhou; o motivo fica em {@code *_envio_erro}. */
    FALHOU;

    /** Terminal = não muda mais sozinho; é a condição de parada do polling. */
    public boolean terminal() {
        return this != PENDENTE;
    }
}
