package com.hfims.xcan.gateway.tcp.demo.web;

import com.hfims.xcan.gateway.netty.client.HfDeviceClient;
import com.hfims.xcan.gateway.netty.client.resp.HfDeviceResp;
import com.hfims.xcan.gateway.netty.error.CgiErrorException;
import com.hfims.xcan.gateway.tcp.demo.support.BaseResult;
import com.hfims.xcan.gateway.tcp.demo.support.ResultWrapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api")
@RestController
public class TestController extends BaseController {

    @RequestMapping(value = "/test")
    public BaseResult test() {
        return ResultWrapper.wrapSuccess();
    }

    @RequestMapping(value = "/test", method = {RequestMethod.GET, RequestMethod.POST})
    public BaseResult test(String deviceKey, String secret)
            throws CgiErrorException {
        validateCommon(deviceKey, secret);
        HfDeviceResp tdxSdkResp = HfDeviceClient.test(hostInfo, deviceKey, secret);
        return ResultWrapper.wrapTdxSdkResponse(tdxSdkResp);
    }

    @RequestMapping(value = "/get", method = {RequestMethod.GET, RequestMethod.POST})
    public BaseResult get(String deviceKey, String secret)
            throws CgiErrorException {
        validateCommon(deviceKey, secret);
        HfDeviceResp tdxSdkResp = HfDeviceClient.deviceGet(hostInfo, deviceKey, secret);
        return ResultWrapper.wrapTdxSdkResponse(tdxSdkResp);
    }

    @RequestMapping(value = "/reboot", method = RequestMethod.POST)
    public BaseResult reboot(String deviceKey, String secret)
            throws CgiErrorException {
        validateCommon(deviceKey, secret);
        HfDeviceResp tdxSdkResp = HfDeviceClient.deviceReboot(hostInfo, deviceKey, secret);
        return ResultWrapper.wrapTdxSdkResponse(tdxSdkResp);
    }
}
