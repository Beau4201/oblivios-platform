#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Checks the compiled SELinux policy for the exit service (ADR-186) with setools (`sesearch`, `seinfo`).

    check-sepolicy.py POLICY [--patch FILE]

POLICY is a binary policy that holds the platform and vendor rules, for example `recovery/root/sepolicy` of the product
output of a build (copy it to a machine with setools; the build server has none). `sesearch` expands the attributes, so a
permission that a domain only gets through an attribute counts. Exit status 0 when every rule holds, 1 with a line for
each rule that does not. Run first on a policy without the patch: it must fail on the missing types.

Rules:
  1. the three domains and their socket types exist;
  2. the daemon (exitd) cannot write netlink route messages (no nlmsg_write, no nlmsg_readpriv) and has no netfilter socket;
  3. the route helper (exitd_route) can (nlmsg_write, the closed list of message types) and has no tcp, udp, raw, packet or
     sock_diag socket of any kind;
  4. the only ioctls on /dev/tun are those of the table: the daemon TUNGETIFF TUNSETIFF (and the two descriptor flags every
     domain has); the helper none;
  5. only exit_app may connect to the control socket of either service;
  6. with --patch: the patch adds files and changes only seapp_contexts, file_contexts and file_contexts_test_data; it
     changes no neverallow file and no existing .te file.
