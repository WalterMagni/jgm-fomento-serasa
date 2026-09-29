package com.portal.serasa.application.service.prospeccao;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Contagem de dias úteis para o SLA da esteira.
 *
 * <p>Utilitário puro de propósito: o cálculo é a regra mais fácil de errar de toda a esteira, e
 * separado assim ele é testável sem banco, sem Spring e sem relógio.</p>
 *
 * <p>Considera sábado, domingo e os feriados <b>nacionais</b>. Feriado municipal fica de fora:
 * a equipe é de uma cidade só, mas cravar isso no código tornaria o número errado no dia em que
 * alguém operar de outro lugar — e errar por um dia a mais de prazo é menos grave do que marcar
 * de vermelho um card que estava no prazo.</p>
 */
public final class DiasUteis {

    private DiasUteis() {
    }

    /**
     * Horas úteis decorridas entre dois instantes.
     *
     * <p>O time mede a análise em horas, não em dias: doze horas para alguém pegar, vinte e quatro
     * para decidir. Conta-se a hora que cai em dia útil — fim de semana e feriado nacional são
     * pulados por inteiro. Não se modela expediente das nove às dezoito: seria preciso acertar
     * horário de almoço, plantão e exceção, e errar isso deixaria o prazo mais confuso do que
     * ausente.</p>
     */
    public static long horasUteisEntre(java.time.LocalDateTime inicio, java.time.LocalDateTime fim) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            return 0;
        }
        Set<LocalDate> feriados = feriadosNacionais(inicio.getYear(), fim.getYear());
        long horas = 0;
        java.time.LocalDateTime cursor = inicio.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        while (cursor.isBefore(fim)) {
            if (util(cursor.toLocalDate(), feriados)) {
                horas++;
            }
            cursor = cursor.plusHours(1);
        }
        return horas;
    }

    /**
     * Dias úteis decorridos entre duas datas, sem contar o dia inicial.
     *
     * <p>Entrar num estágio hoje significa zero dia útil decorrido: o prazo só começa a correr
     * no próximo dia útil.</p>
     */
    public static int entre(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            return 0;
        }
        Set<LocalDate> feriados = feriadosNacionais(inicio.getYear(), fim.getYear());
        int dias = 0;
        for (LocalDate d = inicio.plusDays(1); !d.isAfter(fim); d = d.plusDays(1)) {
            if (util(d, feriados)) {
                dias++;
            }
        }
        return dias;
    }

    public static boolean util(LocalDate data, Set<LocalDate> feriados) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY && !feriados.contains(data);
    }

    /** Feriados nacionais de um intervalo de anos, fixos e móveis. */
    public static Set<LocalDate> feriadosNacionais(int anoInicial, int anoFinal) {
        Set<LocalDate> feriados = new HashSet<>();
        for (int ano = anoInicial; ano <= anoFinal; ano++) {
            feriados.add(LocalDate.of(ano, 1, 1));    // Confraternização Universal
            feriados.add(LocalDate.of(ano, 4, 21));   // Tiradentes
            feriados.add(LocalDate.of(ano, 5, 1));    // Dia do Trabalho
            feriados.add(LocalDate.of(ano, 9, 7));    // Independência
            feriados.add(LocalDate.of(ano, 10, 12));  // Nossa Senhora Aparecida
            feriados.add(LocalDate.of(ano, 11, 2));   // Finados
            feriados.add(LocalDate.of(ano, 11, 15));  // Proclamação da República
            feriados.add(LocalDate.of(ano, 11, 20));  // Consciência Negra (nacional desde 2024)
            feriados.add(LocalDate.of(ano, 12, 25));  // Natal

            LocalDate pascoa = pascoa(ano);
            feriados.add(pascoa.minusDays(48));       // segunda de carnaval
            feriados.add(pascoa.minusDays(47));       // terça de carnaval
            feriados.add(pascoa.minusDays(2));        // Sexta-feira Santa
            feriados.add(pascoa.plusDays(60));        // Corpus Christi
        }
        return feriados;
    }

    /** Domingo de Páscoa pelo algoritmo gregoriano anônimo (Meeus/Jones/Butcher). */
    static LocalDate pascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(ano, mes, dia);
    }
}
