-- ============================================================================
-- V001: Baseline schema (consolidado)
-- ============================================================================
-- Schema final completo gerado a partir do banco dev (pg_dump --schema=public).
-- Consolida as antigas migrations V001..V036 num único baseline idempotente-friendly.
-- Inclui: tabelas, índices, constraints, RLS policies, funções e triggers.
-- ============================================================================

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.10 (Debian 16.10-1.pgdg13+1)
-- Dumped by pg_dump version 16.10 (Debian 16.10-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
--



--
-- Name: get_current_tenant_id(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_current_tenant_id() RETURNS uuid
    LANGUAGE plpgsql STABLE
    AS $$
BEGIN
    RETURN NULLIF(current_setting('app.tenant_id', true), '')::uuid;
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$$;


--
-- Name: update_presenca_vendedor_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_presenca_vendedor_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;


--
-- Name: update_updated_at_column(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_updated_at_column() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: abastecimento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.abastecimento (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    jetski_id uuid NOT NULL,
    locacao_id uuid,
    responsavel_id uuid,
    data_hora timestamp with time zone NOT NULL,
    litros numeric(10,3) NOT NULL,
    preco_litro numeric(10,2) NOT NULL,
    custo_total numeric(10,2) NOT NULL,
    tipo character varying(20),
    observacoes character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    foto_id uuid,
    CONSTRAINT abastecimento_custo_total_check CHECK ((custo_total >= (0)::numeric)),
    CONSTRAINT abastecimento_litros_check CHECK ((litros > (0)::numeric)),
    CONSTRAINT abastecimento_preco_litro_check CHECK ((preco_litro > (0)::numeric)),
    CONSTRAINT abastecimento_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['PRE_LOCACAO'::character varying, 'POS_LOCACAO'::character varying, 'FROTA'::character varying])::text[])))
);

ALTER TABLE ONLY public.abastecimento FORCE ROW LEVEL SECURITY;


--
-- Name: abastecimento_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.abastecimento_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: abastecimento_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.abastecimento_id_seq OWNED BY public.abastecimento.id;


--
-- Name: assinatura; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assinatura (
    id integer NOT NULL,
    tenant_id uuid NOT NULL,
    plano_id integer NOT NULL,
    ciclo character varying(20) DEFAULT 'mensal'::character varying,
    dt_inicio date NOT NULL,
    dt_fim date,
    status character varying(20) DEFAULT 'ativa'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.assinatura FORCE ROW LEVEL SECURITY;


--
-- Name: assinatura_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.assinatura_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: assinatura_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.assinatura_id_seq OWNED BY public.assinatura.id;


--
-- Name: auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid,
    usuario_id uuid,
    acao character varying(50) NOT NULL,
    entidade character varying(50) NOT NULL,
    entidade_id uuid,
    dados_anteriores jsonb,
    dados_novos jsonb,
    ip character varying(45),
    user_agent text,
    trace_id character varying(100),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.auditoria FORCE ROW LEVEL SECURITY;


--
-- Name: bonus_vendedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bonus_vendedor (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    vendedor_id uuid NOT NULL,
    meta_atingida integer NOT NULL,
    valor_bonus numeric(10,2) NOT NULL,
    status character varying(20) DEFAULT 'PENDENTE'::character varying NOT NULL,
    aprovado_por uuid,
    aprovado_em timestamp with time zone,
    pago_por uuid,
    pago_em timestamp with time zone,
    referencia_pagamento character varying(100),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    pagamento_id uuid,
    CONSTRAINT bonus_status_check CHECK (((status)::text = ANY ((ARRAY['PENDENTE'::character varying, 'APROVADO'::character varying, 'PAGO'::character varying, 'CANCELADO'::character varying])::text[])))
);

ALTER TABLE ONLY public.bonus_vendedor FORCE ROW LEVEL SECURITY;


--
-- Name: cliente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cliente (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    nome character varying(200) NOT NULL,
    documento character varying(20),
    data_nascimento date,
    genero character varying(20),
    email character varying(255),
    telefone character varying(30),
    whatsapp character varying(30),
    endereco jsonb,
    termo_aceite boolean DEFAULT false,
    ativo boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.cliente FORCE ROW LEVEL SECURITY;


--
-- Name: comissao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comissao (
    tenant_id uuid NOT NULL,
    locacao_id uuid NOT NULL,
    vendedor_id uuid NOT NULL,
    valor_total_locacao numeric(10,2) NOT NULL,
    valor_comissionavel numeric(10,2) NOT NULL,
    percentual_aplicado numeric(5,2),
    valor_comissao numeric(10,2) NOT NULL,
    status character varying(20) DEFAULT 'PENDENTE'::character varying,
    pago_em timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    data_locacao timestamp with time zone NOT NULL,
    tipo_comissao character varying(20) NOT NULL,
    valor_combustivel numeric(10,2) DEFAULT 0,
    valor_multas numeric(10,2) DEFAULT 0,
    valor_taxas numeric(10,2) DEFAULT 0,
    politica_nome character varying(100),
    politica_nivel character varying(20),
    observacoes character varying(500),
    aprovado_por uuid,
    aprovado_em timestamp with time zone,
    pago_por uuid,
    referencia_pagamento character varying(100),
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    politica_id uuid,
    venda_acima_preco_base boolean DEFAULT true,
    CONSTRAINT comissao_status_check CHECK (((status)::text = ANY ((ARRAY['PENDENTE'::character varying, 'APROVADA'::character varying, 'PAGA'::character varying, 'CANCELADA'::character varying])::text[])))
);

ALTER TABLE ONLY public.comissao FORCE ROW LEVEL SECURITY;


--
-- Name: convite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.convite (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    email character varying(255) NOT NULL,
    nome character varying(200),
    papeis text[] DEFAULT '{}'::text[],
    token character varying(255) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    activated_at timestamp with time zone,
    usuario_id uuid,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    email_sent_at timestamp with time zone,
    email_sent_count integer DEFAULT 0 NOT NULL,
    email_error text,
    password_reset_link text,
    temporary_password_hash character varying(255),
    status character varying(20) DEFAULT 'PENDING'::character varying,
    CONSTRAINT convite_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVATED'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])))
);

ALTER TABLE ONLY public.convite FORCE ROW LEVEL SECURITY;


--
-- Name: despesa_manutencao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.despesa_manutencao (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    os_manutencao_id uuid NOT NULL,
    dt_vencimento date NOT NULL,
    numero_parcela integer DEFAULT 1 NOT NULL,
    total_parcelas integer DEFAULT 1 NOT NULL,
    valor numeric(10,2) NOT NULL,
    status character varying(20) DEFAULT 'PENDENTE'::character varying NOT NULL,
    aprovado_por integer,
    aprovado_em timestamp with time zone,
    pago_por integer,
    pago_em timestamp with time zone,
    referencia_pagamento character varying(100),
    observacoes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT despesa_manutencao_parcela_valida CHECK (((numero_parcela > 0) AND (numero_parcela <= total_parcelas))),
    CONSTRAINT despesa_manutencao_status_check CHECK (((status)::text = ANY ((ARRAY['PENDENTE'::character varying, 'APROVADA'::character varying, 'REJEITADA'::character varying, 'PAGA'::character varying, 'CANCELADA'::character varying])::text[]))),
    CONSTRAINT despesa_manutencao_valor_positivo CHECK ((valor > (0)::numeric))
);

ALTER TABLE ONLY public.despesa_manutencao FORCE ROW LEVEL SECURITY;


--
-- Name: despesa_operacional; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.despesa_operacional (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    dt_referencia date NOT NULL,
    categoria character varying(30) NOT NULL,
    descricao character varying(255),
    valor numeric(10,2) NOT NULL,
    responsavel_id uuid,
    status character varying(20) DEFAULT 'PENDENTE'::character varying NOT NULL,
    aprovado_por uuid,
    aprovado_em timestamp with time zone,
    pago_por uuid,
    pago_em timestamp with time zone,
    referencia_pagamento character varying(100),
    observacoes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT despesa_categoria_check CHECK (((categoria)::text = ANY ((ARRAY['DIARIA_FUNCIONARIO'::character varying, 'REFEICAO'::character varying, 'COMBUSTIVEL_PROPRIO'::character varying, 'LIMPEZA'::character varying, 'TAXA_ADMINISTRATIVA'::character varying, 'TRANSPORTE'::character varying, 'MATERIAL_ESCRITORIO'::character varying, 'OUTROS'::character varying])::text[]))),
    CONSTRAINT despesa_status_check CHECK (((status)::text = ANY ((ARRAY['PENDENTE'::character varying, 'APROVADA'::character varying, 'REJEITADA'::character varying, 'PAGA'::character varying])::text[]))),
    CONSTRAINT despesa_valor_positivo CHECK ((valor > (0)::numeric))
);

ALTER TABLE ONLY public.despesa_operacional FORCE ROW LEVEL SECURITY;


--
-- Name: fechamento_diario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fechamento_diario (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    dt_referencia date NOT NULL,
    total_locacoes integer DEFAULT 0,
    total_faturado numeric(12,2) DEFAULT 0,
    total_combustivel numeric(12,2) DEFAULT 0,
    total_comissoes numeric(12,2) DEFAULT 0,
    observacoes text,
    fechado_por uuid,
    dt_fechamento timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    bloqueado boolean DEFAULT false NOT NULL,
    operador_id uuid,
    total_dinheiro numeric(12,2) DEFAULT 0,
    total_cartao numeric(12,2) DEFAULT 0,
    total_pix numeric(12,2) DEFAULT 0,
    status character varying(20) DEFAULT 'aberto'::character varying,
    divergencias_json jsonb,
    valores_hash character varying(64),
    total_despesas_operacionais numeric(12,2) DEFAULT 0,
    total_diarias_vendedores numeric(12,2) DEFAULT 0
);

ALTER TABLE ONLY public.fechamento_diario FORCE ROW LEVEL SECURITY;


--
-- Name: fechamento_mensal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fechamento_mensal (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    ano integer NOT NULL,
    mes integer NOT NULL,
    total_locacoes integer DEFAULT 0,
    total_receita numeric(12,2) DEFAULT 0,
    total_combustivel numeric(12,2) DEFAULT 0,
    total_comissoes numeric(12,2) DEFAULT 0,
    total_manutencao numeric(12,2) DEFAULT 0,
    observacoes text,
    fechado_por uuid,
    fechado_em timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    valores_hash character varying(64),
    total_despesas_operacionais numeric(12,2) DEFAULT 0,
    bloqueado boolean DEFAULT false NOT NULL,
    status character varying(20) DEFAULT 'aberto'::character varying,
    operador_id uuid,
    relatorio_url text,
    resultado_liquido numeric(12,2) DEFAULT 0,
    total_faturado numeric(12,2) DEFAULT 0,
    total_custos numeric(12,2) DEFAULT 0,
    total_manutencoes numeric(12,2) DEFAULT 0,
    dt_fechamento timestamp with time zone,
    total_diarias_vendedores numeric(12,2) DEFAULT 0
);

ALTER TABLE ONLY public.fechamento_mensal FORCE ROW LEVEL SECURITY;


--
-- Name: foto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.foto (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    locacao_id uuid,
    jetski_id uuid,
    tipo character varying(20) NOT NULL,
    url text NOT NULL,
    s3_key text NOT NULL,
    filename character varying(255) NOT NULL,
    content_type character varying(100) NOT NULL,
    size_bytes bigint,
    sha256_hash character varying(64),
    uploaded_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT foto_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['CHECKIN_FRENTE'::character varying, 'CHECKIN_LATERAL_ESQ'::character varying, 'CHECKIN_LATERAL_DIR'::character varying, 'CHECKIN_HORIMETRO'::character varying, 'CHECKOUT_FRENTE'::character varying, 'CHECKOUT_LATERAL_ESQ'::character varying, 'CHECKOUT_LATERAL_DIR'::character varying, 'CHECKOUT_HORIMETRO'::character varying, 'INCIDENTE'::character varying, 'MANUTENCAO'::character varying])::text[])))
);

