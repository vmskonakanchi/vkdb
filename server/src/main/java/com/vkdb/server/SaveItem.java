package com.vkdb.server;

public class SaveItem {
    private final String key;
    private final String value;
    private String operation;
    private final Long ttl;

    public SaveItem(String key, String value, String operation, Long ttl) {
        this.key = key;
        this.value = value;
        this.operation = operation;
        this.ttl = System.currentTimeMillis() + ttl;
    }

    public SaveItem(String key, String value, String operation) {
        this.key = key;
        this.value = value;
        this.operation = operation;
        this.ttl = null;
    }

    public boolean hasExpired() {
        // check the ttl
        return this.ttl != null && System.currentTimeMillis() > this.ttl;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Long getTtl() {
        return ttl;
    }

    public String getValue() {
        return value;
    }

    public String getKey() {
        return key;
    }

    private String getOperation() {
        return operation;
    }

    @Override
    public String toString() {
        return this.operation + "=" + this.key + "=" + this.value + System.lineSeparator();
    }

    public String toSend() {
        return this.key + " " + this.value + " " + this.ttl;
    }
}
