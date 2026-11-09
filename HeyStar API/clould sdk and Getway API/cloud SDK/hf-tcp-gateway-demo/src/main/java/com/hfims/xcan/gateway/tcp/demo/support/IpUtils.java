package com.hfims.xcan.gateway.tcp.demo.support;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.regex.Pattern;

/**
 * IP地址相关工具
 */
public final class IpUtils {

    private static final String IP = "((2[0-4]\\d|25[0-5]|[01]?\\d\\d?)\\.){3}(2[0-4]\\d|25[0-5]|[01]?\\d\\d?)";

    /**
     * 获取ip地址
     *
     * @param useIPv4 是否ipv4
     */
    public static String getIPAddress(final boolean useIPv4) {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            LinkedList<InetAddress> adds = new LinkedList<>();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    adds.addFirst(addresses.nextElement());
                }
            }
            for (InetAddress add : adds) {
                if (!add.isLoopbackAddress()) {
                    String hostAddress = add.getHostAddress();
                    boolean isIPv4 = hostAddress.indexOf(':') < 0;
                    if (useIPv4) {
                        if (isIPv4) {
                            return hostAddress;
                        }
                    } else {
                        if (!isIPv4) {
                            int index = hostAddress.indexOf('%');
                            return index < 0
                                    ? hostAddress.toUpperCase()
                                    : hostAddress.substring(0, index).toUpperCase();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 获取ip地址
     */
    public static InetAddress getLocalIPAddress() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            LinkedList<InetAddress> adds = new LinkedList<>();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    adds.addFirst(addresses.nextElement());
                }
            }
            for (InetAddress add : adds) {
                if (!add.isLoopbackAddress()) {
                    String hostAddress = add.getHostAddress();
                    if (hostAddress != null && !hostAddress.isEmpty() && Pattern.matches(IP, hostAddress)) {
                        return add;
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 正确的IP拿法，即优先拿site-local地址
    // private static InetAddress getLocalHostLANAddress() throws UnknownHostException {
    //     try {
    //         Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
    //         InetAddress candidateAddress = null;
    //         // 遍历所有的网络接口
    //         while (nis.hasMoreElements()) {
    //             NetworkInterface ni = nis.nextElement();
    //             if (!ni.isUp() || ni.isLoopback()) {
    //                 continue;
    //             }
    //             Enumeration<InetAddress> addresses = ni.getInetAddresses();
    //
    //         }
    //         for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements(); ) {
    //             NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
    //             // 在所有的接口下再遍历IP
    //             for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements(); ) {
    //                 InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
    //                 if (!inetAddr.isLoopbackAddress()) {// 排除loopback类型地址
    //                     if (inetAddr.isSiteLocalAddress()) {
    //                         // 如果是site-local地址，就是它了
    //                         return inetAddr;
    //                     } else if (candidateAddress == null) {
    //                         // site-local类型的地址未被发现，先记录候选地址
    //                         candidateAddress = inetAddr;
    //                     }
    //                 }
    //             }
    //         }
    //         if (candidateAddress != null) {
    //             return candidateAddress;
    //         }
    //         // 如果没有发现 non-loopback地址.只能用最次选的方案
    //         InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
    //         if (jdkSuppliedAddress == null) {
    //             throw new UnknownHostException("The JDK InetAddress.getLocalHost() method unexpectedly returned null.");
    //         }
    //         return jdkSuppliedAddress;
    //     } catch (Exception e) {
    //         UnknownHostException unknownHostException = new UnknownHostException(
    //                 "Failed to determine LAN address: " + e);
    //         unknownHostException.initCause(e);
    //         throw unknownHostException;
    //     }
    // }
}