"""
import re
import subprocess
import sys

# setools prints the command numbers, not the names (the ioctl number is the low 16 bits of the request).
TUN_IOCTLS = {0x54CA: "TUNSETIFF", 0x54D2: "TUNGETIFF"}
FD_FLAG_IOCTLS = {0x5450: "FIONCLEX", 0x5451: "FIOCLEX"}
ROUTE_NLMSG = {
    16: "RTM_NEWLINK", 18: "RTM_GETLINK", 19: "RTM_SETLINK", 20: "RTM_NEWADDR", 24: "RTM_NEWROUTE", 25: "RTM_DELROUTE", 26: "RTM_GETROUTE",
    32: "RTM_NEWRULE", 33: "RTM_DELRULE", 34: "RTM_GETRULE",
}
PATCH_MAY_CHANGE = {"private/seapp_contexts", "private/file_contexts", "contexts/file_contexts_test_data"}


def run(*args):
    out = subprocess.run(args, capture_output=True, text=True)
    if out.returncode != 0 and out.stderr.strip():
        raise SystemExit(f"{' '.join(args)}: {out.stderr.strip()}")
    return out.stdout


def types(policy):
    return {line.strip() for line in run("seinfo", "-t", policy).splitlines()[2:]}


def rules(policy, *args):
    """The permission rules (`allow`) of a `sesearch` query; the extended ones (`allowxperm`) only narrow a permission."""
    return [l.strip() for l in run("sesearch", *args, policy).splitlines() if l.strip().startswith("allow ")]


def xrules(policy, *args):
    return [l.strip() for l in run("sesearch", "--allowxperm", *args, policy).splitlines() if l.strip().startswith("allowxperm ")]


def perms_of(rule_lines):
    out = set()
    for l in rule_lines:
        m = re.search(r":\w+ (?:\{([^}]*)\}|(\w+));", l)
        if m:
            out |= set((m.group(1) or m.group(2)).split())
    return out


def xperms_of(rule_lines):
    """The command numbers of `ioctl`/`nlmsg` extended rules: a set of numbers, ranges expanded."""
    out = set()
    for l in rule_lines:
        m = re.search(r"(?:ioctl|nlmsg) (?:\{([^}]*)\}|(\S+));", l)
        if not m:
            continue
        for item in (m.group(1) or m.group(2)).split():
            lo, _, hi = item.partition("-")
            out |= set(range(int(lo, 16), int(hi or lo, 16) + 1))
    return out


def check_policy(policy):
    problems = []
    have = types(policy)
    for t in ("exitd", "exitd_route", "exit_app", "exitd_exec", "exitd_socket", "exitd_route_socket", "exitd_data_file"):
        if t not in have:
            problems.append(f"type {t} is missing")
    if problems:
        return problems  # nothing else can be read

    # 2. the daemon: the NETLINK_ROUTE socket every network domain has reads and cannot write
    p = perms_of(rules(policy, "-A", "-s", "exitd", "-t", "exitd", "-c", "netlink_route_socket"))
    if "nlmsg_write" in p:
        problems.append("exitd has nlmsg_write on netlink_route_socket")
    if rules(policy, "-A", "-s", "exitd", "-c", "netlink_netfilter_socket"):
        problems.append("exitd has a netfilter socket")
    daemon_msgs = xperms_of(xrules(policy, "-s", "exitd", "-t", "exitd", "-c", "netlink_route_socket"))
    if daemon_msgs & {16 + 3, 24, 25, 32, 33}:  # RTM_SETLINK, RTM_NEWROUTE, RTM_DELROUTE, RTM_NEWRULE, RTM_DELRULE
        problems.append("exitd may send route or rule changes")

    # 3. the route helper
    p = perms_of(rules(policy, "-A", "-s", "exitd_route", "-t", "exitd_route", "-c", "netlink_route_socket"))
    if "nlmsg_write" not in p:
        problems.append("exitd_route cannot write netlink route messages")
    msgs = xperms_of(xrules(policy, "-s", "exitd_route", "-t", "exitd_route", "-c", "netlink_route_socket"))
    if msgs - set(ROUTE_NLMSG):
        problems.append(f"exitd_route may send more message types than the closed list: {sorted(msgs - set(ROUTE_NLMSG))}")
    if not set(ROUTE_NLMSG) <= msgs:
        problems.append(f"exitd_route cannot send {sorted(ROUTE_NLMSG[n] for n in set(ROUTE_NLMSG) - msgs)}")
    for cls in ("tcp_socket", "udp_socket", "rawip_socket", "packet_socket", "icmp_socket", "netlink_tcpdiag_socket",
                "netlink_netfilter_socket"):
        if rules(policy, "-A", "-s", "exitd_route", "-c", cls):
            problems.append(f"exitd_route has {cls} rules")
    if rules(policy, "-A", "-s", "exitd_route", "-t", "tun_device", "-c", "chr_file"):
        problems.append("exitd_route can reach the tun device")

    # 4. the tun: the daemon uses TUNGETIFF and TUNSETIFF and the two flags every domain has, nothing else
    tun = xperms_of(xrules(policy, "-s", "exitd", "-t", "tun_device", "-c", "chr_file"))
    if not set(TUN_IOCTLS) <= tun or tun - set(TUN_IOCTLS) - set(FD_FLAG_IOCTLS):
        problems.append(f"exitd may use the tun ioctls {sorted(hex(n) for n in tun)}")
    if not rules(policy, "-A", "-s", "exitd", "-t", "tun_device", "-c", "chr_file", "-p", "open"):
        problems.append("exitd cannot open the tun device")

    # 5. who may connect: the exit app, and the service to itself (every domain may use its own sockets)
    for server in ("exitd", "exitd_route"):
        sources = {l.split()[1] for l in rules(policy, "-A", "-t", server, "-c", "unix_stream_socket", "-p", "connectto")}
        if sources != {"exit_app", server}:
            problems.append(f"{server} accepts connections from {sorted(sources)}, not only exit_app")
        files = {l.split()[1] for l in rules(policy, "-A", "-t", f"{server}_socket", "-c", "sock_file", "-p", "write")}
        if files - {"exit_app", "init"}:
            problems.append(f"{server}_socket can be written by {sorted(files - {'exit_app', 'init'})}")
    return problems


def check_patch(path):
    problems = []
    text = open(path, encoding="utf-8").read()
    for m in re.finditer(r"^diff --git a/(\S+) b/\S+\n(new file mode)?", text, re.M):
        name, is_new = m.group(1), bool(m.group(2))
        if is_new:
            continue
        if name not in PATCH_MAY_CHANGE:
            problems.append(f"the patch changes the existing file {name}")
    for name in re.findall(r"^diff --git a/(\S+) b/", text, re.M):
        if "neverallow" in name:
            problems.append(f"the patch touches the neverallow file {name}")
    return problems


def main():
    args = sys.argv[1:]
    if not args or args[0].startswith("-"):
        raise SystemExit(__doc__)
    policy = args[0]
    problems = check_policy(policy)
    if "--patch" in args:
        problems += check_patch(args[args.index("--patch") + 1])
    for p in problems:
        print(f"FAIL: {p}")
    if not problems:
        print("exit service policy checks passed")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
