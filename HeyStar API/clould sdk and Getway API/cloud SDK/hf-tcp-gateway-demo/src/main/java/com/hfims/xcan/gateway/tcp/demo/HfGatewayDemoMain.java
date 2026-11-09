package com.hfims.xcan.gateway.tcp.demo;

import com.hfims.xcan.gateway.netty.HfGatewayBootstrap;
import com.hfims.xcan.gateway.netty.util.ThreadUtils;
import com.hfims.xcan.gateway.tcp.demo.config.HfGatewayDemoConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class HfGatewayDemoMain {

    public static void main(String[] args) {
        ThreadUtils.getSinglePool().execute(() -> HfGatewayBootstrap.start(HfGatewayDemoConfig.SDK_PORT));
        SpringApplication.run(HfGatewayDemoMain.class, args);
    }
}
