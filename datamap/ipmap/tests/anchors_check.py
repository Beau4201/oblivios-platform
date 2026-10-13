#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""fetch-anchors.py: the mean of the distinct capitals, and no capital preferred. No network."""
import importlib.util, os
spec = importlib.util.spec_from_file_location("fa", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tools", "fetch-anchors.py"))
fa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fa)


def near(a, b):
    return abs(a - b) < 1e-9


def main():
    assert fa.point("Point(13.383333333 52.516666666)") == (52.516666666, 13.383333333), "WKT is lon then lat"
    got = fa.mean_anchors([("DE", "Point(13.4 52.5)"), ("ZA", "Point(28.0 -25.7)"), ("ZA", "Point(18.4 -33.9)"),
                           ("ZA", "Point(26.2 -29.1)"), ("CY", "Point(33.4 35.2)"), ("CY", "Point(33.4 35.2)")])
    assert got["DE"] == (52.5, 13.4), "one capital is the point itself"
    assert near(got["ZA"][0], (-25.7 - 33.9 - 29.1) / 3) and near(got["ZA"][1], (28.0 + 18.4 + 26.2) / 3), "the mean of three capitals"
    assert got["CY"] == (35.2, 33.4), "the same point twice counts once"
    print("PASS anchors_check")


main()
