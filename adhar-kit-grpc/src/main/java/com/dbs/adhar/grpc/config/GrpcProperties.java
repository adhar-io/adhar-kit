package com.dbs.adhar.grpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adhar.grpc")
public class GrpcProperties {

    private int port = 50051;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}

