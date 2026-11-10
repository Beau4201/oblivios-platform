// SPDX-License-Identifier: Apache-2.0
// Host test of ExitMaskList (run.sh). No argument: the unit checks. `dump LIST NAMES`, `dumpq LIST NAMES` and
// `accept LIST` print what differential.py compares with the daemon's reader.

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include "ExitMaskList.h"

using android::net::ExitMaskList;

#define CHECK(cond)                                                            \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #cond); \
            std::abort();                                                      \
        }                                                                      \
    } while (0)

static const char* kSample =
        "exit-mask-list\t1\nads.example.com\t7\tExample Ads\nexample.net\t8\tExample Net\n"
        "cdn.example.net\t0\tprotected\ngoogle.com\t0\tprotected\nadservice.google.com\t9\tGoogle ads\n";

static ExitMaskList sample() {
    ExitMaskList l;
    CHECK(l.parse(kSample));
    return l;
}

// The wire form of a dotted name as a query (header, one question, A, IN); an empty vector when the name cannot be
// written (an empty label, a label of more than 63 bytes).
static std::vector<uint8_t> query(const std::string& dotted, int questions = 1) {
    std::vector<uint8_t> q = {0x12, 0x34, 0x01, 0x00, 0, static_cast<uint8_t>(questions), 0, 0, 0, 0, 0, 0};
    for (int i = 0; i < questions; ++i) {
        size_t start = 0;
        std::string name = dotted;
        if (!name.empty() && name.back() == '.') name.pop_back();
        if (!name.empty()) {
            for (;;) {
                size_t dot = name.find('.', start);
                std::string label = name.substr(start, dot == std::string::npos ? dot : dot - start);
                if (label.empty() || label.size() > 63) return {};
                q.push_back(static_cast<uint8_t>(label.size()));
                q.insert(q.end(), label.begin(), label.end());
                if (dot == std::string::npos) break;
                start = dot + 1;
            }
        }
        q.push_back(0);
        q.insert(q.end(), {0, 1, 0, 1});
    }
    return q;
}

static void names() {
    ExitMaskList l = sample();
    CHECK(l.size() == 5);
    CHECK(l.masksName("ads.example.com"));
    CHECK(l.masksName("x.y.ads.example.com"));
    CHECK(l.masksName("example.net"));
    CHECK(l.masksName("sub.example.net"));
    CHECK(!l.masksName("notexample.net"));  // a longer label is another domain
    CHECK(!l.masksName("xads.example.com"));
    CHECK(!l.masksName("example.com"));  // the parent of a row is not masked
    CHECK(!l.masksName("net"));
    CHECK(!l.masksName("com"));
    // the most specific row decides
    CHECK(!l.masksName("cdn.example.net"));
    CHECK(!l.masksName("a.cdn.example.net"));
    CHECK(l.masksName("www.example.net"));
    CHECK(!l.masksName("www.google.com"));
    CHECK(l.masksName("adservice.google.com"));
    CHECK(l.masksName("x.adservice.google.com"));
    CHECK(!l.masksName("accounts.google.com"));
    // case and one trailing dot
    CHECK(l.masksName("ADS.Example.COM"));
    CHECK(l.masksName("ads.example.com."));
    CHECK(!l.masksName("ads.example.com.."));
    // names that cannot be host names of the list are never masked
    CHECK(!l.masksName(""));
    CHECK(!l.masksName("."));
    CHECK(!l.masksName("a..ads.example.com"));
    CHECK(!l.masksName(".ads.example.com"));
    CHECK(!l.masksName("-ads.example.com"));
    CHECK(!l.masksName("ads_.example.com"));
    CHECK(!l.masksName(std::string("ads.example.com\0x", 16)));
    CHECK(!l.masksName("\xc3\xa9.ads.example.com"));
    CHECK(!l.masksName(std::string(64, 'a') + ".ads.example.com"));
    CHECK(l.masksName(std::string(63, 'a') + ".ads.example.com"));
    // 253 bytes is the longest name; a masked suffix does not rescue a longer one
    std::string longest = std::string(60, 'a') + "." + std::string(60, 'b') + "." + std::string(60, 'c') + "." +
                          std::string(60, 'd') + ".ads.example.com";  // 60*4 + 4 + 15 = 259
    CHECK(longest.size() > 253 && !l.masksName(longest));
    std::string fits = std::string(60, 'a') + "." + std::string(60, 'b') + "." + std::string(60, 'c') + "." +
                       std::string(40, 'd') + ".ads.example.com";  // 60*3 + 40 + 4 + 15 = 239
    CHECK(fits.size() <= 253 && l.masksName(fits));
}

