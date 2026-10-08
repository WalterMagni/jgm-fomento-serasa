package com.portal.serasa.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quem é admin do portal: os e-mails de {@code APP_USER_MANAGEMENT_ALLOWED_EMAILS}.
 *
 * <p>O admin não vem de {@code users.role}, que tem três grafias no banco e nunca foi usado para
 * decidir nada. A checagem estava copiada em dois controllers; vive aqui desde que a esteira de
 * liberação passou a precisar dela para marcar analistas.</p>
 */
@Component
public class AdminAllowList {

    private final Set<String> emails;

    public AdminAllowList(@Value("${app.user-management.allowed-emails:}") String configurado) {
        this.emails = Arrays.stream(configurado.split("[,;]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean contem(String email) {
        return email != null && !email.isBlank()
                && emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
