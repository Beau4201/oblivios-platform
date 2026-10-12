// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

public final class HostFilterCheck {
    private static void local(String key) {
        if (!HostFilter.isLocal(key)) throw new AssertionError("should be local: " + key);
    }

    private static void remote(String key) {
        if (HostFilter.isLocal(key)) throw new AssertionError("should not be local: " + key);
    }

    public static void main(String[] args) {
        local("localhost");
        local("foo.localhost");
        local("printer");
        local("router.lan");
        local("nas.local");
        local("x.home.arpa");
        local("a.b.internal");
        local("ip:127.0.0.1");
        local("ip:127.255.255.254");
        local("ip:0.0.0.0");
        local("ip:10.1.2.3");
        local("ip:172.16.0.1");
        local("ip:172.31.255.255");
        local("ip:192.168.1.1");
        local("ip:169.254.1.1");
        local("ip:100.64.0.1");
        local("ip:100.127.255.255");
        local("ip:::1");
        local("ip:::");
        local("ip:fe80::1");
        local("ip:fe90::1");
        local("ip:fea0::1");
        local("ip:febf::1");
        local("ip:fc00::1");
        local("ip:fd12:3456::1");
        remote("example.com");
        remote("google.com");
        remote("notlocal.com");
        remote("locally.example");
        remote("a.xlocal");
        remote("a.xinternal");
        remote("a.xlan");
        remote("ip:169.253.0.1");
        remote("ip:169.255.0.1");
        remote("ip:10.1.1.300");
        remote("ip:10.1.1.0001");
        remote("ip:10.1.1.");
        remote("ip:203.0.113.7");
        remote("ip:172.15.0.1");
        remote("ip:172.32.0.1");
        remote("ip:100.63.255.255");
        remote("ip:100.128.0.1");
        remote("ip:192.169.0.1");
        remote("ip:11.0.0.1");
        remote("ip:2001:db8::1");
        remote("ip:fec0::1");
        remote("ip:");
        remote("ip:1.2.3");
        remote("ip:1.2.3.256");
        remote("ip:1.2.3.4.5");
        remote("ip:a.b.c.d");
        remote("");
        remote(null);
        System.out.println("PASS HostFilterCheck");
    }
}
