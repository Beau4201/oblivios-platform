#!/usr/bin/env python3
"""Reads AirPodsProbe logcat lines and prints what the pair reported.

  adb logcat -d -s AirPodsProbe | aap-capture-table.py            controls and opcodes seen
  adb logcat -d -s AirPodsProbe | aap-capture-table.py --get 2E   the four data bytes of control 2E, or "absent"
  aap-capture-table.py --selftest

A control report is opcode 0x0009: 04 00 04 00 09 00 <id> <d1> <d2> <d3> <d4>. The last report of an identifier wins.
"""
import re
import sys

LINE = re.compile(r"recv len=(\d+) opcode=(none|0x[0-9A-F]{4})(?: hex=([0-9A-F ]+?))? ->")


def parse(lines):
    controls = {}
    opcodes = {}
    for line in lines:
        m = LINE.search(line)
        if not m:
            continue
        length, opcode, hexs = int(m.group(1)), m.group(2), m.group(3)
        count, lengths = opcodes.setdefault(opcode, [0, set()])
        opcodes[opcode] = [count + 1, lengths | {length}]
        if opcode == "0x0009" and hexs:
            b = hexs.split()
            if len(b) >= 11:
                controls[b[6]] = " ".join(b[7:11])
    return controls, opcodes


def selftest():
    sample = [
        "10-04 14:00:00.1  1  1 I AirPodsProbe: +1200 ms recv len=11 opcode=0x0009 hex=04 00 04 00 09 00 2E 32 00 00 00 -> AdaptiveLevelSetting[percent=50]",
        "10-04 14:00:00.2  1  1 I AirPodsProbe: +1210 ms recv len=11 opcode=0x0009 hex=04 00 04 00 09 00 2E 64 00 00 00 -> AdaptiveLevelSetting[percent=100]",
        "10-04 14:00:00.3  1  1 I AirPodsProbe: +1220 ms recv len=22 opcode=0x0004 hex=04 00 04 00 04 00 03 04 01 64 02 01 -> Battery",
        "10-04 14:00:00.4  1  1 I AirPodsProbe: +1230 ms recv len=300 opcode=0x001D -> DeviceInformation info=0:4",
        "10-04 14:00:00.5  1  1 I AirPodsProbe: unrelated line",
    ]
    controls, opcodes = parse(sample)
    assert controls == {"2E": "64 00 00 00"}, controls
    assert opcodes["0x0009"] == [2, {11}], opcodes
    assert opcodes["0x0004"] == [1, {22}], opcodes
    assert opcodes["0x001D"] == [1, {300}], opcodes
    assert "hex=" not in sample[3]
    print("selftest OK")


def main(argv):
    if "--selftest" in argv:
        selftest()
        return 0
    controls, opcodes = parse(sys.stdin)
    if "--get" in argv:
        print(controls.get(argv[argv.index("--get") + 1].upper(), "absent"))
        return 0
    print("controls")
    for k in sorted(controls):
        print("  %s  %s" % (k, controls[k]))
    print("opcodes")
    for k in sorted(opcodes):
        print("  %s  x%d  lengths %s" % (k, opcodes[k][0], sorted(opcodes[k][1])))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