ALTER TABLE ONLY public.foto FORCE ROW LEVEL SECURITY;


--
-- Name: fuel_policy; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fuel_policy (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    nome character varying(100) NOT NULL,
    tipo character varying(20) NOT NULL,
    aplicavel_a character varying(20) NOT NULL,
    referencia_id uuid,
    valor_taxa_por_hora numeric(10,2),
    comissionavel boolean DEFAULT false NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    prioridade integer DEFAULT 0 NOT NULL,
    descricao character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT fuel_policy_aplicavel_check CHECK (((aplicavel_a)::text = ANY ((ARRAY['GLOBAL'::character varying, 'MODELO'::character varying, 'JETSKI'::character varying])::text[]))),
    CONSTRAINT fuel_policy_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['INCLUSO'::character varying, 'MEDIDO'::character varying, 'TAXA_FIXA'::character varying])::text[])))
);

ALTER TABLE ONLY public.fuel_policy FORCE ROW LEVEL SECURITY;


--
-- Name: fuel_policy_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.fuel_policy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fuel_policy_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.fuel_policy_id_seq OWNED BY public.fuel_policy.id;


--
-- Name: fuel_price_day; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fuel_price_day (
    id bigint NOT NULL,
    tenant_id uuid NOT NULL,
    data date NOT NULL,
    preco_medio_litro numeric(10,2) NOT NULL,
    total_litros_abastecidos numeric(10,3),
    total_custo numeric(10,2),
    qtd_abastecimentos integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone
);

ALTER TABLE ONLY public.fuel_price_day FORCE ROW LEVEL SECURITY;


--
-- Name: fuel_price_day_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.fuel_price_day_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fuel_price_day_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.fuel_price_day_id_seq OWNED BY public.fuel_price_day.id;


--
-- Name: global_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.global_role (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(50) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: item_opcional; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.item_opcional (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    nome character varying(100) NOT NULL,
    descricao character varying(500),
    preco_base numeric(10,2) DEFAULT 0 NOT NULL,
    ativo boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.item_opcional FORCE ROW LEVEL SECURITY;


--
-- Name: jetski; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.jetski (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    modelo_id uuid NOT NULL,
    serie character varying(50) NOT NULL,
    ano integer,
    horimetro_atual numeric(10,2) DEFAULT 0,
    status character varying(20) DEFAULT 'DISPONIVEL'::character varying,
    ativo boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT jetski_status_check CHECK (((status)::text = ANY ((ARRAY['DISPONIVEL'::character varying, 'LOCADO'::character varying, 'MANUTENCAO'::character varying, 'INDISPONIVEL'::character varying])::text[])))
);

ALTER TABLE ONLY public.jetski FORCE ROW LEVEL SECURITY;


--
-- Name: locacao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.locacao (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    reserva_id uuid,
    jetski_id uuid NOT NULL,
    cliente_id uuid,
    vendedor_id uuid,
    fuel_policy_id bigint,
    data_check_in timestamp with time zone NOT NULL,
    data_check_out timestamp with time zone,
    horimetro_inicio numeric(10,2) NOT NULL,
    horimetro_fim numeric(10,2),
    duracao_prevista integer NOT NULL,
    minutos_usados integer,
    minutos_faturaveis integer,
    valor_base numeric(10,2),
    valor_negociado numeric(10,2),
    valor_total numeric(10,2),
    combustivel_custo numeric(10,2) DEFAULT 0,
    modalidade_preco character varying(20) DEFAULT 'PRECO_FECHADO'::character varying NOT NULL,
    motivo_desconto text,
    status character varying(20) DEFAULT 'EM_CURSO'::character varying NOT NULL,
    observacoes text,
    checklist_saida_json jsonb,
    checklist_entrada_json jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT check_duracao_prevista CHECK ((duracao_prevista > 0)),
    CONSTRAINT check_horimetro_fim CHECK (((horimetro_fim IS NULL) OR (horimetro_fim >= horimetro_inicio))),
    CONSTRAINT chk_locacao_modalidade_preco CHECK (((modalidade_preco)::text = ANY ((ARRAY['PRECO_FECHADO'::character varying, 'DIARIA'::character varying, 'MEIA_DIARIA'::character varying])::text[]))),
    CONSTRAINT locacao_status_check CHECK (((status)::text = ANY ((ARRAY['EM_CURSO'::character varying, 'FINALIZADA'::character varying, 'CANCELADA'::character varying])::text[])))
);

ALTER TABLE ONLY public.locacao FORCE ROW LEVEL SECURITY;


--
-- Name: locacao_item_opcional; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.locacao_item_opcional (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    locacao_id uuid NOT NULL,
    item_opcional_id uuid NOT NULL,
    valor_cobrado numeric(10,2) NOT NULL,
    valor_original numeric(10,2) NOT NULL,
    observacao character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.locacao_item_opcional FORCE ROW LEVEL SECURITY;


--
-- Name: membro; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.membro (
    id integer NOT NULL,
    tenant_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    papeis text[] DEFAULT '{}'::text[] NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: membro_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.membro_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: membro_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.membro_id_seq OWNED BY public.membro.id;


--
-- Name: modelo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.modelo (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    nome character varying(100) NOT NULL,
    fabricante character varying(100),
    potencia_hp integer,
    capacidade_pessoas integer DEFAULT 2,
    preco_base_hora numeric(10,2) DEFAULT 0 NOT NULL,
    tolerancia_min integer DEFAULT 5,
    taxa_hora_extra numeric(10,2) DEFAULT 0,
    inclui_combustivel boolean DEFAULT false,
    caucao numeric(10,2) DEFAULT 0,
    foto_referencia_url text,
    pacotes_json jsonb,
    ativo boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    exibir_no_marketplace boolean DEFAULT true NOT NULL
);

ALTER TABLE ONLY public.modelo FORCE ROW LEVEL SECURITY;


--
-- Name: modelo_midia; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.modelo_midia (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    modelo_id uuid NOT NULL,
    tipo character varying(10) NOT NULL,
    url text NOT NULL,
    thumbnail_url text,
    ordem integer DEFAULT 0 NOT NULL,
    principal boolean DEFAULT false NOT NULL,
    titulo character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT modelo_midia_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['IMAGEM'::character varying, 'VIDEO'::character varying])::text[])))
);

ALTER TABLE ONLY public.modelo_midia FORCE ROW LEVEL SECURITY;


--
-- Name: os_manutencao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.os_manutencao (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    jetski_id uuid NOT NULL,
    tipo character varying(20) NOT NULL,
    descricao_problema text NOT NULL,
    diagnostico text,
    solucao text,
    status character varying(20) DEFAULT 'ABERTA'::character varying,
    prioridade character varying(20) DEFAULT 'MEDIA'::character varying,
    pecas_json jsonb,
    valor_pecas numeric(10,2) DEFAULT 0,
    valor_mao_obra numeric(10,2) DEFAULT 0,
    valor_total numeric(10,2) DEFAULT 0,
    horimetro_abertura numeric(10,2),
    horimetro_conclusao numeric(10,2),
    dt_abertura timestamp with time zone DEFAULT now() NOT NULL,
    dt_prevista_inicio timestamp with time zone,
    dt_inicio_real timestamp with time zone,
    dt_prevista_fim timestamp with time zone,
    dt_conclusao timestamp with time zone,
    mecanico_id uuid,
    observacoes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT os_prioridade_check CHECK (((prioridade)::text = ANY ((ARRAY['baixa'::character varying, 'media'::character varying, 'alta'::character varying, 'urgente'::character varying])::text[]))),
    CONSTRAINT os_status_check CHECK (((status)::text = ANY ((ARRAY['aberta'::character varying, 'em_andamento'::character varying, 'aguardando_pecas'::character varying, 'concluida'::character varying, 'cancelada'::character varying])::text[]))),
    CONSTRAINT os_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['preventiva'::character varying, 'corretiva'::character varying, 'revisao'::character varying])::text[])))
);

