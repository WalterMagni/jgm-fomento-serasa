package com.portal.serasa.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Jobs periódicos. Hoje só o acompanhamento dos pedidos assíncronos da Judit ({@code JuditAcompanhador}). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
