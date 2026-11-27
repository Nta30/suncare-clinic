package com.suncare.clinic.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Cần đăng nhập lại.\"}");
                }).accessDeniedHandler((request, response, error) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"ACCESS_DENIED\",\"message\":\"Bạn không có quyền thực hiện thao tác này.\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/accounts/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/cashier/invoices", "/api/cashier/invoices/*", "/api/cashier/invoices/*/detail").hasRole("CASHIER")
                        .requestMatchers(HttpMethod.POST, "/api/cashier/invoices/*/cash-payments").hasRole("CASHIER")
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers("/api/accounts/profile").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/admin/staff").hasAuthority("ACCOUNT_MANAGE")
                        .requestMatchers("/api/admin/staff/**").hasAuthority("ACCOUNT_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/admin/staff").hasAuthority("ACCOUNT_MANAGE")
                        .requestMatchers("/api/admin/staff-assignments", "/api/admin/staff-assignments/**").hasAuthority("ACCOUNT_MANAGE")
                        .requestMatchers("/api/admin/organization", "/api/admin/organization/**").hasAuthority("CATALOG_MANAGE")
                        .requestMatchers("/api/admin/services", "/api/admin/services/**").hasAuthority("CATALOG_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/admin/drugs").hasAuthority("CATALOG_MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/admin/drugs").hasAuthority("CATALOG_MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/drugs/*").hasAuthority("CATALOG_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/admin/allergens").hasAuthority("CATALOG_MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/admin/allergens").hasAuthority("CATALOG_MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/allergens/*").hasAuthority("CATALOG_MANAGE")
                        .requestMatchers("/api/admin/scheduling", "/api/admin/scheduling/**").hasAuthority("CATALOG_MANAGE")
                        .requestMatchers("/api/appointments/onsite/**").hasRole("PATIENT")
                        .requestMatchers(HttpMethod.GET, "/api/patient/appointments", "/api/patient/appointments/**").hasRole("PATIENT")
                        .requestMatchers(HttpMethod.GET, "/api/doctor/appointments", "/api/doctor/appointments/**").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/doctor/encounters/*/start", "/api/doctor/encounters/*/complete").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/doctor/encounters", "/api/doctor/encounters/*").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/doctor/encounters/*/histories", "/api/doctor/encounters/*/allergies").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/doctor/encounters/*/histories/*", "/api/doctor/encounters/*/allergies/*").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/doctor/encounters/*/clinical").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/doctor/symptoms").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/doctor/drugs", "/api/doctor/encounters/*/prescriptions").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/doctor/encounters/*/prescriptions", "/api/doctor/encounters/*/prescriptions/*/issue").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/doctor/encounters/*/prescriptions/*").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/doctor/encounters/*/prescriptions/*").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.GET, "/api/doctor/encounters/*/history", "/api/doctor/encounters/*/history/*", "/api/doctor/diagnosis-concepts", "/api/doctor/diagnosis-concepts/search").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/doctor/encounters/*/clinical/notes", "/api/doctor/encounters/*/clinical/diagnoses/*").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/doctor/encounters/*/clinical/vitals", "/api/doctor/encounters/*/clinical/diagnoses").hasRole("DOCTOR")
                        .requestMatchers("/api/head/schedules", "/api/head/schedules/**").hasAuthority("SCHEDULE_PUBLISH_DEPARTMENT")
                        .requestMatchers(HttpMethod.GET, "/api/doctor/encounters/*/invoices", "/api/doctor/encounters/*/billing-summary").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/doctor/encounters/*/invoices", "/api/doctor/encounters/*/invoices/*/issue").hasRole("DOCTOR")
                        .anyRequest().denyAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
