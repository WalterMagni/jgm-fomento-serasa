package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoParecerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Abre uma rodada do comitê: leva o card ao estágio e convoca cada membro ({@code users.comite})
 * com um parecer vazio.
 *
 * <p>Separado do {@link ProspeccaoComiteService} porque duas portas levam ao comitê: o envio da
 * analista (da documentação, ou de volta do contrato) e a contraproposta do cliente, registrada
 * no {@link ProspeccaoDecisaoService}. Quem chama confere permissão e roda na própria transação.</p>
 */
@Component
@RequiredArgsConstructor
public class ProspeccaoRodadaComite {

    private final ProspeccaoService prospeccaoService;
    private final ProspeccaoJpaRepository prospeccaoRepository;
    private final ProspeccaoParecerJpaRepository parecerRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher acontecimentos;

    /**
     * @param motivo por que a rodada abriu, para a timeline; nulo no envio comum
     */
    public ProspeccaoEntity abrir(UUID id, String motivo, UserEntity autor) {
        List<UserEntity> membros = userRepository.findByComiteTrueOrderByNameAsc();
        if (membros.isEmpty()) {
            throw new TransicaoInvalidaException(
                    "Nenhum usuário está marcado como Comitê. Peça ao admin para marcar em Configurações.");
        }

        ProspeccaoEntity movido = prospeccaoService.mover(id, EstagioProspeccao.COMITE, null, null, autor);
        movido.setRodadaComite((movido.getRodadaComite() == null ? 0 : movido.getRodadaComite()) + 1);
        ProspeccaoEntity salvo = prospeccaoRepository.save(movido);

        LocalDateTime agora = LocalDateTime.now();
        for (UserEntity membro : membros) {
            parecerRepository.save(ProspeccaoParecerEntity.builder()
                    .prospeccaoId(id)
                    .rodada(salvo.getRodadaComite())
                    .usuarioId(membro.getId())
                    .usuarioNome(membro.getName())
                    .criadoEm(agora)
                    .build());
        }
        String nomes = membros.stream().map(UserEntity::getName).collect(Collectors.joining(", "));
        prospeccaoService.registrarEvento(salvo, TipoEventoProspeccao.COMITE_ENVIADO, null, null, null,
                "Rodada " + salvo.getRodadaComite() + (motivo == null ? "" : " (" + motivo + ")") + " · convocados: " + nomes,
                autor);
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.ComiteConvocado(salvo,
                membros.stream().map(UserEntity::getId).toList(), autor));
        return salvo;
    }
}
