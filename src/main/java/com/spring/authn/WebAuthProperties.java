package com.spring.authn;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "authn")
public class WebAuthProperties {

    private String hostName;
    private String display;
    private Set<String> origin;

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public Set<String> getOrigin() {
        return origin;
    }

    public void setOrigin(Set<String> origin) {
        this.origin = origin;
    }
}
