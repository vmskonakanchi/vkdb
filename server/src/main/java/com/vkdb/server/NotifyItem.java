package com.vkdb.server;

import java.util.Set;

public class NotifyItem {
    private final Set<SocketItem> socketItems;
    private String key;
    private String value;

    public NotifyItem(Set<SocketItem> socketItems) {
        this.socketItems = socketItems;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Set<SocketItem> getSocketItems() {
        return socketItems;
    }
}
