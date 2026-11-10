// SPDX-License-Identifier: Apache-2.0
// See ExitMaskList.h. The format and the matching are those of `masklist.rs` of the exit daemon (oblivios-services,
// exit-daemon); `resolver/test/differential.py` of oblivios-platform compares the two on the real list.

#include "ExitMaskList.h"

#include <algorithm>
#include <cstdio>
#include <utility>

namespace android::net {

namespace {

constexpr std::string_view kHeader = "exit-mask-list\t1";
constexpr size_t kMaxDomain = 253;
constexpr size_t kMaxLabel = 63;
constexpr size_t kDnsHeader = 12;

bool isLabelChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-';
}

// Lower-case letters, digits and hyphens in labels of 1 to 63 bytes without a hyphen at either end, joined by dots.
bool isValidDomain(std::string_view d) {
    if (d.empty() || d.size() > kMaxDomain) return false;
    size_t start = 0;
    for (;;) {
        const size_t dot = d.find('.', start);
        const std::string_view label = d.substr(start, dot == std::string_view::npos ? dot : dot - start);
        if (label.empty() || label.size() > kMaxLabel || label.front() == '-' || label.back() == '-') return false;
        if (!std::all_of(label.begin(), label.end(), isLabelChar)) return false;
        if (dot == std::string_view::npos) return true;
        start = dot + 1;
    }
}

// A company id: digits only, at most 4294967295 (32 bits, ADR-223). The sum is 64 bits wide so that no digit string wraps.
bool parseCompany(std::string_view s, uint32_t* out) {
    if (s.empty()) return false;
    uint64_t v = 0;
    for (char c : s) {
        if (c < '0' || c > '9') return false;
        v = v * 10 + static_cast<uint64_t>(c - '0');
        if (v > 0xffffffffULL) return false;
    }
    *out = static_cast<uint32_t>(v);
    return true;
}

}  // namespace

bool ExitMaskList::parse(std::string_view text, std::string* error) {
    mDomains.clear();
    mRows.clear();
    auto fail = [&](const char* why) {
        if (error) *error = why;
        return false;
    };

    size_t pos = text.find('\n');
    if (text.substr(0, pos) != kHeader) return fail("not a mask list (header)");
    pos = pos == std::string_view::npos ? text.size() : pos + 1;

    std::vector<std::pair<std::string_view, uint32_t>> rows;
    while (pos < text.size()) {
        size_t end = text.find('\n', pos);
        if (end == std::string_view::npos) end = text.size();
        const std::string_view line = text.substr(pos, end - pos);
        pos = end + 1;
        if (line.empty()) continue;

        const size_t tab = line.find('\t');
        if (tab == std::string_view::npos) return fail("a row without a company id");
        const std::string_view domain = line.substr(0, tab);
        std::string_view id = line.substr(tab + 1);
        id = id.substr(0, id.find('\t'));  // the company name that follows is not read
        uint32_t company;
        if (!isValidDomain(domain)) return fail("a row with a domain that is not valid");
        if (!parseCompany(id, &company)) return fail("a row with a company id that is not valid");
        if (rows.size() >= kMaxRows) return fail("too many rows");
        rows.emplace_back(domain, company);
    }

    std::sort(rows.begin(), rows.end());
    for (size_t i = 1; i < rows.size(); ++i) {
        if (rows[i].first == rows[i - 1].first) return fail("a domain twice");
    }
    for (const auto& [domain, company] : rows) {
        mRows.push_back(Row{static_cast<uint32_t>(mDomains.size()), static_cast<uint16_t>(domain.size()), company});
        mDomains.append(domain);
    }
    return true;
}

bool ExitMaskList::load(const char* path, std::string* error) {
    mDomains.clear();
    mRows.clear();
    FILE* file = fopen(path, "rb");
    if (file == nullptr) {
        if (error) *error = "the file cannot be opened";
        return false;
    }
    std::string text;
    char buf[8192];
    size_t n;
    while (text.size() <= kMaxFile && (n = fread(buf, 1, sizeof(buf), file)) > 0) text.append(buf, n);
    const bool failed = ferror(file) != 0;
    fclose(file);
    if (failed || text.size() > kMaxFile) {
        if (error) *error = failed ? "the file cannot be read" : "the file is too long";
        return false;
    }
    return parse(text, error);
}

const ExitMaskList::Row* ExitMaskList::find(std::string_view domain) const {
    auto it = std::lower_bound(mRows.begin(), mRows.end(), domain,
                               [this](const Row& row, std::string_view d) { return domainOf(row) < d; });
    return it != mRows.end() && domainOf(*it) == domain ? &*it : nullptr;
}

bool ExitMaskList::masksName(std::string_view name) const {
    if (!name.empty() && name.back() == '.') name.remove_suffix(1);
    if (name.empty() || name.size() > kMaxDomain) return false;
    char lower[kMaxDomain];
    for (size_t i = 0; i < name.size(); ++i) {
        const char c = name[i];
        lower[i] = (c >= 'A' && c <= 'Z') ? static_cast<char>(c | 0x20) : c;
    }
    std::string_view rest(lower, name.size());
    if (!isValidDomain(rest)) return false;
    for (;;) {
        if (const Row* row = find(rest)) return row->company != 0;
        const size_t dot = rest.find('.');
        if (dot == std::string_view::npos) return false;
        rest.remove_prefix(dot + 1);
    }
}

bool ExitMaskList::masksQuery(std::span<const uint8_t> query) const {
    if (mRows.empty() || query.size() < kDnsHeader) return false;
    size_t questions = (static_cast<size_t>(query[4]) << 8) | query[5];
    size_t pos = kDnsHeader;
    for (; questions > 0; --questions) {
        // The QNAME of a query has no compression pointer: labels of up to 63 bytes, then a zero byte.
        char name[kMaxDomain];
        size_t length = 0;
        bool tooLong = false;
        for (;;) {
            if (pos >= query.size()) return false;
            const size_t label = query[pos++];
            if (label == 0) break;
            if (label >= 64 || pos + label > query.size()) return false;
            if (length + label + (length > 0 ? 1 : 0) > kMaxDomain) {
                tooLong = true;
            } else {
                if (length > 0) name[length++] = '.';
                for (size_t i = 0; i < label; ++i) name[length++] = static_cast<char>(query[pos + i]);
            }
            pos += label;
        }
        pos += 4;  // QTYPE and QCLASS
        if (pos > query.size()) return false;
        if (!tooLong && masksName(std::string_view(name, length))) return true;
    }
    return false;
}

}  // namespace android::net
