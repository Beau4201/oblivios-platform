// SPDX-License-Identifier: Apache-2.0
// The mask list of the Compartments exit service (ADR-181, ADR-186) as the resolver needs it.
//
// The exit daemon answers the DNS queries of an app of class Masked itself for the names on its list, with a fake
// address that leads into Tor. The answer cache of the resolver is shared by every uid of a network and a hit sends no
// packet, so a real answer that another uid cached would reach the masked app without the daemon seeing the query, and
// a fake answer would reach an app that is not masked (NET-014). The resolver therefore never serves or stores an answer
// for a name of this list.
//
// The list file is the one that the daemon reads (`--mask-list`, `masklist.rs`); this class reads the same format and
// matches by the same rules: a name is masked when the row of the longest domain that the name is, or is below, is not
// protected (company id 0). A file that is missing or not a list leaves the list empty, as it leaves the daemon without
// masking (the daemon does not start without it), so the cache then stays as it is.

#pragma once

#include <cstddef>
#include <cstdint>
#include <span>
#include <string>
#include <string_view>
#include <vector>

namespace android::net {

class ExitMaskList {
  public:
    static constexpr const char* kPath = "/system/etc/oblivios/exit-mask-list.tsv";
    // The limits of the daemon's reader (`masklist.rs`): rows and bytes of the file.
    static constexpr size_t kMaxRows = 262144;
    static constexpr size_t kMaxFile = 16 * 1024 * 1024;

    // Parses the text of a list file. On success the list holds its rows; on failure it is empty and `error` (when not
    // null) says why. Every file that the daemon accepts is accepted; the name field of a row is not read.
    bool parse(std::string_view text, std::string* error = nullptr);

    // Reads and parses the file at `path`: at most kMaxFile bytes, never a partial file.
    bool load(const char* path, std::string* error = nullptr);

    // True when the daemon masks `name` (a dotted name, one trailing dot allowed, any case) for a masked app. The same
    // answer as `MaskList::classify(name).is_some()` of the daemon.
    bool masksName(std::string_view name) const;

    // True when one of the questions of a DNS query packet is a masked name. A packet that is not a well-formed query is
    // not masked: the cache does not take it either.
    bool masksQuery(std::span<const uint8_t> query) const;

    size_t size() const { return mRows.size(); }

  private:
    struct Row {
        uint32_t offset;  // of the domain in mDomains
        uint16_t length;
        uint32_t company;  // 0: protected
    };

    std::string_view domainOf(const Row& row) const {
        return std::string_view(mDomains).substr(row.offset, row.length);
    }
    const Row* find(std::string_view domain) const;

    std::string mDomains;    // every domain, back to back
    std::vector<Row> mRows;  // sorted by domain
};

}  // namespace android::net
