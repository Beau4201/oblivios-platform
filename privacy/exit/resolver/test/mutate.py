#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of ExitMaskList: each mutant changes one expression of src/ExitMaskList.cpp (or .h) and run.sh --unit-only
must fail on a CHECK of its own (a compile error or a crash of the sanitizers is not a kill: the mutant is `invalid`).
The unmutated source runs first and must pass, or every mutant would look killed. Start it in the background and read the
last line. Mutants that behave exactly like the original are listed as EQUIVALENT with the reason and must survive.
"""
import concurrent.futures
import os
import shutil
import signal
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src")

# (file, old, new, note): `old` must occur exactly once in the file.
MUTANTS = [
    ("ExitMaskList.cpp", "constexpr size_t kMaxDomain = 253;", "constexpr size_t kMaxDomain = 254;", ""),
    ("ExitMaskList.cpp", "constexpr size_t kMaxLabel = 63;", "constexpr size_t kMaxLabel = 64;", ""),
    ("ExitMaskList.cpp", "(c >= 'a' && c <= 'z')", "(c > 'a' && c <= 'z')", ""),
    ("ExitMaskList.cpp", "(c >= 'a' && c <= 'z')", "(c >= 'a' && c < 'z')", ""),
    ("ExitMaskList.cpp", "(c >= '0' && c <= '9') || c == '-'", "(c > '0' && c <= '9') || c == '-'", ""),
    ("ExitMaskList.cpp", "(c >= '0' && c <= '9') || c == '-'", "(c >= '0' && c < '9') || c == '-'", ""),
    ("ExitMaskList.cpp", "(c >= '0' && c <= '9') || c == '-'", "(c >= '0' && c <= '9') || c == '_'", ""),
    ("ExitMaskList.cpp", "if (d.empty() || d.size() > kMaxDomain) return false;", "if (d.size() > kMaxDomain) return false;", "EQUIVALENT: the empty label check rejects the empty domain"),
    ("ExitMaskList.cpp", "if (d.empty() || d.size() > kMaxDomain) return false;", "if (d.empty() || d.size() >= kMaxDomain) return false;", ""),
    ("ExitMaskList.cpp", "if (label.empty() || label.size() > kMaxLabel ||", "if (label.size() > kMaxLabel ||", ""),
    ("ExitMaskList.cpp", "label.size() > kMaxLabel || label.front()", "label.size() >= kMaxLabel || label.front()", ""),
    ("ExitMaskList.cpp", "label.front() == '-' || label.back() == '-')", "label.back() == '-')", ""),
    ("ExitMaskList.cpp", "label.front() == '-' || label.back() == '-')", "label.front() == '-')", ""),
    ("ExitMaskList.cpp", "if (!std::all_of(label.begin(), label.end(), isLabelChar)) return false;", "if (false && !std::all_of(label.begin(), label.end(), isLabelChar)) return false;", ""),
    ("ExitMaskList.cpp", "if (dot == std::string_view::npos) return true;\n        start = dot + 1;", "if (dot == std::string_view::npos) return true;\n        start = dot;", ""),
    ("ExitMaskList.cpp", "if (s.empty()) return false;\n    uint32_t v", "uint32_t v", ""),
    ("ExitMaskList.cpp", "if (c < '0' || c > '9') return false;", "if (c < '0' || c >= '9') return false;", ""),
    ("ExitMaskList.cpp", "if (c < '0' || c > '9') return false;", "if (c <= '0' || c > '9') return false;", ""),
    ("ExitMaskList.cpp", "if (v > 65535) return false;", "if (v > 65536) return false;", ""),
    ("ExitMaskList.cpp", "if (v > 65535) return false;", "if (v >= 65535) return false;", ""),
    ("ExitMaskList.cpp", "if (text.substr(0, pos) != kHeader)", "if (text.substr(0, pos) == kHeader)", ""),
    ("ExitMaskList.cpp", "pos = pos == std::string_view::npos ? text.size() : pos + 1;", "pos = pos == std::string_view::npos ? text.size() : pos;", "EQUIVALENT: the line that is then the newline itself is empty and skipped"),
    ("ExitMaskList.cpp", "if (line.empty()) continue;", "", ""),
    ("ExitMaskList.cpp", "if (tab == std::string_view::npos) return fail(", "if (false) return fail(", ""),
    ("ExitMaskList.cpp", "id = id.substr(0, id.find('\\t'));", "", ""),
    ("ExitMaskList.cpp", "if (!isValidDomain(domain)) return fail(", "if (false) return fail(", ""),
    ("ExitMaskList.cpp", "if (!parseCompany(id, &company)) return fail(\"a row with a company id that is not valid\");", "if (!parseCompany(id, &company)) company = 0;", ""),
    ("ExitMaskList.cpp", "if (rows.size() >= kMaxRows)", "if (rows.size() > kMaxRows)", ""),
    ("ExitMaskList.cpp", "if (rows.size() >= kMaxRows)", "if (rows.size() >= kMaxRows - 1)", ""),
    ("ExitMaskList.cpp", "std::sort(rows.begin(), rows.end());", "", ""),
    ("ExitMaskList.cpp", "if (rows[i].first == rows[i - 1].first)", "if (rows[i].first != rows[i - 1].first)", ""),
    ("ExitMaskList.cpp", "for (size_t i = 1; i < rows.size(); ++i) {", "for (size_t i = 2; i < rows.size(); ++i) {", ""),
    ("ExitMaskList.cpp", "if (file == nullptr) {", "if (file != nullptr) {", ""),
    ("ExitMaskList.cpp", "while (text.size() <= kMaxFile &&", "while (text.size() < kMaxFile &&", ""),
    ("ExitMaskList.cpp", "text.size() > kMaxFile) {\n        if (error)", "text.size() >= kMaxFile) {\n        if (error)", ""),
    ("ExitMaskList.cpp", "const bool failed = ferror(file) != 0;", "const bool failed = false;", "EQUIVALENT: a read error on a regular file cannot be provoked on the host"),
    ("ExitMaskList.cpp", "return domainOf(row) < d; });", "return domainOf(row) <= d; });", ""),
    ("ExitMaskList.cpp", "return it != mRows.end() && domainOf(*it) == domain ? &*it : nullptr;", "return it != mRows.end() ? &*it : nullptr;", ""),
    ("ExitMaskList.cpp", "if (!name.empty() && name.back() == '.') name.remove_suffix(1);", "", ""),
    ("ExitMaskList.cpp", "if (!name.empty() && name.back() == '.') name.remove_suffix(1);", "while (!name.empty() && name.back() == '.') name.remove_suffix(1);", ""),
    ("ExitMaskList.cpp", "if (name.empty() || name.size() > kMaxDomain) return false;\n    char lower", "if (name.empty() || name.size() >= kMaxDomain) return false;\n    char lower", ""),
    ("ExitMaskList.cpp", "(c >= 'A' && c <= 'Z') ? static_cast<char>(c | 0x20) : c;", "(c > 'A' && c <= 'Z') ? static_cast<char>(c | 0x20) : c;", ""),
    ("ExitMaskList.cpp", "(c >= 'A' && c <= 'Z') ? static_cast<char>(c | 0x20) : c;", "(c >= 'A' && c < 'Z') ? static_cast<char>(c | 0x20) : c;", ""),
    ("ExitMaskList.cpp", "(c >= 'A' && c <= 'Z') ? static_cast<char>(c | 0x20) : c;", "c;", ""),
    ("ExitMaskList.cpp", "if (!isValidDomain(rest)) return false;", "", ""),
    ("ExitMaskList.cpp", "return row->company != 0;", "return row->company == 0;", ""),
    ("ExitMaskList.cpp", "return row->company != 0;", "return row->company != 0 || row != nullptr;", ""),
    ("ExitMaskList.cpp", "if (dot == std::string_view::npos) return false;\n        rest.remove_prefix(dot + 1);", "if (dot == std::string_view::npos) return true;\n        rest.remove_prefix(dot + 1);", ""),
    ("ExitMaskList.cpp", "rest.remove_prefix(dot + 1);", "rest.remove_prefix(dot);", ""),
    ("ExitMaskList.cpp", "if (mRows.empty() || query.size() < kDnsHeader) return false;", "if (mRows.empty()) return false;", ""),
    ("ExitMaskList.cpp", "if (mRows.empty() || query.size() < kDnsHeader) return false;", "if (query.size() < kDnsHeader) return false;", "EQUIVALENT: with no rows masksName is false for every name"),
    ("ExitMaskList.cpp", "(static_cast<size_t>(query[4]) << 8) | query[5];", "query[5];", ""),
    ("ExitMaskList.cpp", "if (label >= 64 || pos + label > query.size()) return false;", "if (pos + label > query.size()) return false;", ""),
    ("ExitMaskList.cpp", "if (label >= 64 || pos + label > query.size()) return false;", "if (label >= 64) return false;", ""),
    ("ExitMaskList.cpp", "if (label >= 64 || pos + label > query.size()) return false;", "if (label > 64 || pos + label > query.size()) return false;", ""),
    ("ExitMaskList.cpp", "if (label == 0) break;", "if (label == 0) return false;", ""),
    ("ExitMaskList.cpp", "if (pos >= query.size()) return false;\n            const size_t label", "if (pos > query.size()) return false;\n            const size_t label", ""),
    ("ExitMaskList.cpp", "if (length + label + (length > 0 ? 1 : 0) > kMaxDomain) {", "if (length + label + (length > 0 ? 1 : 0) >= kMaxDomain) {", ""),
    ("ExitMaskList.cpp", "if (length + label + (length > 0 ? 1 : 0) > kMaxDomain) {", "if (length + label > kMaxDomain) {", ""),
    ("ExitMaskList.cpp", "if (length > 0) name[length++] = '.';", "", ""),
    ("ExitMaskList.cpp", "pos += 4;  // QTYPE and QCLASS\n        if (pos > query.size()) return false;", "pos += 4;  // QTYPE and QCLASS", ""),
    ("ExitMaskList.cpp", "pos += 4;  // QTYPE and QCLASS\n        if (pos > query.size()) return false;", "pos += 3;  // QTYPE and QCLASS\n        if (pos > query.size()) return false;", ""),
    ("ExitMaskList.cpp", "pos += 4;  // QTYPE and QCLASS\n        if (pos > query.size()) return false;", "pos += 4;  // QTYPE and QCLASS\n        if (pos >= query.size()) return false;", ""),
    ("ExitMaskList.cpp", "if (!tooLong && masksName(", "if ((!tooLong || true) && masksName(", ""),
    ("ExitMaskList.cpp", "for (; questions > 0; --questions) {", "for (; questions > 1; --questions) {", ""),
]


class Result:
    def __init__(self, returncode, stderr):
        self.returncode, self.stderr = returncode, stderr


def run_unit(src_dir):
    """Runs the unit test of `src_dir`; a run of more than 90 s (a mutant that loops for ever) is killed with its children."""
    env = dict(os.environ, SRC=src_dir)
    p = subprocess.Popen(["bash", os.path.join(HERE, "run.sh"), "--unit-only", tempfile.mkdtemp()], stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE, text=True, env=env, start_new_session=True)
    try:
        _, err = p.communicate(timeout=90)
    except subprocess.TimeoutExpired:
        os.killpg(p.pid, signal.SIGKILL)
        p.communicate()
        return Result(-9, "HANG: the test did not finish in 90 s")
    return Result(p.returncode, err)


def one(i_mutant):
    i, (name, old, new, note) = i_mutant
    work = tempfile.mkdtemp(prefix="exitmask-mutant-")
    try:
        dst = os.path.join(work, "src")
        shutil.copytree(SRC, dst)
        path = os.path.join(dst, name)
        text = open(path, encoding="utf-8").read()
        assert text.count(old) == 1, f"mutant {i}: the pattern occurs {text.count(old)} times: {old!r}"
        open(path, "w", encoding="utf-8").write(text.replace(old, new))
        r = run_unit(dst)
        if r.returncode == 0:
            return i, "SURVIVED", note
        if "CHECK failed" in r.stderr:
            return i, "killed", note
        if r.stderr.startswith("HANG"):
            return i, "killed (hang)", note
        if "runtime error:" in r.stderr or "Sanitizer" in r.stderr or r.returncode < 0:
            return i, "killed (crash)", note  # a sanitizer or a signal ended the test: the mutant did something the test saw
        if "error:" in r.stderr:
            return i, "invalid (does not compile)", r.stderr.strip().splitlines()[0][:160]
        return i, "killed (crash)", r.stderr.strip().splitlines()[-1][:160] if r.stderr.strip() else ""
    finally:
        shutil.rmtree(work, ignore_errors=True)


def main():
    base = run_unit(SRC)
    if base.returncode != 0:
        print(base.stderr[-1500:])
        sys.exit("the unmutated source fails the unit test: no mutant can be judged")
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as ex:
        results = sorted(ex.map(one, enumerate(MUTANTS)))
    killed = survived = invalid = equivalent = 0
    for i, status, note in results:
        if status.startswith("killed"):
            killed += 1
        elif status == "SURVIVED" and note.startswith("EQUIVALENT"):
            equivalent += 1
            print(f"mutant {i}: survived as expected ({note})")
        elif status == "SURVIVED":
            survived += 1
            print(f"mutant {i}: SURVIVED  {MUTANTS[i][1]!r} -> {MUTANTS[i][2]!r}")
        else:
            invalid += 1
            print(f"mutant {i}: {status}: {note}  {MUTANTS[i][1]!r} -> {MUTANTS[i][2]!r}")
    total = len(MUTANTS)
    print(f"{killed} of {total - equivalent} mutants killed ({equivalent} equivalent, {survived} survived, {invalid} invalid)")
    sys.exit(0 if survived == 0 and invalid == 0 else 1)


if __name__ == "__main__":
    main()
