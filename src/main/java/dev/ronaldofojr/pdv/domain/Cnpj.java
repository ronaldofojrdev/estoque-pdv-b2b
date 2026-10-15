package dev.ronaldofojr.pdv.domain;

/** Validacao e normalizacao de CNPJ (14 digitos, com os dois digitos verificadores). */
public final class Cnpj {

    private static final int[] WEIGHTS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] WEIGHTS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Cnpj() {
    }

    /** Remove pontuacao: "11.222.333/0001-81" vira "11222333000181". */
    public static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public static boolean isValid(String value) {
        // so digitos e a pontuacao comum (. / - e espaco); qualquer outra coisa, como letras, invalida o CNPJ
        if (value == null || !value.matches("[0-9./\\-\\s]*")) {
            return false;
        }
        String cnpj = normalize(value);
        if (cnpj.length() != 14 || cnpj.chars().distinct().count() == 1) {
            return false; // tamanho errado ou todos os digitos iguais (00000000000000...)
        }
        int first = checkDigit(cnpj, WEIGHTS_1);
        int second = checkDigit(cnpj, WEIGHTS_2);
        return first == cnpj.charAt(12) - '0' && second == cnpj.charAt(13) - '0';
    }

    private static int checkDigit(String cnpj, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (cnpj.charAt(i) - '0') * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }
}
