package com.zeroverse.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.cookie")
@Getter
@Setter
public class AuthCookieProperties {
    private String name;
    private String path;
    private Boolean secure;
    private String sameSite;
    private Integer maxAge;
}