static void lists() {
    ExitMaskList l;
    std::string error;
    CHECK(!l.parse("", &error) && !error.empty() && l.size() == 0);
    CHECK(!l.parse("exit-mask-list\t2\n", &error));
    CHECK(!l.parse("exit-mask-list 1\n", &error));
    CHECK(!l.parse("\nexit-mask-list\t1\n", &error));
    CHECK(l.parse("exit-mask-list\t1") && l.size() == 0);
    CHECK(l.parse("exit-mask-list\t1\n") && l.size() == 0);
    CHECK(l.parse("exit-mask-list\t1\n\n\na.com\t1\tA\n\n") && l.size() == 1);
    CHECK(l.parse("exit-mask-list\t1\na.com\t1\tA") && l.masksName("a.com"));  // no newline at the end
    // the name field is not read, a row of two fields is taken
    CHECK(l.parse("exit-mask-list\t1\na.com\t1\n") && l.masksName("a.com"));
    // a bad row refuses the whole file, and the list is then empty
    CHECK(l.parse(kSample));
    CHECK(!l.parse("exit-mask-list\t1\na.com\t1\tA\nA.com\t1\tA\n") && l.size() == 0 && !l.masksName("a.com"));
    CHECK(!l.parse("exit-mask-list\t1\na.com\t1\tA\nb.com\tx\tB\n"));
    CHECK(!l.parse("exit-mask-list\t1\na.com\t\tA\n"));
    // ids are 32 bits (ADR-223): 2^32 is refused and so is a digit string that would wrap a 32-bit sum
    CHECK(!l.parse("exit-mask-list\t1\na.com\t4294967296\tA\n"));
    CHECK(!l.parse("exit-mask-list\t1\na.com\t42949672960\tA\n"));
    CHECK(l.parse("exit-mask-list\t1\na.com\t65535\tA\n"));
    CHECK(l.parse("exit-mask-list\t1\na.com\t65536\tA\n") && l.masksName("a.com"));
    CHECK(l.parse("exit-mask-list\t1\na.com\t4294967295\tA\n") && l.masksName("a.com"));
    CHECK(l.parse("exit-mask-list\t1\na.com\t0000000000000000005\tA\n") && l.masksName("a.com"));
    CHECK(!l.parse("exit-mask-list\t1\na.com\t99999999999\tA\n"));
    CHECK(!l.parse("exit-mask-list\t1\na.com\n"));
    CHECK(!l.parse("exit-mask-list\t1\na.com\t1\tA\na.com\t2\tB\n"));  // a domain twice
    CHECK(!l.parse("exit-mask-list\t1\n-a.com\t1\tA\n"));
    CHECK(!l.parse("exit-mask-list\t1\na..com\t1\tA\n"));
    CHECK(!l.parse("exit-mask-list\t1\na.com.\t1\tA\n"));
    CHECK(!l.parse("exit-mask-list\t1\nA.com\t1\tA\n"));
    CHECK(!l.parse("exit-mask-list\t1\na-.com\t1\tA\n"));
    CHECK(!l.parse("exit-mask-list\t1\n123\n"));  // a row without a tab is not a domain with a company id
    CHECK(!l.parse("exit-mask-list\t1\n\t1\tA\n"));  // an empty domain
    // the characters of a label, at both ends of each range, as a row and as a name
    CHECK(l.parse("exit-mask-list\t1\naz-09.zz-z.com\t5\tx\n"));
    CHECK(l.masksName("az-09.zz-z.com") && l.masksName("AZ-09.ZZ-Z.COM") && l.masksName("x.Az-09.zZ-z.com"));
    for (const char* bad : {"`z.", "{z.", "/9.", ":9.", "[z.", "@z.", "_z.", "x-.", "-x.", ".", "z_."}) {
        CHECK(!l.masksName(std::string(bad) + "az-09.zz-z.com"));
    }
    // a domain of 253 bytes is a row, one of 254 is not
    const std::string d253 = std::string(63, 'a') + "." + std::string(63, 'b') + "." + std::string(63, 'c') + "." + std::string(61, 'd');
    CHECK(d253.size() == 253);
    CHECK(l.parse("exit-mask-list\t1\n" + d253 + "\t1\tx\n") && l.masksName(d253) && !l.masksName("x." + d253));  // the name is then 255 bytes
    CHECK(!l.parse("exit-mask-list\t1\n" + d253 + "d\t1\tx\n"));
    // the number of rows
    std::string many = "exit-mask-list\t1\n";
    for (size_t i = 0; i < ExitMaskList::kMaxRows; ++i) many += "d" + std::to_string(i) + ".com\t1\tx\n";
    CHECK(l.parse(many) && l.size() == ExitMaskList::kMaxRows);
    many += "over.com\t1\tx\n";
    CHECK(!l.parse(many));
    // a file that is missing or too long is not a list
    CHECK(!l.load("/nonexistent/exit-mask-list.tsv", &error) && l.size() == 0);
    {
        // the longest file is exactly kMaxFile bytes (the daemon's `MAX_FILE`); one byte more is refused
        const std::string header = "exit-mask-list\t1\n";
        const char* path = "/tmp/exit-mask-list-test-long.tsv";
        std::ofstream(path) << header << std::string(ExitMaskList::kMaxFile - header.size(), '\n');
        CHECK(l.load(path, &error) && l.size() == 0);
        std::ofstream(path) << header << std::string(ExitMaskList::kMaxFile - header.size() + 1, '\n');
        CHECK(!l.load(path, &error) && l.size() == 0 && !error.empty());
        std::remove(path);
    }
}

