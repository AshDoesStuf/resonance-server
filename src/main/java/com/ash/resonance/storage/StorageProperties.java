package com.ash.resonance.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "resonance.storage")
public class StorageProperties {

    /** Root directory. music/, uploads/ live under here. Created on startup if missing. */
    private String root = "./data";

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }
}
