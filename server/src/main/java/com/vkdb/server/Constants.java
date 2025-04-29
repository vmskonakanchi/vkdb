package com.vkdb.server;

import java.nio.file.Path;

public class Constants {
    public static final Path APPEND_ONLY_LOG_FILE_PATH = Path.of("append-log.vdb");
    public static final long CACHE_CHECK_INTERVAL = 10 * 1000L; // cache check interval
    public static final long COMPACTION_INTERVAL = 200 * 1000L;  // compaction interval
    public static final Path USER_LIST_PATH = Path.of("users.vdb");
    public static final boolean IS_SYNCHRONOUS_REPLICATION = false;
}