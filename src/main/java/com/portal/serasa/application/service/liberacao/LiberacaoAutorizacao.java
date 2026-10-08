package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Quem pode o quê na esteira de liberação.
 *
 * <p>Ao contrário da prospecção, aqui há separação de verdade, pedida pelo departamento: na
 * Origem todo mundo cria, edita e apaga; do Comitê em diante só analista mexe no card. Comentar e
 * ser marcado continua aberto a todos em qualquer etapa, e o destinatário de uma pendência
 * responde mesmo sem ser analista.</p>
 *
 * <p>A mesma regra alimenta duas coisas: a recusa no servidor e o motivo que a tela mostra no
 * botão desabilitado. Ficam juntas para não divergirem — se a tela libera e o servidor recusa, o
 * usuário descobre soltando o card e tomando erro.</p>
 */
@Component
public class LiberacaoAutorizacao {

    public boolean podeEditar(LiberacaoCardEntity card, UserEntity usuario) {
        return card.getEtapa() == EtapaLiberacao.ORIGEM || ehAnalista(usuario);
    }

    /** Editar campos, apagar. */
    public void exigirEdicao(LiberacaoCardEntity card, UserEntity usuario) {
        exigirAutenticado(usuario);
        if (!podeEditar(card, usuario)) {
            throw new AcessoNegadoException("A partir do Comitê, só analista altera o card.");
        }
    }

    /**
     * Por que esta pessoa não pode mover o card para {@code destino} agora. Vazio = pode.
     *
     * @param aguardando nomes de quem ainda deve parecer na rodada vigente
     * @param comiteVazio ninguém tem a marca de Comitê
     */
    public Optional<String> motivoBloqueio(LiberacaoCardEntity card, EtapaLiberacao destino,
                                           UserEntity usuario, List<String> aguardando,
                                           boolean comiteVazio) {
        return motivoCaminho(card, destino)
                .or(() -> motivoPermissao(card, usuario))
                .or(() -> motivoRegra(card, destino, aguardando, comiteVazio));
    }

    public void exigirTransicao(LiberacaoCardEntity card, EtapaLiberacao destino, UserEntity usuario,
                                List<String> aguardando, boolean comiteVazio) {
        exigirAutenticado(usuario);
        // Caminho inexistente é regra da máquina de estados (409), não falta de permissão (403).
        motivoCaminho(card, destino).ifPresent(motivo -> {
            throw new TransicaoInvalidaException(motivo);
        });
        motivoPermissao(card, usuario).ifPresent(motivo -> {
            throw new AcessoNegadoException(motivo);
        });
        motivoRegra(card, destino, aguardando, comiteVazio).ifPresent(motivo -> {
            throw new TransicaoInvalidaException(motivo);
        });
    }

    /** Abrir pendência é decisão da analista; responder, não. */
    public void exigirAnalista(UserEntity usuario, String acao) {
        exigirAutenticado(usuario);
        if (!ehAnalista(usuario)) {
            throw new AcessoNegadoException("Só analista pode " + acao + ".");
        }
    }

    /** Responde quem recebeu a pendência, ou uma analista cobrindo a colega. */
    public void exigirResposta(LiberacaoPendenciaEntity pendencia, UserEntity usuario) {
        exigirAutenticado(usuario);
        boolean destinatario = usuario.getId().equals(pendencia.getDestinatarioId());
        if (!destinatario && !ehAnalista(usuario)) {
            throw new AcessoNegadoException("Só " + pendencia.getDestinatarioNome() + " ou uma analista responde esta pendência.");
        }
    }

    public void exigirAutenticado(UserEntity usuario) {
        if (usuario == null || usuario.getId() == null) {
            throw new AcessoNegadoException("Não autenticado");
        }
    }

    private Optional<String> motivoCaminho(LiberacaoCardEntity card, EtapaLiberacao destino) {
        if (!card.getEtapa().aceita(destino)) {
            return Optional.of("Não dá para ir de " + rotulo(card.getEtapa()) + " para " + rotulo(destino) + ".");
        }
        return Optional.empty();
    }

    private Optional<String> motivoPermissao(LiberacaoCardEntity card, UserEntity usuario) {
        if (card.getEtapa() != EtapaLiberacao.ORIGEM && !ehAnalista(usuario)) {
            return Optional.of("A partir do Comitê, só analista move o card.");
        }
        return Optional.empty();
    }

    private Optional<String> motivoRegra(LiberacaoCardEntity card, EtapaLiberacao destino,
                                         List<String> aguardando, boolean comiteVazio) {
        if (destino == EtapaLiberacao.COMITE && comiteVazio) {
            return Optional.of("Ninguém está marcado como Comitê. Peça ao admin para marcar em Configurações.");
        }
        if (EtapaLiberacao.decisaoDoComite(card.getEtapa(), destino) && !aguardando.isEmpty()) {
            return Optional.of("Aguardando parecer de " + juntar(aguardando) + ".");
        }
        return Optional.empty();
    }

    /** "A", "A e B", "A, B e C". */
    static String juntar(List<String> nomes) {
        if (nomes.size() <= 1) {
            return String.join("", nomes);
        }
        return String.join(", ", nomes.subList(0, nomes.size() - 1)) + " e " + nomes.get(nomes.size() - 1);
    }

    private boolean ehAnalista(UserEntity usuario) {
        return usuario != null && usuario.isAnalista();
    }

    static String rotulo(EtapaLiberacao etapa) {
        return switch (etapa) {
            case ORIGEM -> "Origem";
            case COMITE -> "Comitê";
            case PENDENCIA -> "Pendência";
            case APROVADO -> "Aprovado";
            case REPROVADO -> "Reprovado";
        };
    }
}
