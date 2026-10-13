package com.portal.serasa.application.service.empresa;

import com.portal.serasa.domain.model.NotaEmpresa;
import com.portal.serasa.infrastructure.persistence.entity.EmpresaNotaEntity;
import com.portal.serasa.infrastructure.persistence.entity.EmpresaNotaHistoricoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.EmpresaNotaHistoricoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.EmpresaNotaJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Nota que a equipe dá para a empresa (A a D). Qualquer usuário dá ou troca; cada troca fica no
 * histórico com quem fez e quando.
 */
@Service
@RequiredArgsConstructor
public class EmpresaNotaService {

    private final EmpresaNotaJpaRepository notaRepository;
    private final EmpresaNotaHistoricoJpaRepository historicoRepository;

    /** Uma troca de nota. {@code nota} nula = a nota foi retirada. */
    public record Registro(NotaEmpresa nota, String por, LocalDateTime em) {
    }

    /** Nota atual (nula se a empresa não tem) e as últimas trocas, da mais recente para a mais antiga. */
    public record NotaAtual(NotaEmpresa nota, String por, LocalDateTime em, List<Registro> historico) {
    }

    @Transactional(readOnly = true)
    public NotaAtual consultar(String cnpj) {
        String documento = normalizar(cnpj);
        List<Registro> historico = historicoRepository.findTop10ByCnpjOrderByCriadoEmDesc(documento).stream()
                .map(h -> new Registro(h.getNota(), h.getUsuarioNome(), h.getCriadoEm()))
                .toList();
        return notaRepository.findById(documento)
                .map(n -> new NotaAtual(n.getNota(), n.getUsuarioNome(), n.getAtribuidaEm(), historico))
                .orElseGet(() -> new NotaAtual(null, null, null, historico));
    }

    /**
     * Dá, troca ou retira ({@code nota} nula) a nota da empresa. Repetir a nota atual, ou retirar
     * a de quem não tem, não muda nada nem suja o histórico.
     */
    @Transactional
    public NotaAtual atribuir(String cnpj, NotaEmpresa nota, UserEntity autor) {
        String documento = normalizar(cnpj);
        var atual = notaRepository.findById(documento);
        NotaEmpresa anterior = atual.map(EmpresaNotaEntity::getNota).orElse(null);
        if (anterior == nota) {
            return consultar(documento);
        }

        LocalDateTime agora = LocalDateTime.now();
        if (nota == null) {
            notaRepository.deleteById(documento);
        } else {
            EmpresaNotaEntity registro = atual.orElseGet(() -> EmpresaNotaEntity.builder().cnpj(documento).build());
            registro.setNota(nota);
            registro.setUsuarioId(autor.getId());
            registro.setUsuarioNome(autor.getName());
            registro.setAtribuidaEm(agora);
            notaRepository.save(registro);
        }
        historicoRepository.save(EmpresaNotaHistoricoEntity.builder()
                .cnpj(documento)
                .nota(nota)
                .usuarioId(autor.getId())
                .usuarioNome(autor.getName())
                .criadoEm(agora)
                .build());
        return consultar(documento);
    }

    /** Notas de várias empresas numa consulta só, para a lista da carteira. Sem nota = fora do mapa. */
    @Transactional(readOnly = true)
    public Map<String, NotaEmpresa> notasDe(Collection<String> cnpjs) {
        if (cnpjs == null || cnpjs.isEmpty()) {
            return Map.of();
        }
        return notaRepository.findByCnpjIn(cnpjs).stream()
                .collect(Collectors.toMap(EmpresaNotaEntity::getCnpj, EmpresaNotaEntity::getNota, (a, b) -> a));
    }

    private static String normalizar(String cnpj) {
        String documento = cnpj == null ? "" : cnpj.replaceAll("\\D", "");
        if (documento.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve ter 14 dígitos.");
        }
        return documento;
    }
}