ALTER TABLE ONLY public.os_manutencao FORCE ROW LEVEL SECURITY;


--
-- Name: pagamento_vendedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pagamento_vendedor (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    vendedor_id uuid NOT NULL,
    valor_comissoes numeric(12,2) DEFAULT 0 NOT NULL,
    valor_diarias numeric(12,2) DEFAULT 0 NOT NULL,
    valor_total numeric(12,2) NOT NULL,
    chave_pix character varying(100),
    tipo_chave_pix character varying(20),
    referencia_pagamento character varying(100),
    comprovante_url text,
    comprovante_s3_key text,
    qtd_comissoes integer DEFAULT 0 NOT NULL,
    qtd_diarias integer DEFAULT 0 NOT NULL,
    periodo_inicio date,
    periodo_fim date,
    pago_por uuid NOT NULL,
    observacoes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tipo_pagamento character varying(20) DEFAULT 'PIX'::character varying NOT NULL,
    valor_bonus numeric(10,2) DEFAULT 0 NOT NULL,
    qtd_bonus integer DEFAULT 0 NOT NULL,
    vendedor_nome character varying(150) NOT NULL,
    CONSTRAINT pagamento_tipo_pix_check CHECK (((tipo_chave_pix)::text = ANY ((ARRAY['CPF'::character varying, 'CNPJ'::character varying, 'EMAIL'::character varying, 'TELEFONE'::character varying, 'ALEATORIA'::character varying])::text[]))),
    CONSTRAINT pagamento_valor_positivo CHECK ((valor_total > (0)::numeric)),
    CONSTRAINT pagamento_vendedor_tipo_pagamento_check CHECK (((tipo_pagamento)::text = ANY ((ARRAY['PIX'::character varying, 'DINHEIRO'::character varying])::text[])))
);

ALTER TABLE ONLY public.pagamento_vendedor FORCE ROW LEVEL SECURITY;


--
-- Name: plano; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plano (
    id integer NOT NULL,
    nome character varying(50) NOT NULL,
    limites jsonb DEFAULT '{}'::jsonb,
    preco_mensal numeric(10,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: plano_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.plano_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: plano_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.plano_id_seq OWNED BY public.plano.id;


--
-- Name: politica_comissao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.politica_comissao (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    nivel character varying(20) NOT NULL,
    tipo character varying(20) NOT NULL,
    nome character varying(100) NOT NULL,
    descricao character varying(500),
    vendedor_id uuid,
    modelo_id uuid,
    codigo_campanha character varying(50),
    duracao_min_minutos integer,
    duracao_max_minutos integer,
    percentual_comissao numeric(5,2),
    valor_fixo numeric(10,2),
    percentual_extra numeric(5,2),
    vigencia_inicio timestamp with time zone,
    vigencia_fim timestamp with time zone,
    ativa boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid NOT NULL,
    CONSTRAINT politica_comissao_nivel_check CHECK (((nivel)::text = ANY ((ARRAY['CAMPANHA'::character varying, 'MODELO'::character varying, 'DURACAO'::character varying, 'VENDEDOR'::character varying])::text[]))),
    CONSTRAINT politica_comissao_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['PERCENTUAL'::character varying, 'VALOR_FIXO'::character varying, 'ESCALONADO'::character varying])::text[])))
);

ALTER TABLE ONLY public.politica_comissao FORCE ROW LEVEL SECURITY;


--
-- Name: presenca_vendedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.presenca_vendedor (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    vendedor_id uuid NOT NULL,
    dt_referencia date NOT NULL,
    tipo character varying(20) DEFAULT 'INTEGRAL'::character varying NOT NULL,
    valor_diaria numeric(10,2) NOT NULL,
    valor_ajustado numeric(10,2),
    motivo_ajuste character varying(255),
    registrado_por uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    pagamento_id uuid,
    pago_em timestamp with time zone,
    pago_por uuid,
    CONSTRAINT presenca_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['INTEGRAL'::character varying, 'MEIA_DIARIA'::character varying])::text[]))),
    CONSTRAINT presenca_valor_positivo CHECK ((valor_diaria >= (0)::numeric))
);

ALTER TABLE ONLY public.presenca_vendedor FORCE ROW LEVEL SECURITY;


--
-- Name: reserva; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reserva (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    modelo_id uuid NOT NULL,
    jetski_id uuid,
    cliente_id uuid NOT NULL,
    vendedor_id uuid,
    locacao_id uuid,
    data_inicio timestamp with time zone NOT NULL,
    data_fim_prevista timestamp with time zone NOT NULL,
    prioridade character varying(20) DEFAULT 'BAIXA'::character varying NOT NULL,
    sinal_pago boolean DEFAULT false NOT NULL,
    valor_sinal numeric(10,2),
    sinal_pago_em timestamp with time zone,
    expira_em timestamp with time zone,
    status character varying(20) DEFAULT 'PENDENTE'::character varying,
    observacoes text,
    ativo boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT reserva_prioridade_check CHECK (((prioridade)::text = ANY ((ARRAY['ALTA'::character varying, 'BAIXA'::character varying])::text[]))),
    CONSTRAINT reserva_prioridade_sinal CHECK (((sinal_pago = false) OR ((sinal_pago = true) AND ((prioridade)::text = 'ALTA'::text)))),
    CONSTRAINT reserva_sinal_consistency CHECK (((sinal_pago = false) OR ((sinal_pago = true) AND (valor_sinal IS NOT NULL) AND (sinal_pago_em IS NOT NULL)))),
    CONSTRAINT reserva_status_check CHECK (((status)::text = ANY ((ARRAY['PENDENTE'::character varying, 'CONFIRMADA'::character varying, 'CANCELADA'::character varying, 'FINALIZADA'::character varying, 'EXPIRADA'::character varying])::text[])))
);

ALTER TABLE ONLY public.reserva FORCE ROW LEVEL SECURITY;


--
-- Name: reserva_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reserva_config (
    tenant_id uuid NOT NULL,
    grace_period_minutos integer DEFAULT 30 NOT NULL,
    percentual_sinal numeric(5,2) DEFAULT 30.00 NOT NULL,
    fator_overbooking numeric(5,2) DEFAULT 1.0 NOT NULL,
    max_reservas_sem_sinal_por_modelo integer DEFAULT 8 NOT NULL,
    notificar_antes_expiracao boolean DEFAULT true NOT NULL,
    notificar_minutos_antecedencia integer DEFAULT 15 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT fator_overbooking_valid CHECK (((fator_overbooking >= 1.0) AND (fator_overbooking <= 10.0))),
    CONSTRAINT grace_period_positive CHECK ((grace_period_minutos > 0)),
    CONSTRAINT max_reservas_positive CHECK ((max_reservas_sem_sinal_por_modelo > 0)),
    CONSTRAINT percentual_sinal_valid CHECK (((percentual_sinal >= (0)::numeric) AND (percentual_sinal <= (100)::numeric)))
);


--
-- Name: tenant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    slug character varying(50) NOT NULL,
    razao_social character varying(200) NOT NULL,
    cnpj character varying(18),
    timezone character varying(50) DEFAULT 'America/Sao_Paulo'::character varying,
    moeda character varying(3) DEFAULT 'BRL'::character varying,
    status character varying(20) DEFAULT 'ATIVO'::character varying,
    branding jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    whatsapp character varying(20),
    cidade character varying(100),
    uf character varying(2),
    exibir_no_marketplace boolean DEFAULT true NOT NULL,
    prioridade_marketplace integer DEFAULT 0 NOT NULL,
    comissao_config jsonb DEFAULT '{"bonusAtivo": true, "bonusValor": 500.00, "bonusMetaVendas": 50, "percentualPadrao": 10.0, "percentualAbaixoBase": 5.0}'::jsonb
);


--
-- Name: tenant_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_access (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    roles text[] DEFAULT '{}'::text[],
    is_default boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tenant_signup; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_signup (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    email character varying(255) NOT NULL,
    nome character varying(200) NOT NULL,
    token character varying(255) NOT NULL,
    temporary_password character varying(255),
    expires_at timestamp with time zone NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    activated_at timestamp with time zone,
    CONSTRAINT tenant_signup_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVATED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(255) NOT NULL,
    nome character varying(200) NOT NULL,
    ativo boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    email_verified boolean DEFAULT false NOT NULL,
    email_verified_at timestamp with time zone
);


--
-- Name: usuario_global_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario_global_roles (
    usuario_id uuid NOT NULL,
    roles text[] NOT NULL,
    unrestricted_access boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT roles_not_empty CHECK ((array_length(roles, 1) > 0))
);


--
-- Name: usuario_identity_provider; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario_identity_provider (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    provider character varying(50) NOT NULL,
    provider_user_id character varying(255) NOT NULL,
    linked_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: vendedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vendedor (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    nome character varying(200) NOT NULL,
    documento character varying(20),
    tipo character varying(20) DEFAULT 'INTERNO'::character varying NOT NULL,
    regra_comissao_json jsonb,
    ativo boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    email character varying(255),
    telefone character varying(20),
    diaria_base numeric(10,2) DEFAULT 0,
    chave_pix character varying(100),
    tipo_chave_pix character varying(20),
    CONSTRAINT vendedor_pix_consistency CHECK ((((chave_pix IS NULL) AND (tipo_chave_pix IS NULL)) OR ((chave_pix IS NOT NULL) AND (tipo_chave_pix IS NOT NULL)))),
    CONSTRAINT vendedor_tipo_chave_pix_check CHECK (((tipo_chave_pix IS NULL) OR ((tipo_chave_pix)::text = ANY ((ARRAY['CPF'::character varying, 'CNPJ'::character varying, 'EMAIL'::character varying, 'TELEFONE'::character varying, 'ALEATORIA'::character varying])::text[])))),
    CONSTRAINT vendedor_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['INTERNO'::character varying, 'PARCEIRO'::character varying])::text[])))
);

