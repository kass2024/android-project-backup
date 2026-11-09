package com.hfims.xcan.gateway.tcp.demo.web;

import com.hfims.xcan.gateway.netty.client.dto.HostInfoDto;
import com.hfims.xcan.gateway.netty.error.CgiErrorEnum;
import com.hfims.xcan.gateway.netty.error.CgiErrorException;
import com.hfims.xcan.gateway.tcp.demo.support.IpUtils;
import com.hfims.xcan.gateway.netty.util.StringUtils;
import com.hfims.xcan.gateway.tcp.demo.config.HfGatewayDemoConfig;

public abstract class BaseController {

    final HostInfoDto hostInfo = new HostInfoDto(IpUtils.getIPAddress(true), HfGatewayDemoConfig.SDK_PORT, HfGatewayDemoConfig.TIMEOUT);

    protected void validateCommon(String deviceKey, String secret)
            throws CgiErrorException {
        if (StringUtils.isBlank(deviceKey) || deviceKey.length() < 16) {
            throw new CgiErrorException(CgiErrorEnum.CODE_1000);
        }
        if (StringUtils.isBlank(secret)) {
            throw new CgiErrorException(CgiErrorEnum.CODE_1000);
        }
    }
}