static void queries() {
    ExitMaskList l = sample();
    CHECK(l.masksQuery(query("ads.example.com")));
    CHECK(l.masksQuery(query("WWW.Example.NET")));
    CHECK(!l.masksQuery(query("www.example.org")));
    CHECK(!l.masksQuery(query("cdn.example.net")));
    CHECK(!l.masksQuery(query("")));  // the root
    // any question of the packet
    std::vector<uint8_t> two = query("www.example.org", 1);
    std::vector<uint8_t> second = query("ads.example.com", 1);
    two[5] = 2;
    two.insert(two.end(), second.begin() + 12, second.end());
    CHECK(l.masksQuery(two));
    // malformed packets are never masked. Each is copied into a vector of exactly its size, so that AddressSanitizer
    // sees a read past the end of the packet.
    auto exact = [](const std::vector<uint8_t>& v, size_t n) { return std::vector<uint8_t>(v.begin(), v.begin() + n); };
    std::vector<uint8_t> q = query("ads.example.com");
    for (size_t cut = 0; cut < q.size(); ++cut) {  // cut inside the header, the name, or the tail
        CHECK(!l.masksQuery(exact(q, cut)));
    }
    CHECK(l.masksQuery(exact(q, q.size())));
    std::vector<uint8_t> pointer = q;
    pointer[12] = 0xc0;  // a compression pointer in the QNAME
    CHECK(!l.masksQuery(exact(pointer, pointer.size())));
    std::vector<uint8_t> count = q;
    count[5] = 3;  // more questions than the packet holds: the first one still decides
    CHECK(l.masksQuery(exact(count, count.size())));
    count[4] = 1;
    count[5] = 0;  // 256 questions: the high byte counts
    CHECK(l.masksQuery(exact(count, count.size())));
    std::vector<uint8_t> unlisted = query("www.example.org");
    unlisted[5] = 3;  // the second question is missing, so nothing is masked
    CHECK(!l.masksQuery(exact(unlisted, unlisted.size())));
    count[4] = 0;
    count[5] = 0;
    CHECK(!l.masksQuery(exact(count, count.size())));
    std::vector<uint8_t> label = q;
    label[12] = 200;  // a label that runs past the end
    CHECK(!l.masksQuery(exact(label, label.size())));
    // a label of 64 bytes is not a label: the packet is malformed and a masked second question is not looked at
    std::vector<uint8_t> bad = {0x12, 0x34, 0x01, 0x00, 0, 2, 0, 0, 0, 0, 0, 0, 64};
    bad.insert(bad.end(), 64, 'a');
    bad.insert(bad.end(), {0, 0, 1, 0, 1});
    std::vector<uint8_t> masked = query("ads.example.com");
    bad.insert(bad.end(), masked.begin() + 12, masked.end());
    CHECK(!l.masksQuery(exact(bad, bad.size())));
    bad[12] = 63;  // the same packet with a label of 63 bytes and one byte too few: still malformed
    CHECK(!l.masksQuery(exact(bad, bad.size())));
    // the longest name is 253 bytes, as text and as a query
    std::string p253 = std::string(63, 'a') + "." + std::string(63, 'b') + "." + std::string(63, 'c') + "." + std::string(45, 'd');
    CHECK((p253 + ".ads.example.com").size() == 253);
    CHECK(l.masksName(p253 + ".ads.example.com") && !l.masksName(p253 + "d.ads.example.com"));
    CHECK(l.masksQuery(query(p253 + ".ads.example.com")));
    CHECK(!l.masksQuery(query(p253 + "d.ads.example.com")));
    // a label in the middle that does not fit must not be left out: the whole name is longer than 253 bytes, so it is
    // not masked, although what is left of it ends in a masked domain
    const std::string skipped = std::string(63, 'x') + "." + std::string(63, 'x') + "." + std::string(63, 'x') + "." +
                                std::string(63, 'y') + ".ads.example.com";
    CHECK(skipped.size() > 253 && !l.masksQuery(query(skipped)) && !l.masksName(skipped));
    // a label with a dot byte or an upper-case letter in it is read as written
    CHECK(l.masksQuery(query("ADS.EXAMPLE.COM")));
    // an empty list masks nothing and costs nothing
    ExitMaskList empty;
    CHECK(!empty.masksQuery(query("ads.example.com")));
    // names longer than 253 bytes on the wire (many short labels) are never masked
    std::string longName;
    for (int i = 0; i < 90; ++i) longName += "ab.";
    longName += "ads.example.com";
    CHECK(longName.size() > 253 && !l.masksQuery(query(longName)));
    std::string shorter;
    for (int i = 0; i < 60; ++i) shorter += "ab.";
    shorter += "ads.example.com";
    CHECK(shorter.size() <= 253 && l.masksQuery(query(shorter)));
}

static std::vector<std::string> lines(const char* path) {
    std::ifstream f(path);
    std::vector<std::string> out;
    for (std::string s; std::getline(f, s);) out.push_back(s);
    return out;
}

int main(int argc, char** argv) {
    if (argc == 1) {
        names();
        lists();
        queries();
        std::puts("ExitMaskList host test: ok");
        return 0;
    }
    std::string mode = argv[1];
    ExitMaskList l;
    std::ifstream in(argv[2], std::ios::binary);
    std::stringstream text;
    text << in.rdbuf();
    if (mode == "accept") {
        std::puts(l.parse(text.str()) ? "ok" : "err");
        return 0;
    }
    CHECK(l.parse(text.str()));
    for (const std::string& name : lines(argv[3])) {
        if (mode == "dump") {
            std::puts(l.masksName(name) ? "1" : "0");
        } else {  // dumpq
            std::vector<uint8_t> q = query(name);
            std::puts(q.empty() ? "x" : (l.masksQuery(q) ? "1" : "0"));
        }
    }
    return 0;
}