ALTER TABLE ONLY public.vendedor FORCE ROW LEVEL SECURITY;


--
-- Name: abastecimento id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abastecimento ALTER COLUMN id SET DEFAULT nextval('public.abastecimento_id_seq'::regclass);


--
-- Name: assinatura id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assinatura ALTER COLUMN id SET DEFAULT nextval('public.assinatura_id_seq'::regclass);


--
-- Name: fuel_policy id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fuel_policy ALTER COLUMN id SET DEFAULT nextval('public.fuel_policy_id_seq'::regclass);


--
-- Name: fuel_price_day id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fuel_price_day ALTER COLUMN id SET DEFAULT nextval('public.fuel_price_day_id_seq'::regclass);


--
-- Name: membro id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.membro ALTER COLUMN id SET DEFAULT nextval('public.membro_id_seq'::regclass);


--
-- Name: plano id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plano ALTER COLUMN id SET DEFAULT nextval('public.plano_id_seq'::regclass);


--
-- Name: abastecimento abastecimento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abastecimento
    ADD CONSTRAINT abastecimento_pkey PRIMARY KEY (id);


--
-- Name: assinatura assinatura_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assinatura
    ADD CONSTRAINT assinatura_pkey PRIMARY KEY (id);


--
-- Name: auditoria auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria
    ADD CONSTRAINT auditoria_pkey PRIMARY KEY (id);


--
-- Name: bonus_vendedor bonus_vendedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bonus_vendedor
    ADD CONSTRAINT bonus_vendedor_pkey PRIMARY KEY (id);


--
-- Name: cliente cliente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cliente
    ADD CONSTRAINT cliente_pkey PRIMARY KEY (id);


--
-- Name: comissao comissao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comissao
    ADD CONSTRAINT comissao_pkey PRIMARY KEY (id);


--
-- Name: convite convite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.convite
    ADD CONSTRAINT convite_pkey PRIMARY KEY (id);


--
-- Name: convite convite_tenant_id_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.convite
    ADD CONSTRAINT convite_tenant_id_email_key UNIQUE (tenant_id, email);


--
-- Name: convite convite_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.convite
    ADD CONSTRAINT convite_token_key UNIQUE (token);


--
-- Name: despesa_manutencao despesa_manutencao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.despesa_manutencao
    ADD CONSTRAINT despesa_manutencao_pkey PRIMARY KEY (id);


--
-- Name: despesa_operacional despesa_operacional_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.despesa_operacional
    ADD CONSTRAINT despesa_operacional_pkey PRIMARY KEY (id);


--
-- Name: fechamento_diario fechamento_diario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fechamento_diario
    ADD CONSTRAINT fechamento_diario_pkey PRIMARY KEY (id);


--
-- Name: fechamento_diario fechamento_diario_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fechamento_diario
    ADD CONSTRAINT fechamento_diario_unique UNIQUE (tenant_id, dt_referencia);


--
-- Name: fechamento_mensal fechamento_mensal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fechamento_mensal
    ADD CONSTRAINT fechamento_mensal_pkey PRIMARY KEY (id);


--
-- Name: fechamento_mensal fechamento_mensal_tenant_id_ano_mes_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fechamento_mensal
    ADD CONSTRAINT fechamento_mensal_tenant_id_ano_mes_key UNIQUE (tenant_id, ano, mes);


--
-- Name: foto foto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.foto
    ADD CONSTRAINT foto_pkey PRIMARY KEY (id);


--
-- Name: fuel_policy fuel_policy_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fuel_policy
    ADD CONSTRAINT fuel_policy_pkey PRIMARY KEY (id);


--
-- Name: fuel_price_day fuel_price_day_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fuel_price_day
    ADD CONSTRAINT fuel_price_day_pkey PRIMARY KEY (id);


--
-- Name: fuel_price_day fuel_price_day_tenant_id_data_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fuel_price_day
    ADD CONSTRAINT fuel_price_day_tenant_id_data_key UNIQUE (tenant_id, data);


--
-- Name: global_role global_role_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.global_role
    ADD CONSTRAINT global_role_name_key UNIQUE (name);


--
-- Name: global_role global_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.global_role
    ADD CONSTRAINT global_role_pkey PRIMARY KEY (id);


--
-- Name: item_opcional item_opcional_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_opcional
    ADD CONSTRAINT item_opcional_pkey PRIMARY KEY (id);


--
-- Name: item_opcional item_opcional_tenant_id_nome_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_opcional
    ADD CONSTRAINT item_opcional_tenant_id_nome_key UNIQUE (tenant_id, nome);


--
-- Name: jetski jetski_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jetski
    ADD CONSTRAINT jetski_pkey PRIMARY KEY (id);


--
-- Name: jetski jetski_tenant_id_serie_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jetski
    ADD CONSTRAINT jetski_tenant_id_serie_key UNIQUE (tenant_id, serie);


--
-- Name: locacao_item_opcional locacao_item_opcional_locacao_id_item_opcional_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao_item_opcional
    ADD CONSTRAINT locacao_item_opcional_locacao_id_item_opcional_id_key UNIQUE (locacao_id, item_opcional_id);


--
-- Name: locacao_item_opcional locacao_item_opcional_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao_item_opcional
    ADD CONSTRAINT locacao_item_opcional_pkey PRIMARY KEY (id);


--
-- Name: locacao locacao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao
    ADD CONSTRAINT locacao_pkey PRIMARY KEY (id);


--
-- Name: membro membro_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.membro
    ADD CONSTRAINT membro_pkey PRIMARY KEY (id);


--
-- Name: membro membro_tenant_id_usuario_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.membro
    ADD CONSTRAINT membro_tenant_id_usuario_id_key UNIQUE (tenant_id, usuario_id);


--
-- Name: modelo_midia modelo_midia_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modelo_midia
    ADD CONSTRAINT modelo_midia_pkey PRIMARY KEY (id);


--
-- Name: modelo modelo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modelo
    ADD CONSTRAINT modelo_pkey PRIMARY KEY (id);


--
-- Name: modelo modelo_tenant_id_nome_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modelo
    ADD CONSTRAINT modelo_tenant_id_nome_key UNIQUE (tenant_id, nome);


--
-- Name: plano nome_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plano
    ADD CONSTRAINT nome_unique UNIQUE (nome);


--
-- Name: os_manutencao os_manutencao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.os_manutencao
    ADD CONSTRAINT os_manutencao_pkey PRIMARY KEY (id);


--
-- Name: pagamento_vendedor pagamento_vendedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pagamento_vendedor
    ADD CONSTRAINT pagamento_vendedor_pkey PRIMARY KEY (id);


--
-- Name: plano plano_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plano
    ADD CONSTRAINT plano_pkey PRIMARY KEY (id);


--
-- Name: politica_comissao politica_comissao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.politica_comissao
    ADD CONSTRAINT politica_comissao_pkey PRIMARY KEY (id);


--
-- Name: presenca_vendedor presenca_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.presenca_vendedor
    ADD CONSTRAINT presenca_unique UNIQUE (tenant_id, vendedor_id, dt_referencia);


--
-- Name: presenca_vendedor presenca_vendedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.presenca_vendedor
    ADD CONSTRAINT presenca_vendedor_pkey PRIMARY KEY (id);


--
-- Name: reserva_config reserva_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reserva_config
    ADD CONSTRAINT reserva_config_pkey PRIMARY KEY (tenant_id);


--
-- Name: reserva reserva_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reserva
    ADD CONSTRAINT reserva_pkey PRIMARY KEY (id);


--
-- Name: tenant_access tenant_access_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_access
    ADD CONSTRAINT tenant_access_pkey PRIMARY KEY (id);


--
-- Name: tenant_access tenant_access_usuario_id_tenant_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_access
    ADD CONSTRAINT tenant_access_usuario_id_tenant_id_key UNIQUE (usuario_id, tenant_id);


--
-- Name: tenant tenant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_pkey PRIMARY KEY (id);


--
-- Name: tenant_signup tenant_signup_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_signup
    ADD CONSTRAINT tenant_signup_pkey PRIMARY KEY (id);


--
-- Name: tenant_signup tenant_signup_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_signup
    ADD CONSTRAINT tenant_signup_token_key UNIQUE (token);


--
-- Name: tenant tenant_slug_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_slug_key UNIQUE (slug);


--
-- Name: usuario usuario_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_email_key UNIQUE (email);


--
-- Name: usuario_global_roles usuario_global_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_global_roles
    ADD CONSTRAINT usuario_global_roles_pkey PRIMARY KEY (usuario_id);


--
-- Name: usuario_identity_provider usuario_identity_provider_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_identity_provider
    ADD CONSTRAINT usuario_identity_provider_pkey PRIMARY KEY (id);


--
-- Name: usuario_identity_provider usuario_identity_provider_provider_provider_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_identity_provider
    ADD CONSTRAINT usuario_identity_provider_provider_provider_user_id_key UNIQUE (provider, provider_user_id);


