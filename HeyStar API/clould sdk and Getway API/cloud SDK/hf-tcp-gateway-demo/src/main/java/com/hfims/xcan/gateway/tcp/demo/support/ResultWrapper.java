package com.hfims.xcan.gateway.tcp.demo.support;

import com.hfims.xcan.gateway.netty.client.resp.HfDeviceResp;
import com.hfims.xcan.gateway.netty.error.CgiErrorEnum;

/**
 * 返回结果包装器
 * Created by Cosmo(87292008@qq.com) on 2018/6/11.
 */
public class ResultWrapper {

    public static BaseResult wrapSuccess() {
        return new BaseResult(CgiErrorEnum.CODE_000.getCode(), CgiErrorEnum.CODE_000.getMsg());
    }

    public static <T> DataResult<T> wrapSuccess(T data) {
        return new DataResult<>(CgiErrorEnum.CODE_000.getCode(), CgiErrorEnum.CODE_000.getMsg(), data);
    }

    public static BaseResult wrapFailure(String code, String msg) {
        return new BaseResult(code, msg);
    }

    public static BaseResult wrapFailure(CgiErrorEnum errorCode) {
        return new BaseResult(errorCode.getCode(), errorCode.getMsg());
    }

    public static <T> DataResult<T> wrapFailure(CgiErrorEnum errorCode, T data) {
        return new DataResult<>(errorCode, data);
    }

    public static <T> DataResult<T> wrapFailure(String code, String msg, T data) {
        return new DataResult<>(code, msg, data);
    }

    public static BaseResult wrapTdxSdkResponse(HfDeviceResp tdxSdkResp) {
        if (null == tdxSdkResp) {
            return wrapFailure(CgiErrorEnum.CODE_3504.getCode(), CgiErrorEnum.CODE_3504.getMsg());
        }
        if (!tdxSdkResp.success()) {
            return wrapFailure(tdxSdkResp.getCode(), tdxSdkResp.getMsg());
        }
        return wrapSuccess(tdxSdkResp.getData());
    }
}
