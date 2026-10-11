// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

public final class ServiceKeysCheck {
    public static void main(String[] args) throws Exception {
        Classifier c = Classifier.parse(ClassifierCheck.FIXTURE);
        Check.eq("doubleclick.net", ServiceKeys.keyFor("ads.g.doubleclick.net", c), "the classifier suffix is the key");
        Check.eq("facebook.com", ServiceKeys.keyFor("graph.facebook.com", c), "a subdomain of a listed service");
        Check.eq("example.org", ServiceKeys.keyFor("cdn.eu.example.org", c), "an unknown host falls back to two labels");
        Check.eq("example.org", ServiceKeys.keyFor("example.org", c), "two labels stay");
        Check.eq("localhost", ServiceKeys.keyFor("localhost", c), "one label stays");
        Check.eq("bbc.co.uk", ServiceKeys.keyFor("news.bbc.co.uk", c), "a two-level suffix keeps three labels");
        Check.eq("bbc.co.uk", ServiceKeys.keyFor("bbc.co.uk", c), "already three labels");
        Check.eq("shop.com.au", ServiceKeys.keyFor("www.shop.com.au", c), "com.au");
        Check.eq("notfacebook.com", ServiceKeys.keyFor("notfacebook.com", c), "a name that only ends the same is not a match");
        Check.eq("a.com", ServiceKeys.keyFor("a.com", null), "no classifier");

        Check.eq("ip:203.0.113.7", ServiceKeys.ipKey("203.0.113.7"), "ipv4");
        Check.eq("ip:2001:db8::1", ServiceKeys.ipKey("2001:DB8::1"), "ipv6 in lower case");
        Check.eq(null, ServiceKeys.ipKey("evil host"), "not an address");
        Check.eq(null, ServiceKeys.ipKey(""), "empty");
        Check.eq(null, ServiceKeys.ipKey(null), "null");
        Check.eq(null, ServiceKeys.ipKey("1".repeat(46)), "too long");
        Check.passed("ServiceKeysCheck");
    }
}