--
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id);


--
-- Name: vendedor vendedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendedor
    ADD CONSTRAINT vendedor_pkey PRIMARY KEY (id);


--
-- Name: idx_abastecimento_locacao; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_abastecimento_locacao ON public.abastecimento USING btree (locacao_id);


--
-- Name: idx_abastecimento_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_abastecimento_tenant ON public.abastecimento USING btree (tenant_id);


--
-- Name: idx_assinatura_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assinatura_tenant ON public.assinatura USING btree (tenant_id);


--
-- Name: idx_auditoria_entidade; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auditoria_entidade ON public.auditoria USING btree (entidade, entidade_id);


--
-- Name: idx_auditoria_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auditoria_tenant ON public.auditoria USING btree (tenant_id);


--
-- Name: idx_auditoria_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auditoria_usuario ON public.auditoria USING btree (usuario_id);


--
-- Name: idx_bonus_vendedor_pagamento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bonus_vendedor_pagamento ON public.bonus_vendedor USING btree (pagamento_id) WHERE (pagamento_id IS NOT NULL);


--
-- Name: idx_bonus_vendedor_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bonus_vendedor_status ON public.bonus_vendedor USING btree (tenant_id, status);


--
-- Name: idx_bonus_vendedor_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bonus_vendedor_tenant ON public.bonus_vendedor USING btree (tenant_id);


--
-- Name: idx_bonus_vendedor_vendedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bonus_vendedor_vendedor ON public.bonus_vendedor USING btree (tenant_id, vendedor_id);


--
-- Name: idx_cliente_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cliente_tenant ON public.cliente USING btree (tenant_id);


--
-- Name: idx_comissao_tenant_data; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comissao_tenant_data ON public.comissao USING btree (tenant_id, data_locacao);


--
-- Name: idx_comissao_tenant_locacao; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comissao_tenant_locacao ON public.comissao USING btree (tenant_id, locacao_id);


--
-- Name: idx_comissao_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comissao_tenant_status ON public.comissao USING btree (tenant_id, status);


--
-- Name: idx_comissao_tenant_vendedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comissao_tenant_vendedor ON public.comissao USING btree (tenant_id, vendedor_id);


--
-- Name: idx_comissao_venda_acima_base; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comissao_venda_acima_base ON public.comissao USING btree (tenant_id, vendedor_id, venda_acima_preco_base) WHERE (venda_acima_preco_base = true);


--
-- Name: idx_comissao_vendedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comissao_vendedor ON public.comissao USING btree (tenant_id, vendedor_id);


--
-- Name: idx_convite_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_convite_tenant ON public.convite USING btree (tenant_id);


--
-- Name: idx_convite_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_convite_token ON public.convite USING btree (token);


--
-- Name: idx_despesa_manutencao_os; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_despesa_manutencao_os ON public.despesa_manutencao USING btree (os_manutencao_id);


--
-- Name: idx_despesa_manutencao_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_despesa_manutencao_status ON public.despesa_manutencao USING btree (tenant_id, status);


--
-- Name: idx_despesa_manutencao_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_despesa_manutencao_tenant ON public.despesa_manutencao USING btree (tenant_id);


--
-- Name: idx_despesa_manutencao_vencimento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_despesa_manutencao_vencimento ON public.despesa_manutencao USING btree (tenant_id, dt_vencimento);


--
-- Name: idx_despesa_operacional_categoria; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_despesa_operacional_categoria ON public.despesa_operacional USING btree (tenant_id, categoria);


--
-- Name: idx_despesa_operacional_data; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_despesa_operacional_data ON public.despesa_operacional USING btree (tenant_id, dt_referencia);


--
-- Name: idx_despesa_operacional_responsavel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_despesa_operacional_responsavel ON public.despesa_operacional USING btree (tenant_id, responsavel_id);


--
-- Name: idx_despesa_operacional_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_despesa_operacional_status ON public.despesa_operacional USING btree (tenant_id, status);


--
-- Name: idx_despesa_operacional_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_despesa_operacional_tenant ON public.despesa_operacional USING btree (tenant_id);


--
-- Name: idx_fechamento_diario_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fechamento_diario_tenant ON public.fechamento_diario USING btree (tenant_id);


--
-- Name: idx_fechamento_diario_tenant_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fechamento_diario_tenant_hash ON public.fechamento_diario USING btree (tenant_id, valores_hash);


--
-- Name: idx_fechamento_mensal_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fechamento_mensal_tenant ON public.fechamento_mensal USING btree (tenant_id);


--
-- Name: idx_fechamento_mensal_tenant_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fechamento_mensal_tenant_hash ON public.fechamento_mensal USING btree (tenant_id, valores_hash);


--
-- Name: idx_foto_jetski; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_foto_jetski ON public.foto USING btree (tenant_id, jetski_id);


--
-- Name: idx_foto_locacao; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_foto_locacao ON public.foto USING btree (tenant_id, locacao_id);


--
-- Name: idx_foto_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_foto_tenant ON public.foto USING btree (tenant_id);


--
-- Name: idx_fuel_policy_aplicacao; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fuel_policy_aplicacao ON public.fuel_policy USING btree (tenant_id, aplicavel_a, referencia_id);


--
-- Name: idx_fuel_policy_ativo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fuel_policy_ativo ON public.fuel_policy USING btree (tenant_id, ativo);


--
-- Name: idx_fuel_policy_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fuel_policy_tenant ON public.fuel_policy USING btree (tenant_id);


--
-- Name: idx_fuel_price_day_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fuel_price_day_tenant ON public.fuel_price_day USING btree (tenant_id);


--
-- Name: idx_global_roles_unrestricted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_global_roles_unrestricted ON public.usuario_global_roles USING btree (unrestricted_access) WHERE (unrestricted_access = true);


--
-- Name: idx_item_opcional_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_opcional_tenant ON public.item_opcional USING btree (tenant_id);


--
-- Name: idx_jetski_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_jetski_tenant ON public.jetski USING btree (tenant_id);


--
-- Name: idx_jetski_tenant_modelo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_jetski_tenant_modelo ON public.jetski USING btree (tenant_id, modelo_id);


--
-- Name: idx_jetski_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_jetski_tenant_status ON public.jetski USING btree (tenant_id, status);


--
-- Name: idx_locacao_data_check_in; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_locacao_data_check_in ON public.locacao USING btree (tenant_id, data_check_in DESC);


--
-- Name: idx_locacao_fuel_policy; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_locacao_fuel_policy ON public.locacao USING btree (tenant_id, fuel_policy_id);


--
-- Name: idx_locacao_item_opcional_locacao; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_locacao_item_opcional_locacao ON public.locacao_item_opcional USING btree (locacao_id);


--
-- Name: idx_locacao_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_locacao_tenant ON public.locacao USING btree (tenant_id);


--
-- Name: idx_locacao_tenant_cliente; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_locacao_tenant_cliente ON public.locacao USING btree (tenant_id, cliente_id);


--
-- Name: idx_locacao_tenant_jetski; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_locacao_tenant_jetski ON public.locacao USING btree (tenant_id, jetski_id);


--
-- Name: idx_locacao_tenant_reserva; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_locacao_tenant_reserva ON public.locacao USING btree (tenant_id, reserva_id) WHERE (reserva_id IS NOT NULL);


--
-- Name: idx_locacao_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_locacao_tenant_status ON public.locacao USING btree (tenant_id, status);


--
-- Name: idx_membro_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_membro_tenant ON public.membro USING btree (tenant_id);


--
-- Name: idx_membro_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_membro_usuario ON public.membro USING btree (usuario_id);


--
-- Name: idx_modelo_marketplace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modelo_marketplace ON public.modelo USING btree (tenant_id, ativo, exibir_no_marketplace) WHERE ((ativo = true) AND (exibir_no_marketplace = true));


--
-- Name: idx_modelo_midia_ordem; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modelo_midia_ordem ON public.modelo_midia USING btree (modelo_id, ordem);


--
-- Name: idx_modelo_midia_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modelo_midia_principal ON public.modelo_midia USING btree (modelo_id, principal) WHERE (principal = true);


--
-- Name: idx_modelo_midia_tenant_modelo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modelo_midia_tenant_modelo ON public.modelo_midia USING btree (tenant_id, modelo_id);


--
-- Name: idx_modelo_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modelo_tenant ON public.modelo USING btree (tenant_id);


--
-- Name: idx_os_manutencao_jetski; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_os_manutencao_jetski ON public.os_manutencao USING btree (tenant_id, jetski_id);


--
-- Name: idx_os_manutencao_mecanico; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_os_manutencao_mecanico ON public.os_manutencao USING btree (tenant_id, mecanico_id);


--
-- Name: idx_os_manutencao_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_os_manutencao_tenant ON public.os_manutencao USING btree (tenant_id);


--
-- Name: idx_pagamento_vendedor_data; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pagamento_vendedor_data ON public.pagamento_vendedor USING btree (tenant_id, created_at DESC);


--
-- Name: idx_pagamento_vendedor_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pagamento_vendedor_tenant ON public.pagamento_vendedor USING btree (tenant_id);


--
-- Name: idx_pagamento_vendedor_vendedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pagamento_vendedor_vendedor ON public.pagamento_vendedor USING btree (tenant_id, vendedor_id);


--
-- Name: idx_politica_tenant_campanha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_politica_tenant_campanha ON public.politica_comissao USING btree (tenant_id, codigo_campanha);


--
-- Name: idx_politica_tenant_modelo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_politica_tenant_modelo ON public.politica_comissao USING btree (tenant_id, modelo_id);


--
-- Name: idx_politica_tenant_nivel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_politica_tenant_nivel ON public.politica_comissao USING btree (tenant_id, nivel);


