package com.coffeepilot.api.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EmailQuotaProperties.class)
public class MailConfig {
}
