package com.smartfinance.shared.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normaliza descrições de transações bancárias para consumo pela AI.
 *
 * Remove ruído estrutural (códigos de terminal, IBANs, referências alfanuméricas)
 * e expande abreviaturas bancárias para texto legível em português.
 *
 * A descrição normalizada é usada APENAS em prompts e pré-visualização;
 * nunca é persistida — a descrição original é sempre mantida na DB.
 *
 * Classe utilitária estática: sem estado, sem dependências Spring.
 */
public final class DescriptionNormalizer {

    private DescriptionNormalizer() {}

    // -----------------------------------------------------------------------
    // Padrões de ruído a remover
    // -----------------------------------------------------------------------

    /** IBAN: PT50000201231234567890154, LU960000000000, GB29NWBK60161331926819 */
    private static final Pattern IBAN = Pattern.compile(
            "[A-Z]{2}\\d{2}[A-Z0-9]{10,30}", Pattern.CASE_INSENSITIVE);

    /** Referências alfanuméricas compactas: 4MWJ2254UWEGQ, XKCD9283NF */
    private static final Pattern ALPHANUM_REF = Pattern.compile(
            "\\b[A-Z0-9]{8,}\\b");

    /** Códigos postais portugueses: 7300-, 7300-100 */
    private static final Pattern POSTAL_CODE = Pattern.compile(
            "\\b\\d{4}-(?:\\d{3})?\\b");

    /** Sequências de 4 ou mais dígitos isolados (códigos de terminal, autorização) */
    private static final Pattern DIGIT_CODE = Pattern.compile(
            "\\b\\d{4,}\\b");

    /** Traços, barras e pontuação final sobrando após limpeza */
    private static final Pattern TRAILING_PUNCT = Pattern.compile(
            "[\\-/.,;:]+$");

    /** Espaços múltiplos */
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    // -----------------------------------------------------------------------
    // Abreviaturas bancárias → forma legível
    // Ordem importa: padrões mais específicos primeiro.
    // -----------------------------------------------------------------------

    private static final Map<Pattern, String> ABBREVIATIONS = new LinkedHashMap<>();

    static {
        // Transferências
        put("TRF\\.?\\s+P/O",          "Transferência para");
        put("TRF\\.?\\s+DE",           "Transferência de");
        put("TRF\\.?\\s+BETWEEN",      "Transferência entre contas");
        put("TRF\\.?",                 "Transferência");

        // Débitos diretos
        put("\\bDD\\b",                "Débito Direto");

        // Compras / pagamentos em terminais
        put("COMPRA\\s+\\d+",          "Compra em");
        put("\\bCOMPRA\\b",            "Compra em");

        // MB WAY
        put("MB WAY TELEMOVEL",        "MB Way");
        put("MB WAY",                  "MB Way");

        // Pagamentos de serviços
        put("PAG\\.?\\s+SERV\\.?",     "Pagamento de Serviços");
        put("PAGAMENTO DE SERVICOS",   "Pagamento de Serviços");
        put("PAG\\.?\\s+IMPO\\.?",     "Pagamento de Impostos");
        put("PAG\\.?\\s+REF\\.?",      "Pagamento por Referência");

        // Levantamentos / depósitos
        put("LEV\\.?\\s+NUM\\.?",      "Levantamento");
        put("\\bLEVANTAMENTO\\b",      "Levantamento");
        put("DEP\\.?\\s+NUM\\.?",      "Depósito");
        put("\\bDEPOSITO\\b",          "Depósito");

        // Juros / comissões
        put("JUR\\.?\\s+DEB\\.?",      "Juros Devedores");
        put("JUR\\.?\\s+CRED\\.?",     "Juros Credores");
        put("COM\\.?\\s+MANUT\\.?",    "Comissão de Manutenção");

        // Estabelecimentos comuns
        put("EST SERVICO",             "Estação de Serviço");
        put("\\bCOMBUST\\.?\\b",       "Combustível");
        put("\\bSUPERMERCADO\\b",      "Supermercado");
        put("\\bHIPERMERCADO\\b",      "Hipermercado");
        put("\\bFARMACIA\\b",          "Farmácia");
    }

    private static void put(String regex, String replacement) {
        ABBREVIATIONS.put(
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE),
                replacement);
    }

    // -----------------------------------------------------------------------
    // API pública
    // -----------------------------------------------------------------------

    /**
     * Normaliza uma descrição bancária.
     *
     * @param raw descrição original tal como vem do banco / do utilizador
     * @return descrição limpa e legível; se a limpeza destruir tudo, devolve o original
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return raw;

        String s = raw.trim();

        // 1. Expandir abreviaturas (antes de remover tokens, para não cortar contexto)
        for (Map.Entry<Pattern, String> entry : ABBREVIATIONS.entrySet()) {
            s = entry.getKey().matcher(s).replaceAll(entry.getValue());
        }

        // 2. Remover IBANs completos
        s = IBAN.matcher(s).replaceAll("");

        // 3. Remover referências alfanuméricas compactas (8+ chars)
        s = ALPHANUM_REF.matcher(s).replaceAll("");

        // 4. Remover códigos postais
        s = POSTAL_CODE.matcher(s).replaceAll("");

        // 5. Remover sequências de 4+ dígitos isolados
        s = DIGIT_CODE.matcher(s).replaceAll("");

        // 6. Limpar pontuação sobrando e espaços redundantes
        s = TRAILING_PUNCT.matcher(s.trim()).replaceAll("");
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();

        return s.isBlank() ? raw : s;
    }
}