--
-- Name: idx_politica_tenant_vendedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_politica_tenant_vendedor ON public.politica_comissao USING btree (tenant_id, vendedor_id);


--
-- Name: idx_presenca_vendedor_data; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_presenca_vendedor_data ON public.presenca_vendedor USING btree (tenant_id, dt_referencia);


--
-- Name: idx_presenca_vendedor_nao_pagas; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_presenca_vendedor_nao_pagas ON public.presenca_vendedor USING btree (tenant_id, vendedor_id) WHERE (pagamento_id IS NULL);


--
-- Name: idx_presenca_vendedor_pagamento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_presenca_vendedor_pagamento ON public.presenca_vendedor USING btree (pagamento_id);


--
-- Name: idx_presenca_vendedor_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_presenca_vendedor_tenant ON public.presenca_vendedor USING btree (tenant_id);


--
-- Name: idx_presenca_vendedor_vendedor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_presenca_vendedor_vendedor ON public.presenca_vendedor USING btree (tenant_id, vendedor_id);


--
-- Name: idx_reserva_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reserva_tenant ON public.reserva USING btree (tenant_id);


--
-- Name: idx_reserva_tenant_cliente; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reserva_tenant_cliente ON public.reserva USING btree (tenant_id, cliente_id);


--
-- Name: idx_reserva_tenant_data; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reserva_tenant_data ON public.reserva USING btree (tenant_id, data_inicio, data_fim_prevista);


--
-- Name: idx_reserva_tenant_modelo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reserva_tenant_modelo ON public.reserva USING btree (tenant_id, modelo_id);


--
-- Name: idx_reserva_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reserva_tenant_status ON public.reserva USING btree (tenant_id, status);


--
-- Name: idx_tenant_access_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_access_tenant ON public.tenant_access USING btree (tenant_id);


--
-- Name: idx_tenant_access_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_access_usuario ON public.tenant_access USING btree (usuario_id);


--
-- Name: idx_tenant_marketplace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_marketplace ON public.tenant USING btree (status, exibir_no_marketplace, prioridade_marketplace DESC) WHERE (((status)::text = 'ATIVO'::text) AND (exibir_no_marketplace = true));


--
-- Name: idx_tenant_signup_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_signup_email ON public.tenant_signup USING btree (email);


--
-- Name: idx_tenant_signup_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_signup_status ON public.tenant_signup USING btree (status);


--
-- Name: idx_tenant_signup_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_signup_tenant ON public.tenant_signup USING btree (tenant_id);


--
-- Name: idx_tenant_signup_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_signup_token ON public.tenant_signup USING btree (token);


--
-- Name: idx_vendedor_ativo_diaria; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vendedor_ativo_diaria ON public.vendedor USING btree (tenant_id, ativo) WHERE (ativo = true);


--
-- Name: idx_vendedor_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vendedor_email ON public.vendedor USING btree (email) WHERE (email IS NOT NULL);


--
-- Name: idx_vendedor_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vendedor_tenant ON public.vendedor USING btree (tenant_id);


--
-- Name: abastecimento update_abastecimento_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_abastecimento_updated_at BEFORE UPDATE ON public.abastecimento FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: assinatura update_assinatura_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_assinatura_updated_at BEFORE UPDATE ON public.assinatura FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: cliente update_cliente_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_cliente_updated_at BEFORE UPDATE ON public.cliente FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: comissao update_comissao_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_comissao_updated_at BEFORE UPDATE ON public.comissao FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: convite update_convite_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_convite_updated_at BEFORE UPDATE ON public.convite FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: despesa_operacional update_despesa_operacional_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_despesa_operacional_updated_at BEFORE UPDATE ON public.despesa_operacional FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: fechamento_diario update_fechamento_diario_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_fechamento_diario_updated_at BEFORE UPDATE ON public.fechamento_diario FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: fechamento_mensal update_fechamento_mensal_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_fechamento_mensal_updated_at BEFORE UPDATE ON public.fechamento_mensal FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: fuel_policy update_fuel_policy_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_fuel_policy_updated_at BEFORE UPDATE ON public.fuel_policy FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: global_role update_global_role_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_global_role_updated_at BEFORE UPDATE ON public.global_role FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: item_opcional update_item_opcional_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_item_opcional_updated_at BEFORE UPDATE ON public.item_opcional FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: jetski update_jetski_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_jetski_updated_at BEFORE UPDATE ON public.jetski FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: locacao update_locacao_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_locacao_updated_at BEFORE UPDATE ON public.locacao FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: membro update_membro_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_membro_updated_at BEFORE UPDATE ON public.membro FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: modelo update_modelo_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_modelo_updated_at BEFORE UPDATE ON public.modelo FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: os_manutencao update_os_manutencao_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_os_manutencao_updated_at BEFORE UPDATE ON public.os_manutencao FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: pagamento_vendedor update_pagamento_vendedor_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_pagamento_vendedor_updated_at BEFORE UPDATE ON public.pagamento_vendedor FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: plano update_plano_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_plano_updated_at BEFORE UPDATE ON public.plano FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: politica_comissao update_politica_comissao_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_politica_comissao_updated_at BEFORE UPDATE ON public.politica_comissao FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: presenca_vendedor update_presenca_vendedor_updated_at_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_presenca_vendedor_updated_at_trigger BEFORE UPDATE ON public.presenca_vendedor FOR EACH ROW EXECUTE FUNCTION public.update_presenca_vendedor_updated_at();


--
-- Name: reserva update_reserva_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_reserva_updated_at BEFORE UPDATE ON public.reserva FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: tenant_access update_tenant_access_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_tenant_access_updated_at BEFORE UPDATE ON public.tenant_access FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: tenant_signup update_tenant_signup_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_tenant_signup_updated_at BEFORE UPDATE ON public.tenant_signup FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: tenant update_tenant_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_tenant_updated_at BEFORE UPDATE ON public.tenant FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: usuario_global_roles update_usuario_global_roles_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_usuario_global_roles_updated_at BEFORE UPDATE ON public.usuario_global_roles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: usuario_identity_provider update_usuario_identity_provider_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_usuario_identity_provider_updated_at BEFORE UPDATE ON public.usuario_identity_provider FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: usuario update_usuario_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_usuario_updated_at BEFORE UPDATE ON public.usuario FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: vendedor update_vendedor_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_vendedor_updated_at BEFORE UPDATE ON public.vendedor FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: abastecimento abastecimento_foto_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abastecimento
    ADD CONSTRAINT abastecimento_foto_id_fkey FOREIGN KEY (foto_id) REFERENCES public.foto(id);


--
-- Name: abastecimento abastecimento_jetski_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abastecimento
    ADD CONSTRAINT abastecimento_jetski_id_fkey FOREIGN KEY (jetski_id) REFERENCES public.jetski(id);


--
-- Name: abastecimento abastecimento_locacao_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abastecimento
    ADD CONSTRAINT abastecimento_locacao_id_fkey FOREIGN KEY (locacao_id) REFERENCES public.locacao(id);


--
-- Name: abastecimento abastecimento_responsavel_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abastecimento
    ADD CONSTRAINT abastecimento_responsavel_id_fkey FOREIGN KEY (responsavel_id) REFERENCES public.usuario(id);


--
-- Name: abastecimento abastecimento_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abastecimento
    ADD CONSTRAINT abastecimento_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: assinatura assinatura_plano_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assinatura
    ADD CONSTRAINT assinatura_plano_id_fkey FOREIGN KEY (plano_id) REFERENCES public.plano(id);


--
-- Name: assinatura assinatura_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assinatura
    ADD CONSTRAINT assinatura_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: auditoria auditoria_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria
    ADD CONSTRAINT auditoria_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE SET NULL;


--
-- Name: auditoria auditoria_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria
    ADD CONSTRAINT auditoria_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE SET NULL;


--
-- Name: bonus_vendedor bonus_vendedor_pagamento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bonus_vendedor
    ADD CONSTRAINT bonus_vendedor_pagamento_id_fkey FOREIGN KEY (pagamento_id) REFERENCES public.pagamento_vendedor(id);


--
-- Name: bonus_vendedor bonus_vendedor_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bonus_vendedor
    ADD CONSTRAINT bonus_vendedor_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: bonus_vendedor bonus_vendedor_vendedor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bonus_vendedor
    ADD CONSTRAINT bonus_vendedor_vendedor_id_fkey FOREIGN KEY (vendedor_id) REFERENCES public.vendedor(id) ON DELETE CASCADE;


--
-- Name: cliente cliente_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cliente
    ADD CONSTRAINT cliente_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: comissao comissao_locacao_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comissao
    ADD CONSTRAINT comissao_locacao_id_fkey FOREIGN KEY (locacao_id) REFERENCES public.locacao(id);


--
-- Name: comissao comissao_politica_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comissao
    ADD CONSTRAINT comissao_politica_id_fkey FOREIGN KEY (politica_id) REFERENCES public.politica_comissao(id);


--
-- Name: comissao comissao_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comissao
    ADD CONSTRAINT comissao_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: comissao comissao_vendedor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comissao
    ADD CONSTRAINT comissao_vendedor_id_fkey FOREIGN KEY (vendedor_id) REFERENCES public.vendedor(id);


--
-- Name: convite convite_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.convite
    ADD CONSTRAINT convite_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.usuario(id);


--
-- Name: convite convite_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.convite
    ADD CONSTRAINT convite_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: convite convite_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.convite
    ADD CONSTRAINT convite_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id);


--
-- Name: despesa_manutencao despesa_manutencao_aprovado_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.despesa_manutencao
    ADD CONSTRAINT despesa_manutencao_aprovado_por_fkey FOREIGN KEY (aprovado_por) REFERENCES public.membro(id);


