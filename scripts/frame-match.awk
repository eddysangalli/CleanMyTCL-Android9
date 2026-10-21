# Casa a tecla com o quadro que ela provocou, no `dumpsys gfxinfo <pkg> framestats` do Android 9.
#
# Por que o termino do quadro e nao o vsync pretendido: num launcher que move o foco em um unico
# quadro (sem animacao), o quadro que reage a tecla nasce do vsync ANTERIOR a ela e so termina depois.
# Casar pelo IntendedVsync descarta a maioria dessas teclas e deixa uma amostra pequena demais.
# A mesma regra esta em tools/bench.py; as duas ferramentas tem de concordar, senao os numeros de
# uma nao valem para a outra.
#
# Variaveis (-v):
#   t0       eventTime da tecla, em ms de uptime (do log do `input`)
#   t_before Uptime do `dumpsys gfxinfo reset`, antes da tecla (ms)
#   t_after  Uptime do dump do framestats, depois da tecla (ms)
#   key      keycode, devolvido como terceira coluna
# Saida: "<resposta_ms> <movimento_ms> <key>" ou nada quando a tecla nao tem reacao visual.
#
# Colunas do PROFILEDATA: 1 Flags, 2 IntendedVsync, 14 FrameCompleted (ns). Flags != 0 = descartado.

BEGIN {
    FS = ","
    # Uma linha de log de outra tecla cai fora da janela entre o reset e o dump: usa o inicio
    # da janela e a medida vira um numero honesto, em vez de segundos.
    if (t_before != "" && t0 < t_before) t0 = t_before
    if (t_after  != "" && t0 > t_after)  t0 = t_before
    threshold = t0 * 1e6
    first = 0
}

/---PROFILEDATA---/ { inside = !inside; next }

inside && $1 == "0" && ($14 + 0) >= threshold {
    if (!first) {
        # Uma tecla que nao move nada (o fim de uma linha, o foco na barra) deixa o proximo quadro,
        # causado por outra coisa, ser medido como resposta. Um Android TV responde em 16 a 33 ms:
        # um primeiro quadro que so termina mais de 150 ms depois nao e a resposta desta tecla.
        if (($14 - threshold) / 1e6 > 150) exit
        first = $14; last = $14; prev = $2
        next
    }
    # Intervalo maior que 50 ms: o movimento acabou; o que vem depois e o relogio ou a saudacao.
    if (($2 - prev) / 1e6 > 50) exit
    last = $14; prev = $2
}

END {
    if (first) printf "%d %d %d\n", (first - threshold) / 1e6, (last - threshold) / 1e6, key
}
