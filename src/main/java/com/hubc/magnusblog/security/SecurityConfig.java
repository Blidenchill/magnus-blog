package com.hubc.magnusblog.security;

import com.hubc.magnusblog.config.AppProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            LoginRateLimitFilter loginRateLimitFilter,
                                            AuthAuditHandlers auditHandlers) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/login").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .successHandler(auditHandlers)
                .failureHandler(auditHandlers)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessHandler(auditHandlers)
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.changeSessionId()))
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers
                .contentTypeOptions(with -> {})
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(ref -> ref.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; img-src 'self'; script-src 'self'; "
                        + "style-src 'self' 'unsafe-inline'; font-src 'self'; "
                        + "form-action 'self'; frame-ancestors 'none'; base-uri 'self'")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Single fixed admin account. Username from config, password BCrypt hash from
     * the configtree secret `magnus_admin_password-hash`. Startup fails fast when
     * either is missing - there is no default password.
     */
    @Bean
    UserDetailsService userDetailsService(AppProperties properties,
                                          @Value("${magnus_admin_password-hash:}") String passwordHash) {
        String username = properties.admin().username();
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException(
                    "APP_ADMIN_USERNAME is not set; refusing to start without an admin username");
        }
        if (!StringUtils.hasText(passwordHash) || !passwordHash.startsWith("$2")) {
            throw new IllegalStateException(
                    "magnus_admin_password-hash secret is missing or not a BCrypt hash; refusing to start");
        }
        var user = User.withUsername(username)
                .password(passwordHash)
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user) {
            @Override
            public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String name)
                    throws UsernameNotFoundException {
                // Do not reveal whether the account exists: same exception either way.
                return super.loadUserByUsername(name);
            }
        };
    }
}