--
-- Name: despesa_manutencao despesa_manutencao_os_manutencao_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.despesa_manutencao
    ADD CONSTRAINT despesa_manutencao_os_manutencao_id_fkey FOREIGN KEY (os_manutencao_id) REFERENCES public.os_manutencao(id);


--
-- Name: despesa_manutencao despesa_manutencao_pago_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.despesa_manutencao
    ADD CONSTRAINT despesa_manutencao_pago_por_fkey FOREIGN KEY (pago_por) REFERENCES public.membro(id);


--
-- Name: despesa_manutencao despesa_manutencao_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.despesa_manutencao
    ADD CONSTRAINT despesa_manutencao_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: despesa_operacional despesa_operacional_aprovado_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.despesa_operacional
    ADD CONSTRAINT despesa_operacional_aprovado_por_fkey FOREIGN KEY (aprovado_por) REFERENCES public.usuario(id);


--
-- Name: despesa_operacional despesa_operacional_pago_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.despesa_operacional
    ADD CONSTRAINT despesa_operacional_pago_por_fkey FOREIGN KEY (pago_por) REFERENCES public.usuario(id);


--
-- Name: despesa_operacional despesa_operacional_responsavel_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.despesa_operacional
    ADD CONSTRAINT despesa_operacional_responsavel_id_fkey FOREIGN KEY (responsavel_id) REFERENCES public.usuario(id);


--
-- Name: despesa_operacional despesa_operacional_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.despesa_operacional
    ADD CONSTRAINT despesa_operacional_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: fechamento_diario fechamento_diario_fechado_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fechamento_diario
    ADD CONSTRAINT fechamento_diario_fechado_por_fkey FOREIGN KEY (fechado_por) REFERENCES public.usuario(id);


--
-- Name: fechamento_diario fechamento_diario_operador_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fechamento_diario
    ADD CONSTRAINT fechamento_diario_operador_id_fkey FOREIGN KEY (operador_id) REFERENCES public.usuario(id);


--
-- Name: fechamento_diario fechamento_diario_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fechamento_diario
    ADD CONSTRAINT fechamento_diario_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: fechamento_mensal fechamento_mensal_fechado_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fechamento_mensal
    ADD CONSTRAINT fechamento_mensal_fechado_por_fkey FOREIGN KEY (fechado_por) REFERENCES public.usuario(id);


--
-- Name: fechamento_mensal fechamento_mensal_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fechamento_mensal
    ADD CONSTRAINT fechamento_mensal_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: foto foto_jetski_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.foto
    ADD CONSTRAINT foto_jetski_id_fkey FOREIGN KEY (jetski_id) REFERENCES public.jetski(id);


--
-- Name: foto foto_locacao_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.foto
    ADD CONSTRAINT foto_locacao_id_fkey FOREIGN KEY (locacao_id) REFERENCES public.locacao(id) ON DELETE CASCADE;


--
-- Name: foto foto_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.foto
    ADD CONSTRAINT foto_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: fuel_policy fuel_policy_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fuel_policy
    ADD CONSTRAINT fuel_policy_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: fuel_price_day fuel_price_day_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fuel_price_day
    ADD CONSTRAINT fuel_price_day_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: item_opcional item_opcional_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_opcional
    ADD CONSTRAINT item_opcional_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: jetski jetski_modelo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jetski
    ADD CONSTRAINT jetski_modelo_id_fkey FOREIGN KEY (modelo_id) REFERENCES public.modelo(id);


--
-- Name: jetski jetski_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jetski
    ADD CONSTRAINT jetski_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: locacao locacao_cliente_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao
    ADD CONSTRAINT locacao_cliente_id_fkey FOREIGN KEY (cliente_id) REFERENCES public.cliente(id);


--
-- Name: locacao locacao_fuel_policy_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao
    ADD CONSTRAINT locacao_fuel_policy_id_fkey FOREIGN KEY (fuel_policy_id) REFERENCES public.fuel_policy(id);


--
-- Name: locacao_item_opcional locacao_item_opcional_item_opcional_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao_item_opcional
    ADD CONSTRAINT locacao_item_opcional_item_opcional_id_fkey FOREIGN KEY (item_opcional_id) REFERENCES public.item_opcional(id);


--
-- Name: locacao_item_opcional locacao_item_opcional_locacao_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao_item_opcional
    ADD CONSTRAINT locacao_item_opcional_locacao_id_fkey FOREIGN KEY (locacao_id) REFERENCES public.locacao(id) ON DELETE CASCADE;


--
-- Name: locacao_item_opcional locacao_item_opcional_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao_item_opcional
    ADD CONSTRAINT locacao_item_opcional_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: locacao locacao_jetski_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao
    ADD CONSTRAINT locacao_jetski_id_fkey FOREIGN KEY (jetski_id) REFERENCES public.jetski(id);


--
-- Name: locacao locacao_reserva_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao
    ADD CONSTRAINT locacao_reserva_id_fkey FOREIGN KEY (reserva_id) REFERENCES public.reserva(id);


--
-- Name: locacao locacao_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao
    ADD CONSTRAINT locacao_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: locacao locacao_vendedor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locacao
    ADD CONSTRAINT locacao_vendedor_id_fkey FOREIGN KEY (vendedor_id) REFERENCES public.vendedor(id);


--
-- Name: membro membro_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.membro
    ADD CONSTRAINT membro_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: membro membro_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.membro
    ADD CONSTRAINT membro_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- Name: modelo_midia modelo_midia_modelo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modelo_midia
    ADD CONSTRAINT modelo_midia_modelo_id_fkey FOREIGN KEY (modelo_id) REFERENCES public.modelo(id) ON DELETE CASCADE;


--
-- Name: modelo_midia modelo_midia_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modelo_midia
    ADD CONSTRAINT modelo_midia_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: modelo modelo_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modelo
    ADD CONSTRAINT modelo_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: os_manutencao os_manutencao_jetski_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.os_manutencao
    ADD CONSTRAINT os_manutencao_jetski_id_fkey FOREIGN KEY (jetski_id) REFERENCES public.jetski(id);


--
-- Name: os_manutencao os_manutencao_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.os_manutencao
    ADD CONSTRAINT os_manutencao_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: pagamento_vendedor pagamento_vendedor_pago_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pagamento_vendedor
    ADD CONSTRAINT pagamento_vendedor_pago_por_fkey FOREIGN KEY (pago_por) REFERENCES public.usuario(id);


--
-- Name: pagamento_vendedor pagamento_vendedor_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pagamento_vendedor
    ADD CONSTRAINT pagamento_vendedor_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: pagamento_vendedor pagamento_vendedor_vendedor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pagamento_vendedor
    ADD CONSTRAINT pagamento_vendedor_vendedor_id_fkey FOREIGN KEY (vendedor_id) REFERENCES public.vendedor(id);


--
-- Name: politica_comissao politica_comissao_modelo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.politica_comissao
    ADD CONSTRAINT politica_comissao_modelo_id_fkey FOREIGN KEY (modelo_id) REFERENCES public.modelo(id);


--
-- Name: politica_comissao politica_comissao_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.politica_comissao
    ADD CONSTRAINT politica_comissao_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: politica_comissao politica_comissao_vendedor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.politica_comissao
    ADD CONSTRAINT politica_comissao_vendedor_id_fkey FOREIGN KEY (vendedor_id) REFERENCES public.vendedor(id);


--
-- Name: presenca_vendedor presenca_vendedor_pagamento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.presenca_vendedor
    ADD CONSTRAINT presenca_vendedor_pagamento_id_fkey FOREIGN KEY (pagamento_id) REFERENCES public.pagamento_vendedor(id);


--
-- Name: presenca_vendedor presenca_vendedor_pago_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.presenca_vendedor
    ADD CONSTRAINT presenca_vendedor_pago_por_fkey FOREIGN KEY (pago_por) REFERENCES public.usuario(id);


--
-- Name: presenca_vendedor presenca_vendedor_registrado_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.presenca_vendedor
    ADD CONSTRAINT presenca_vendedor_registrado_por_fkey FOREIGN KEY (registrado_por) REFERENCES public.usuario(id);


--
-- Name: presenca_vendedor presenca_vendedor_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.presenca_vendedor
    ADD CONSTRAINT presenca_vendedor_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: presenca_vendedor presenca_vendedor_vendedor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.presenca_vendedor
    ADD CONSTRAINT presenca_vendedor_vendedor_id_fkey FOREIGN KEY (vendedor_id) REFERENCES public.vendedor(id) ON DELETE CASCADE;


--
-- Name: reserva reserva_cliente_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reserva
    ADD CONSTRAINT reserva_cliente_id_fkey FOREIGN KEY (cliente_id) REFERENCES public.cliente(id);


--
-- Name: reserva_config reserva_config_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reserva_config
    ADD CONSTRAINT reserva_config_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: reserva reserva_jetski_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reserva
    ADD CONSTRAINT reserva_jetski_id_fkey FOREIGN KEY (jetski_id) REFERENCES public.jetski(id);


--
-- Name: reserva reserva_modelo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reserva
    ADD CONSTRAINT reserva_modelo_id_fkey FOREIGN KEY (modelo_id) REFERENCES public.modelo(id);


--
-- Name: reserva reserva_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reserva
    ADD CONSTRAINT reserva_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: reserva reserva_vendedor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reserva
    ADD CONSTRAINT reserva_vendedor_id_fkey FOREIGN KEY (vendedor_id) REFERENCES public.vendedor(id);


