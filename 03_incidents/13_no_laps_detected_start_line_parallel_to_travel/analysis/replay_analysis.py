#!/usr/bin/env python3
"""
Incident 13 — reproducible analysis of the "no laps detected" session.

Replays the shipped LocalLapDetector geometry against the real telemetry
recorded on 2026-09-07, and reproduces every figure quoted in the incident
report and the RCA.

This is an ANALYSIS ARTEFACT, not application code. It is a faithful port of
the Kotlin logic in:
  app/src/main/java/com/drivingcoach/lap/LocalLapDetector.kt
  app/src/main/java/com/drivingcoach/util/GeoUtils.kt
as those files stood at the time of the incident.

Usage:
    python3 analysis/replay_analysis.py
"""

import json
import math
import os

EARTH_RADIUS_M = 6371000.0

# Constants as shipped in LocalLapDetector at the time of the incident
MIN_LAP_TIME_MS = 20_000
MIN_DISTANCE_FROM_START_M = 50.0
MIN_SAMPLES = 50

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, os.pardir, "data")


# --------------------------------------------------------------------------
# Geometry — ported from GeoUtils.kt
# --------------------------------------------------------------------------

def haversine(lat1, lng1, lat2, lng2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = p2 - p1
    dlmb = math.radians(lng2 - lng1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlmb / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(a))


def bearing(lat1, lng1, lat2, lng2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dl = math.radians(lng2 - lng1)
    y = math.sin(dl) * math.cos(p2)
    x = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def make_local(ref_lat, ref_lng):
    """Equirectangular projection to local metres, as GeoUtils.toLocal does."""
    def to_local(lat, lng):
        x = math.radians(lng - ref_lng) * EARTH_RADIUS_M * math.cos(math.radians(ref_lat))
        y = math.radians(lat - ref_lat) * EARTH_RADIUS_M
        return (x, y)
    return to_local


def segments_intersect(ax, ay, bx, by, cx, cy, dx, dy):
    """Cross-product segment intersection, as GeoUtils.segmentsIntersect does."""
    def orient(px, py, qx, qy, rx, ry):
        v = (qy - py) * (rx - qx) - (qx - px) * (ry - qy)
        if abs(v) < 1e-12:
            return 0
        return 1 if v > 0 else 2

    o1 = orient(ax, ay, bx, by, cx, cy)
    o2 = orient(ax, ay, bx, by, dx, dy)
    o3 = orient(cx, cy, dx, dy, ax, ay)
    o4 = orient(cx, cy, dx, dy, bx, by)
    return o1 != o2 and o3 != o4


# --------------------------------------------------------------------------
# Load
# --------------------------------------------------------------------------

def load():
    with open(os.path.join(DATA, "session.json")) as fh:
        session = json.load(fh)
    samples = []
    with open(os.path.join(DATA, "telemetry.jsonl")) as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            if obj.get("type") == "header":
                continue
            samples.append(obj)
    samples.sort(key=lambda s: s["timestampMs"])
    return session, samples


# --------------------------------------------------------------------------
# Report sections
# --------------------------------------------------------------------------

def section_session(samples, line):
    print("=" * 74)
    print("1. SESSION CHARACTERISTICS")
    print("=" * 74)
    span = (samples[-1]["timestampMs"] - samples[0]["timestampMs"]) / 1000.0
    intervals = {samples[i]["timestampMs"] - samples[i - 1]["timestampMs"]
                 for i in range(1, len(samples))}
    path = sum(haversine(samples[i - 1]["latitude"], samples[i - 1]["longitude"],
                         samples[i]["latitude"], samples[i]["longitude"])
               for i in range(1, len(samples)))
    speeds = [s.get("speedMs", 0.0) for s in samples]
    accs = [s.get("gpsAccuracyM", 0.0) for s in samples]
    lats = [s["latitude"] for s in samples]
    lngs = [s["longitude"] for s in samples]
    mid_lat = (line["lat1"] + line["lat2"]) / 2
    mid_lng = (line["lng1"] + line["lng2"]) / 2
    dists = [haversine(s["latitude"], s["longitude"], mid_lat, mid_lng) for s in samples]

    print(f"  samples ................. {len(samples)}  (MIN_SAMPLES = {MIN_SAMPLES})")
    print(f"  duration ................ {span:.1f} s")
    print(f"  sample intervals seen ... {sorted(intervals)} ms  -> {1000.0 / min(intervals):.1f} Hz")
    print(f"  path length ............. {path:.0f} m")
    print(f"  bbox .................... {haversine(min(lats), mid_lng, max(lats), mid_lng):.0f} m "
          f"x {haversine(mid_lat, min(lngs), mid_lat, max(lngs)):.0f} m")
    print(f"  speed ................... max {max(speeds):.1f} m/s, mean {sum(speeds)/len(speeds):.2f} m/s, "
          f"{sum(1 for v in speeds if v > 2)} samples > 2 m/s")
    print(f"  gps accuracy ............ min {min(accs):.1f} m, mean {sum(accs)/len(accs):.1f} m, max {max(accs):.1f} m")
    print(f"  distance from line mid .. min {min(dists):.1f} m, max {max(dists):.1f} m")


def section_startline(line):
    print()
    print("=" * 74)
    print("2. START LINE AS CAPTURED BY THE USER")
    print("=" * 74)
    length = haversine(line["lat1"], line["lng1"], line["lat2"], line["lng2"])
    brg = bearing(line["lat1"], line["lng1"], line["lat2"], line["lng2"])
    print(f"  P1 ...................... ({line['lat1']}, {line['lng1']})")
    print(f"  P2 ...................... ({line['lat2']}, {line['lng2']})")
    print(f"  length .................. {length:.2f} m")
    print(f"  bearing ................. {brg:.1f} deg  (reciprocal {(brg + 180) % 360:.1f} deg)")
    print(f"  half-length ............. {length / 2:.2f} m  <- a crossing only counts within this of the midpoint")
    return length, brg


def shipped_detector(samples, line):
    """Faithful replay of LocalLapDetector.detectCrossings()."""
    mid_lat = (line["lat1"] + line["lat2"]) / 2
    mid_lng = (line["lng1"] + line["lng2"]) / 2
    to_local = make_local(mid_lat, mid_lng)
    a = to_local(line["lat1"], line["lng1"])
    b = to_local(line["lat2"], line["lng2"])

    crossings = []
    last_ts = None
    max_dist = 0.0
    for i in range(1, len(samples)):
        prev, curr = samples[i - 1], samples[i]
        max_dist = max(max_dist, haversine(curr["latitude"], curr["longitude"], mid_lat, mid_lng))
        p = to_local(prev["latitude"], prev["longitude"])
        q = to_local(curr["latitude"], curr["longitude"])
        if not segments_intersect(a[0], a[1], b[0], b[1], p[0], p[1], q[0], q[1]):
            continue
        ts = curr["timestampMs"]
        if last_ts is None:
            crossings.append(ts)
            last_ts = ts
            max_dist = 0.0
            continue
        if ts - last_ts < MIN_LAP_TIME_MS:
            continue
        if max_dist < MIN_DISTANCE_FROM_START_M:
            continue
        crossings.append(ts)
        last_ts = ts
        max_dist = 0.0
    return crossings


def section_shipped(samples, line):
    print()
    print("=" * 74)
    print("3. SHIPPED ALGORITHM — RESULT")
    print("=" * 74)
    crossings = shipped_detector(samples, line)
    print(f"  crossings detected ...... {len(crossings)}")
    print(f"  laps built .............. {max(0, len(crossings) - 1)}")
    print(f"  user-visible outcome .... "
          f"{'No laps detected. Complete at least 2 laps.' if len(crossings) < 2 else 'laps reported'}")
    return crossings


def section_passes(samples, line):
    print()
    print("=" * 74)
    print("4. THE FIVE PASSES — GEOMETRY OF THE FAILURE")
    print("=" * 74)
    mid_lat = (line["lat1"] + line["lat2"]) / 2
    mid_lng = (line["lng1"] + line["lng2"]) / 2
    to_local = make_local(mid_lat, mid_lng)
    a = to_local(line["lat1"], line["lng1"])
    b = to_local(line["lat2"], line["lng2"])
    ux, uy = b[0] - a[0], b[1] - a[1]
    ln = math.hypot(ux, uy)
    ux, uy = ux / ln, uy / ln          # along the line
    nx, ny = -uy, ux                   # normal to the line
    line_brg = bearing(line["lat1"], line["lng1"], line["lat2"], line["lng2"])

    # closest approach of each travelled segment to the start line segment
    def seg_seg_distance(p, q):
        best = 1e9
        for i in range(51):
            t = i / 50.0
            px, py = p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t
            for j in range(51):
                u = j / 50.0
                ax, ay = a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u
                best = min(best, math.hypot(px - ax, py - ay))
        return best

    approaches = []
    for i in range(1, len(samples)):
        p = to_local(samples[i - 1]["latitude"], samples[i - 1]["longitude"])
        q = to_local(samples[i]["latitude"], samples[i]["longitude"])
        approaches.append((seg_seg_distance(p, q), i))
    approaches.sort()

    print("  closest approaches of the driven path to the start line:")
    for d, i in approaches[:10]:
        t = (samples[i]["timestampMs"] - samples[0]["timestampMs"]) // 1000
        print(f"    sample {i:3d}  t={t:3d}s   {d:5.2f} m")

    print()
    print("  for each near pass: does the path cross the INFINITE extension of the line?")
    print("  (if it does not, no line length whatsoever could have detected it)")
    print(f"  start line bearing = {line_brg:.1f} deg")
    print()
    for idx in (33, 34, 113, 114, 190, 191, 267, 268, 351, 352):
        s = samples[idx]
        p = to_local(samples[idx - 1]["latitude"], samples[idx - 1]["longitude"])
        q = to_local(s["latitude"], s["longitude"])
        hdg = s.get("headingDeg", 0.0)
        angle = abs(((hdg - line_brg + 90) % 180) - 90)   # 0 = parallel, 90 = perpendicular
        dp = (q[0] - p[0]) * nx + (q[1] - p[1]) * ny
        verdict = "does NOT cross"
        if abs(dp) > 1e-9:
            t = -(p[0] * nx + p[1] * ny) / dp
            if 0.0 <= t <= 1.0:
                ix = p[0] + (q[0] - p[0]) * t
                iy = p[1] + (q[1] - p[1]) * t
                verdict = f"crosses at offset {ix * ux + iy * uy:+.1f} m from midpoint"
        t_s = (s["timestampMs"] - samples[0]["timestampMs"]) // 1000
        print(f"    sample {idx:3d}  t={t_s:3d}s  heading={hdg:6.1f}  "
              f"angle to line={angle:4.1f} deg  speed={s.get('speedMs', 0):4.1f} m/s  {verdict}")


def section_extension(samples, line):
    print()
    print("=" * 74)
    print("5. COUNTER-HYPOTHESIS — 'THE LINE WAS SIMPLY TOO SHORT'")
    print("=" * 74)
    print("  Extending the captured line symmetrically about its midpoint,")
    print("  keeping its ORIENTATION unchanged:")
    print()
    mid_lat = (line["lat1"] + line["lat2"]) / 2
    mid_lng = (line["lng1"] + line["lng2"]) / 2
    to_local = make_local(mid_lat, mid_lng)
    a = to_local(line["lat1"], line["lng1"])
    b = to_local(line["lat2"], line["lng2"])
    ux, uy = b[0] - a[0], b[1] - a[1]
    ln = math.hypot(ux, uy)
    ux, uy = ux / ln, uy / ln

    for half in (ln / 2, 5, 7.5, 10, 15, 20, 25):
        A = (-ux * half, -uy * half)
        B = (ux * half, uy * half)
        kept, last, max_dist = [], None, 0.0
        for i in range(1, len(samples)):
            curr = samples[i]
            max_dist = max(max_dist, haversine(curr["latitude"], curr["longitude"], mid_lat, mid_lng))
            p = to_local(samples[i - 1]["latitude"], samples[i - 1]["longitude"])
            q = to_local(curr["latitude"], curr["longitude"])
            if not segments_intersect(A[0], A[1], B[0], B[1], p[0], p[1], q[0], q[1]):
                continue
            ts = curr["timestampMs"]
            if last is None:
                kept.append(ts); last = ts; max_dist = 0.0; continue
            if ts - last < MIN_LAP_TIME_MS:
                continue
            if max_dist < MIN_DISTANCE_FROM_START_M:
                continue
            kept.append(ts); last = ts; max_dist = 0.0
        print(f"    line length {half * 2:5.1f} m  ->  crossings {len(kept)}, laps {max(0, len(kept) - 1)}")
    print()
    print("  Conclusion: length is not the controlling variable. Orientation is.")


def section_corrected(samples, line):
    print()
    print("=" * 74)
    print("6. CORRECTED GEOMETRY — ORIENTATION TAKEN FROM DIRECTION OF TRAVEL")
    print("=" * 74)
    print("  The user's two points are used only to ANCHOR the position (their")
    print("  midpoint). The crossing line is built PERPENDICULAR to the direction")
    print("  of travel. Crossing instants are linearly interpolated between samples.")
    print()
    mid_lat = (line["lat1"] + line["lat2"]) / 2
    mid_lng = (line["lng1"] + line["lng2"]) / 2
    to_local = make_local(mid_lat, mid_lng)
    P = [to_local(s["latitude"], s["longitude"]) for s in samples]
    T = [s["timestampMs"] for s in samples]

    for half in (10, 15, 20, 25):
        kept, last, max_dist = [], None, 0.0
        for i in range(1, len(samples)):
            max_dist = max(max_dist, haversine(samples[i]["latitude"], samples[i]["longitude"],
                                               mid_lat, mid_lng))
            p, q = P[i - 1], P[i]
            dx, dy = q[0] - p[0], q[1] - p[1]
            seg_len = math.hypot(dx, dy)
            if seg_len < 0.5:
                continue
            dx, dy = dx / seg_len, dy / seg_len
            s0 = p[0] * dx + p[1] * dy
            s1 = q[0] * dx + q[1] * dy
            if not (s0 <= 0 <= s1 or s1 <= 0 <= s0):
                continue
            t = (0 - s0) / (s1 - s0) if s1 != s0 else 0.0
            ix = p[0] + (q[0] - p[0]) * t
            iy = p[1] + (q[1] - p[1]) * t
            lateral = math.hypot(ix, iy)
            if lateral > half:
                continue
            ts = T[i - 1] + (T[i] - T[i - 1]) * t     # interpolated crossing instant
            if last is None:
                kept.append((ts, lateral)); last = ts; max_dist = 0.0; continue
            if ts - last < MIN_LAP_TIME_MS:
                continue
            if max_dist < MIN_DISTANCE_FROM_START_M:
                continue
            kept.append((ts, lateral)); last = ts; max_dist = 0.0

        times = [round((kept[k][0] - kept[k - 1][0]) / 1000.0, 2) for k in range(1, len(kept))]
        print(f"    half-width {half:2d} m  ->  crossings {len(kept)}, laps {max(0, len(kept) - 1)}, "
              f"lap times {times}")
        if half == 15:
            print()
            print("      detail at 15 m half-width:")
            for ts, lat in kept:
                print(f"        crossing at t = {(ts - T[0]) / 1000:6.2f} s, "
                      f"lateral offset {lat:.1f} m")
            print()


def main():
    session, samples = load()
    line = session["session"]["startLine"]

    print()
    print("INCIDENT 13 — REPLAY ANALYSIS")
    print(f"session '{session['session']['trackName']}' (id {session['session']['id']}), "
          f"app v{session['app']['versionName']} ({session['app']['versionCode']}), "
          f"{session['device']['model']}")
    print(f"laps recorded by the app: {session['laps']}")
    print()

    section_session(samples, line)
    section_startline(line)
    section_shipped(samples, line)
    section_passes(samples, line)
    section_extension(samples, line)
    section_corrected(samples, line)

    print("=" * 74)
    print("END OF ANALYSIS")
    print("=" * 74)


if __name__ == "__main__":
    main()
