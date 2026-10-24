package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMotivoReprovacaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMotivoReprovacaoJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Catálogo dos motivos de reprovação. Todos leem; só o admin altera.
 *
 * <p>Motivo não se apaga: desativado, some da lista de escolha, e o card que o usou continua
 * legível, porque guarda o nome como texto.</p>
 */
@Service
@RequiredArgsConstructor
public class LiberacaoMotivoReprovacaoService {

    private final LiberacaoMotivoReprovacaoJpaRepository repository;
    private final LiberacaoAutorizacao autorizacao;

    public List<LiberacaoMotivoReprovacaoEntity> listar() {
        return repository.findAllByOrderByOrdemAscNomeAsc();
    }

    @Transactional
    public LiberacaoMotivoReprovacaoEntity criar(String nome, Integer ordem, UserEntity autor) {
        autorizacao.exigirAdmin(autor);
        String limpo = nomeValido(nome);
        if (repository.existsByNomeIgnoreCase(limpo)) {
            throw new TransicaoInvalidaException("Motivo já existe na lista: " + limpo);
        }
        return repository.save(LiberacaoMotivoReprovacaoEntity.builder()
                .nome(limpo)
                .ordem(ordem == null ? 99 : ordem)
                .ativo(true)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** Renomear não muda os cards já reprovados: eles guardam o nome do momento. */
    @Transactional
    public LiberacaoMotivoReprovacaoEntity editar(UUID id, String nome, Integer ordem, Boolean ativo, UserEntity autor) {
        autorizacao.exigirAdmin(autor);
        LiberacaoMotivoReprovacaoEntity motivo = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Motivo de reprovação não encontrado"));
        String limpo = nomeValido(nome);
        if (!limpo.equalsIgnoreCase(motivo.getNome()) && repository.existsByNomeIgnoreCase(limpo)) {
            throw new TransicaoInvalidaException("Motivo já existe na lista: " + limpo);
        }
        motivo.setNome(limpo);
        if (ordem != null) {
            motivo.setOrdem(ordem);
        }
        if (ativo != null) {
            motivo.setAtivo(ativo);
        }
        return repository.save(motivo);
    }

    private static String nomeValido(String nome) {
        String limpo = nome == null ? "" : nome.trim().replaceAll("\\s+", " ");
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("Informe o motivo.");
        }
        if (limpo.length() > 120) {
            throw new IllegalArgumentException("Motivo com até 120 caracteres.");
        }
        return limpo;
    }
}