--
-- Name: tenant_access tenant_access_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_access
    ADD CONSTRAINT tenant_access_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: tenant_access tenant_access_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_access
    ADD CONSTRAINT tenant_access_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- Name: tenant_signup tenant_signup_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_signup
    ADD CONSTRAINT tenant_signup_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: usuario_global_roles usuario_global_roles_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_global_roles
    ADD CONSTRAINT usuario_global_roles_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- Name: usuario_identity_provider usuario_identity_provider_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_identity_provider
    ADD CONSTRAINT usuario_identity_provider_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- Name: vendedor vendedor_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendedor
    ADD CONSTRAINT vendedor_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: abastecimento; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.abastecimento ENABLE ROW LEVEL SECURITY;

--
-- Name: assinatura; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.assinatura ENABLE ROW LEVEL SECURITY;

--
-- Name: assinatura assinatura_tenant_delete; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY assinatura_tenant_delete ON public.assinatura FOR DELETE USING ((tenant_id = (current_setting('app.tenant_id'::text, true))::uuid));


--
-- Name: assinatura assinatura_tenant_insert; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY assinatura_tenant_insert ON public.assinatura FOR INSERT WITH CHECK (true);


--
-- Name: assinatura assinatura_tenant_select; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY assinatura_tenant_select ON public.assinatura FOR SELECT USING ((tenant_id = COALESCE((current_setting('app.tenant_id'::text, true))::uuid, tenant_id)));


--
-- Name: assinatura assinatura_tenant_update; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY assinatura_tenant_update ON public.assinatura FOR UPDATE USING ((tenant_id = (current_setting('app.tenant_id'::text, true))::uuid));


--
-- Name: auditoria; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.auditoria ENABLE ROW LEVEL SECURITY;

--
-- Name: bonus_vendedor; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.bonus_vendedor ENABLE ROW LEVEL SECURITY;

--
-- Name: cliente; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.cliente ENABLE ROW LEVEL SECURITY;

--
-- Name: comissao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.comissao ENABLE ROW LEVEL SECURITY;

--
-- Name: convite; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.convite ENABLE ROW LEVEL SECURITY;

--
-- Name: despesa_manutencao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.despesa_manutencao ENABLE ROW LEVEL SECURITY;

--
-- Name: despesa_operacional; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.despesa_operacional ENABLE ROW LEVEL SECURITY;

--
-- Name: fechamento_diario; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.fechamento_diario ENABLE ROW LEVEL SECURITY;

--
-- Name: fechamento_mensal; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.fechamento_mensal ENABLE ROW LEVEL SECURITY;

--
-- Name: foto; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.foto ENABLE ROW LEVEL SECURITY;

--
-- Name: fuel_policy; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.fuel_policy ENABLE ROW LEVEL SECURITY;

--
-- Name: fuel_policy fuel_policy_tenant_delete; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY fuel_policy_tenant_delete ON public.fuel_policy FOR DELETE USING ((tenant_id = (current_setting('app.tenant_id'::text, true))::uuid));


--
-- Name: fuel_policy fuel_policy_tenant_insert; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY fuel_policy_tenant_insert ON public.fuel_policy FOR INSERT WITH CHECK (true);


--
-- Name: fuel_policy fuel_policy_tenant_select; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY fuel_policy_tenant_select ON public.fuel_policy FOR SELECT USING ((tenant_id = COALESCE((current_setting('app.tenant_id'::text, true))::uuid, tenant_id)));


--
-- Name: fuel_policy fuel_policy_tenant_update; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY fuel_policy_tenant_update ON public.fuel_policy FOR UPDATE USING ((tenant_id = (current_setting('app.tenant_id'::text, true))::uuid));


--
-- Name: fuel_price_day; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.fuel_price_day ENABLE ROW LEVEL SECURITY;

--
-- Name: item_opcional; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.item_opcional ENABLE ROW LEVEL SECURITY;

--
-- Name: jetski; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.jetski ENABLE ROW LEVEL SECURITY;

--
-- Name: locacao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.locacao ENABLE ROW LEVEL SECURITY;

--
-- Name: locacao_item_opcional; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.locacao_item_opcional ENABLE ROW LEVEL SECURITY;

--
-- Name: modelo marketplace_public_read; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY marketplace_public_read ON public.modelo FOR SELECT USING (((exibir_no_marketplace = true) AND (ativo = true) AND (EXISTS ( SELECT 1
   FROM public.tenant t
  WHERE ((t.id = modelo.tenant_id) AND ((t.status)::text = 'ATIVO'::text) AND (t.exibir_no_marketplace = true))))));


--
-- Name: modelo_midia marketplace_public_read; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY marketplace_public_read ON public.modelo_midia FOR SELECT USING ((EXISTS ( SELECT 1
   FROM (public.modelo m
     JOIN public.tenant t ON ((m.tenant_id = t.id)))
  WHERE ((m.id = modelo_midia.modelo_id) AND (m.ativo = true) AND (m.exibir_no_marketplace = true) AND ((t.status)::text = 'ATIVO'::text) AND (t.exibir_no_marketplace = true)))));


--
-- Name: tenant marketplace_public_read; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY marketplace_public_read ON public.tenant FOR SELECT USING (((exibir_no_marketplace = true) AND ((status)::text = 'ATIVO'::text)));


--
-- Name: modelo; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.modelo ENABLE ROW LEVEL SECURITY;

--
-- Name: modelo_midia; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.modelo_midia ENABLE ROW LEVEL SECURITY;

--
-- Name: os_manutencao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.os_manutencao ENABLE ROW LEVEL SECURITY;

--
-- Name: pagamento_vendedor; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.pagamento_vendedor ENABLE ROW LEVEL SECURITY;

--
-- Name: politica_comissao; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.politica_comissao ENABLE ROW LEVEL SECURITY;

--
-- Name: presenca_vendedor; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.presenca_vendedor ENABLE ROW LEVEL SECURITY;

--
-- Name: reserva; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.reserva ENABLE ROW LEVEL SECURITY;

--
-- Name: abastecimento tenant_isolation_abastecimento; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_abastecimento ON public.abastecimento USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: auditoria tenant_isolation_auditoria; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_auditoria ON public.auditoria USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: bonus_vendedor tenant_isolation_bonus_vendedor; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_bonus_vendedor ON public.bonus_vendedor USING ((tenant_id = (NULLIF(current_setting('app.tenant_id'::text, true), ''::text))::uuid));


--
-- Name: cliente tenant_isolation_cliente; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_cliente ON public.cliente USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: comissao tenant_isolation_comissao; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_comissao ON public.comissao USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: convite tenant_isolation_convite; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_convite ON public.convite USING (
CASE
    WHEN (public.get_current_tenant_id() IS NULL) THEN true
    ELSE (tenant_id = public.get_current_tenant_id())
END) WITH CHECK (
CASE
    WHEN (public.get_current_tenant_id() IS NULL) THEN true
    ELSE (tenant_id = public.get_current_tenant_id())
END);


--
-- Name: despesa_manutencao tenant_isolation_despesa_manutencao; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_despesa_manutencao ON public.despesa_manutencao USING ((tenant_id = (NULLIF(current_setting('app.tenant_id'::text, true), ''::text))::uuid));


--
-- Name: despesa_operacional tenant_isolation_despesa_operacional; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_despesa_operacional ON public.despesa_operacional USING ((tenant_id = (NULLIF(current_setting('app.tenant_id'::text, true), ''::text))::uuid));


--
-- Name: fechamento_diario tenant_isolation_fechamento_diario; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_fechamento_diario ON public.fechamento_diario USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: fechamento_mensal tenant_isolation_fechamento_mensal; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_fechamento_mensal ON public.fechamento_mensal USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: foto tenant_isolation_foto; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_foto ON public.foto USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: fuel_price_day tenant_isolation_fuel_price_day; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_fuel_price_day ON public.fuel_price_day USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: item_opcional tenant_isolation_item_opcional; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_item_opcional ON public.item_opcional USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: jetski tenant_isolation_jetski; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_jetski ON public.jetski USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: locacao tenant_isolation_locacao; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_locacao ON public.locacao USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: locacao_item_opcional tenant_isolation_locacao_item_opcional; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_locacao_item_opcional ON public.locacao_item_opcional USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: modelo tenant_isolation_modelo; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_modelo ON public.modelo USING ((tenant_id = (NULLIF(current_setting('app.tenant_id'::text, true), ''::text))::uuid));


--
-- Name: modelo_midia tenant_isolation_modelo_midia; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_modelo_midia ON public.modelo_midia USING ((tenant_id = (NULLIF(current_setting('app.tenant_id'::text, true), ''::text))::uuid));


--
-- Name: os_manutencao tenant_isolation_os_manutencao; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_os_manutencao ON public.os_manutencao USING ((tenant_id = (NULLIF(current_setting('app.tenant_id'::text, true), ''::text))::uuid));


--
-- Name: pagamento_vendedor tenant_isolation_pagamento_vendedor; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_pagamento_vendedor ON public.pagamento_vendedor USING ((tenant_id = (NULLIF(current_setting('app.tenant_id'::text, true), ''::text))::uuid));


--
-- Name: politica_comissao tenant_isolation_politica_comissao; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_politica_comissao ON public.politica_comissao USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: presenca_vendedor tenant_isolation_presenca_vendedor; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_presenca_vendedor ON public.presenca_vendedor USING ((tenant_id = (NULLIF(current_setting('app.tenant_id'::text, true), ''::text))::uuid));


--
-- Name: reserva tenant_isolation_reserva; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_reserva ON public.reserva USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: vendedor tenant_isolation_vendedor; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation_vendedor ON public.vendedor USING ((tenant_id = public.get_current_tenant_id()));


--
-- Name: vendedor; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.vendedor ENABLE ROW LEVEL SECURITY;

--
-- PostgreSQL database dump complete
--


