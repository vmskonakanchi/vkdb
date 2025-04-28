package com.vkdb.server;

public class AuthUser {
    private String username;
    private String password;
    private long lastLoginTime;

    public AuthUser(String username, String password) {
        this.password = password;
        this.username = username;
        this.lastLoginTime = System.currentTimeMillis();
    }

    public AuthUser(String username, String password, long lastLoginTime) {
        this.password = password;
        this.username = username;
        this.lastLoginTime = lastLoginTime;
    }

    public long getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(long lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void updateLastLoginTime() {
        this.lastLoginTime = System.currentTimeMillis();
    }
}
