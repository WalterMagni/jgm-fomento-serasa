package com.portal.serasa.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.model.CreditAnalysisStatus;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

        private final UserRepository userRepository;
        private final ClientRepository clientRepository;
        private final PasswordEncoder passwordEncoder;
        private final CompanyDetailRepository companyDetailRepository;
        private final CreditAnalysisRepository creditAnalysisRepository;
        private final ObjectMapper objectMapper;

        @Override
        @Transactional
        public void run(String... args) throws Exception {
                log.info("Iniciando verificação de Seed de dados...");
                seedUsers();
                seedCompanyAndCredit();
                log.info("Seed de dados concluído.");
        }

        private void seedUsers() {
                if (userRepository.count() == 0) {
                        log.info("Criando usuários de teste...");
                        UserEntity admin = UserEntity.builder()
                                        .name("Administrador do Sistema")
                                        .email("admin@jgm.com.br")
                                        .passwordHash(passwordEncoder.encode("admin123"))
                                        .role("ADMIN")
                                        .build();

                        UserEntity operador = UserEntity.builder()
                                        .name("Operador de Crédito")
                                        .email("operador@jgm.com.br")
                                        .passwordHash(passwordEncoder.encode("operador123"))
                                        .role("USER")
                                        .build();

                        userRepository.saveAll(List.of(admin, operador));
                        log.info("Usuários criados com sucesso.");
                }
        }

        private void seedCompanyAndCredit() {
                if (companyDetailRepository.findAll(org.springframework.data.domain.Pageable.unpaged()).getContent()
                                .isEmpty()) {
                        log.info("Criando empresas e análises de teste...");

                        Client client1 = Client.builder()
                                        .documentNumber("12345678000190")
                                        .name("Tech Solutions Logística LTDA")
                                        .build();
                        clientRepository.save(client1);

                        // Empresa 1: Baixo Risco
                        CompanyDetail company1 = CompanyDetail.builder()
                                        .documentNumber("12345678000190")
                                        .companyName("Tech Solutions Logística LTDA")
                                        .alias("TechLog")
                                        .founded(LocalDate.of(2010, 3, 15))
                                        .statusText("Ativa")
                                        .city("São Paulo")
                                        .state("SP")
                                        .companyEquity(new BigDecimal("500000.00"))
                                        .build();
                        companyDetailRepository.save(company1);

                        CreditAnalysis analysis1 = CreditAnalysis.builder()
                                        .clientId(client1.getId())
                                        .cnpj("12345678000190")
                                        .companyName("Tech Solutions Logística LTDA")
                                        .score(720)
                                        .riskClass("Baixo")
                                        .probability(new BigDecimal("0.02"))
                                        .analysisDate(LocalDateTime.now())
                                        .consultaEm(LocalDateTime.now())
                                        .status(CreditAnalysisStatus.CONCLUIDO)
                                        .inquiryHistory(objectMapper.createObjectNode())
                                        .negativeSummary(objectMapper.createObjectNode())
                                        .partnerDetails(objectMapper.createObjectNode())
                                        .build();
                        creditAnalysisRepository.save(analysis1);

                        Client client2 = Client.builder()
                                        .documentNumber("98765432000110")
                                        .name("Comércio de Alimentos Silva Ltda")
                                        .build();
                        clientRepository.save(client2);

                        // Empresa 2: Risco Moderado
                        CompanyDetail company2 = CompanyDetail.builder()
                                        .documentNumber("98765432000110")
                                        .companyName("Comércio de Alimentos Silva Ltda")
                                        .alias("Silva Alimentos")
                                        .founded(LocalDate.of(2018, 5, 20))
                                        .statusText("Ativa")
                                        .city("Campinas")
                                        .state("SP")
                                        .companyEquity(new BigDecimal("50000.00"))
                                        .build();
                        companyDetailRepository.save(company2);

                        var negSummaryC2 = objectMapper.createObjectNode();
                        negSummaryC2.put("protestoCount", 2);
                        negSummaryC2.put("protestoBalance", 12450.0);

                        CreditAnalysis analysis2 = CreditAnalysis.builder()
                                        .clientId(client2.getId())
                                        .cnpj("98765432000110")
                                        .companyName("Comércio de Alimentos Silva Ltda")
                                        .score(450)
                                        .riskClass("Moderado")
                                        .probability(new BigDecimal("0.18"))
                                        .analysisDate(LocalDateTime.now().minusDays(2))
                                        .consultaEm(LocalDateTime.now().minusDays(2))
                                        .status(CreditAnalysisStatus.CONCLUIDO)
                                        .inquiryHistory(objectMapper.createObjectNode())
                                        .negativeSummary(negSummaryC2)
                                        .partnerDetails(objectMapper.createObjectNode())
                                        .build();
                        creditAnalysisRepository.save(analysis2);

                        log.info("Empresas e análises criadas com sucesso.");
                }
        }
}
