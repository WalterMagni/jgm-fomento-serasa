package com.portal.serasa.application.service.empresa;

import com.portal.serasa.infrastructure.persistence.entity.EmpresaLimiteSacadoEntity;
import com.portal.serasa.infrastructure.persistence.entity.EmpresaLimiteSacadoHistoricoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.EmpresaLimiteSacadoHistoricoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.EmpresaLimiteSacadoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Limite da empresa como sacado (pedido do time em 2026-10-26), definido na Visão 360º e mostrado
 * na esteira de liberação. Qualquer usuário define, troca ou retira; cada troca fica no histórico
 * com quem fez e quando, como a nota da equipe.
 */
@Service
@RequiredArgsConstructor
public class EmpresaLimiteSacadoService {

    private final EmpresaLimiteSacadoJpaRepository limiteRepository;
    private final EmpresaLimiteSacadoHistoricoJpaRepository historicoRepository;

    /** Uma troca. {@code valor} nulo = o limite foi retirado. */
    public record Registro(BigDecimal valor, String por, LocalDateTime em) {
    }

    /** Limite atual (nulo se não tem) e as últimas trocas, da mais recente para a mais antiga. */
    public record LimiteAtual(BigDecimal valor, String por, LocalDateTime em, List<Registro> historico) {
    }

    @Transactional(readOnly = true)
    public LimiteAtual consultar(String cnpj) {
        String documento = normalizar(cnpj);
        List<Registro> historico = historicoRepository.findTop10ByCnpjOrderByCriadoEmDesc(documento).stream()
                .map(h -> new Registro(h.getValor(), h.getUsuarioNome(), h.getCriadoEm()))
                .toList();
        return limiteRepository.findById(documento)
                .map(l -> new LimiteAtual(l.getValor(), l.getUsuarioNome(), l.getDefinidoEm(), historico))
                .orElseGet(() -> new LimiteAtual(null, null, null, historico));
    }

    /**
     * Define, troca ou retira ({@code valor} nulo) o limite. Repetir o valor atual, ou retirar o
     * de quem não tem, não muda nada nem suja o histórico.
     */
    @Transactional
    public LimiteAtual definir(String cnpj, BigDecimal valor, UserEntity autor) {
        String documento = normalizar(cnpj);
        BigDecimal novo = valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
        if (novo != null && novo.signum() <= 0) {
            throw new IllegalArgumentException("O limite precisa ser maior que zero. Para tirar o limite, deixe em branco.");
        }
        var atual = limiteRepository.findById(documento);
        BigDecimal anterior = atual.map(EmpresaLimiteSacadoEntity::getValor).orElse(null);
        if (anterior == null ? novo == null : novo != null && anterior.compareTo(novo) == 0) {
            return consultar(documento);
        }

        LocalDateTime agora = LocalDateTime.now();
        if (novo == null) {
            limiteRepository.deleteById(documento);
        } else {
            EmpresaLimiteSacadoEntity registro = atual.orElseGet(() -> EmpresaLimiteSacadoEntity.builder().cnpj(documento).build());
            registro.setValor(novo);
            registro.setUsuarioId(autor.getId());
            registro.setUsuarioNome(autor.getName());
            registro.setDefinidoEm(agora);
            limiteRepository.save(registro);
        }
        historicoRepository.save(EmpresaLimiteSacadoHistoricoEntity.builder()
                .cnpj(documento)
                .valor(novo)
                .usuarioId(autor.getId())
                .usuarioNome(autor.getName())
                .criadoEm(agora)
                .build());
        return consultar(documento);
    }

    /** Limites de várias empresas numa consulta só, para a liberação. Sem limite = fora do mapa. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> limitesDe(Collection<String> cnpjs) {
        List<String> empresas = cnpjs == null ? List.of() : cnpjs.stream().filter(c -> c != null && c.length() == 14).distinct().toList();
        if (empresas.isEmpty()) {
            return Map.of();
        }
        return limiteRepository.findByCnpjIn(empresas).stream()
                .collect(Collectors.toMap(EmpresaLimiteSacadoEntity::getCnpj, EmpresaLimiteSacadoEntity::getValor, (a, b) -> a));
    }

    private static String normalizar(String cnpj) {
        String documento = cnpj == null ? "" : cnpj.replaceAll("\\D", "");
        if (documento.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve ter 14 dígitos.");
        }
        return documento;
    }
}
