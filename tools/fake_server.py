#!/usr/bin/env python3
"""Fake Isaac Companion mod: serves protocol v1 (docs/protocol.md) on 127.0.0.1:47777.

Lets the Android app be developed and tested without the game.

    python3 tools/fake_server.py                  # one player, looping run
    python3 tools/fake_server.py --players 2      # Isaac + Cain
    python3 tools/fake_server.py --players jacob  # Jacob & Esau
    python3 tools/fake_server.py --chaos          # garbage lines and unknown fields
    python3 tools/fake_server.py --mode mute      # connected but silent (main menu)
    python3 tools/fake_server.py --mode idle      # "no run in progress"
"""
import argparse
import json
import socket
import sys
import threading
import time

PROTOCOL = 1
GRID = 13

# RoomType / RoomShape values as in the Isaac API.
DEFAULT, SHOP, TREASURE, BOSS, SECRET, CURSE, LIBRARY, SACRIFICE, DEVIL, ANGEL = 1, 2, 4, 5, 7, 10, 12, 13, 14, 15
SH_1X1, SH_1X2, SH_2X1, SH_2X2, SH_LTL, SH_LTR, SH_LBL, SH_LBR = 1, 4, 6, 8, 9, 10, 11, 12
DF_VISIBLE, DF_SHADOW, DF_ICON = 1, 2, 4
CURSE_OF_THE_LOST = 4


def idx(x, y):
    return y * GRID + x


# (x, y, type, shape): GridIndex is the top-left cell of the bounding box.
LAYOUT = [
    (6, 6, DEFAULT, SH_1X1),   # start
    (5, 6, DEFAULT, SH_1X1),
    (7, 6, DEFAULT, SH_2X1),
    (6, 5, DEFAULT, SH_1X1),
    (6, 4, DEFAULT, SH_1X1),
    (5, 7, DEFAULT, SH_LBL),   # L-shaped room: occupies (5,7) (6,7) (6,8)
    (6, 3, BOSS, SH_1X1),
    (4, 6, TREASURE, SH_1X1),
    (9, 6, SHOP, SH_1X1),
    (7, 4, DEFAULT, SH_1X1),
    (9, 7, DEFAULT, SH_1X2),
    (4, 8, CURSE, SH_1X1),
    (7, 5, SECRET, SH_1X1),
]
# Order in which the scripted player walks through the level.
WALK = [0, 1, 0, 3, 4, 3, 0, 2, 0, 5, 0]

ITEMS = {
    1: ("The Sad Onion", "Tears up", "collectibles_001_thesadonion", "{{Tears}} +0.7 Tears"),
    2: ("The Inner Eye", "Triple shot", "collectibles_002_theinnereye", "Isaac shoots 3 tears at once"),
    3: ("Spoon Bender", "Homing shots", "collectibles_003_spoonbender", "Tears become homing"),
    4: ("Cricket's Head", "Damage up", "collectibles_004_cricketshead", "{{Damage}} +0.5 Damage#x1.5 multiplier"),
    5: ("My Reflection", "Boomerang tears", "collectibles_005_myreflection", "Tears boomerang back"),
    7: ("Magic Mushroom", "All stats up", "collectibles_012_magicmushroom", "All stats up"),
    9: ("Mom's Knife", "Throw it", "collectibles_114_momsknife", "Replaces tears with a knife"),
    105: ("The D6", "Reroll", "collectibles_105_thed6", "Rerolls all items in the room"),
    34: ("The Book of Belial", "Evil up", "collectibles_034_thebookofbelial", "{{Damage}} +2 Damage this room"),
    118: ("Brimstone", "Blood laser barrage", "collectibles_118_brimstone", "Charged blood laser"),
    168: ("Epic Fetus", "On a missile", "collectibles_168_epicfetus", "Charged missile"),
    "#NEWT": ("#NEW_ITEM_NAME", "#NEW_ITEM_DESC", "collectibles_999_unknown", None),  # localisation keys
}
ACTIVES = {105: 6, 34: 3}
TRINKETS = {1: ("Swallowed Penny", "Drops coins on hit"), 5: ("Mom's Toenail", "Stomp")}
CARDS = {1: ("0 - The Fool", "Where journey begins"), 11: ("XI - Justice", "May fortune find you")}
PILLS = {3: ("Bombs are keys", "Bombs and keys swap")}

STAGES = [
    (1, 0, "Basement I", 0), (2, 0, "Basement II", 0), (3, 0, "Caves I", CURSE_OF_THE_LOST),
    (4, 0, "Caves II", 0), (5, 1, "Depths I", 0),
]


def item_gfx(key):
    return "gfx/items/collectibles/%s.png" % ITEMS[key][2]


class Scenario:
    """Deterministic script of a run; `step(n)` returns the snapshot for tick n."""

    def __init__(self, players):
        self.players = players  # "1" | "2" | "jacob"

    def player(self, i, owned, n):
        names = {"1": ["Isaac"], "2": ["Isaac", "Cain"], "jacob": ["Jacob", "Esau"]}[self.players]
        ptypes = {"1": [0], "2": [0, 2], "jacob": [19, 20]}[self.players]
        order = [k for k in owned if k != "#NEWT"] + (["#NEWT"] if "#NEWT" in owned else [])
        actives = [k for k in order if k in ACTIVES]
        passives = [k for k in order if k not in ACTIVES]
        if i == 1:
            passives = passives[::2]
            actives = []
        act = actives[0] if actives else 0
        return {
            "index": i, "name": names[i], "playerType": ptypes[i],
            "isChild": False, "isCoopGhost": False,
            "coins": (7 + n // 4 + i) % 100, "bombs": 1 + i, "keys": 2,
            "active": {"id": act, "charge": (n // 3) % (ACTIVES.get(act, 0) + 1), "maxCharge": ACTIVES.get(act, 0), "battery": 0},
            "pocketActive": {"id": 0, "charge": 0, "maxCharge": 0},
            "trinkets": [1 if i == 0 else 0, 5 if n > 25 and i == 0 else 0],
            "card": 11 if n > 10 and i == 0 else 0,
            "pill": 3 if n > 18 and i == 0 else 0,
            "items": [{"id": k, "count": 1 + (n > 40 and k == 4)} for k in passives],
            "pickupOrder": passives,
        }

    def step(self, n):
        stage_i = (n // 40) % len(STAGES)
        t = n % 40
        stage, stype, name, curses = STAGES[stage_i]
        lost = bool(curses & CURSE_OF_THE_LOST)
        walked = WALK[: min(len(WALK), t // 2 + 1)]
        current = walked[-1]
        known = set(walked)
        for k in list(known):  # neighbours become visible (shadow), a few get their icon
            known.update({1, 2, 3, 0})
        if t > 8:
            known.update({5, 6, 7, 8, 9})
        if t > 22:
            known.update({10, 11})
        visited = set(walked)
        rooms = []
        for k, (x, y, rtype, shape) in enumerate(LAYOUT):
            if k not in known and k not in visited:
                continue
            if k == 12 and t < 30:  # secret room appears late
                continue
            is_v = k in visited
            icon = is_v or k in (6, 7, 8, 11)
            rooms.append({
                "gridIndex": idx(x, y), "safeGridIndex": idx(x, y) + (1 if shape == SH_LTL else 0),
                "type": rtype if icon else -1, "shape": shape,
                "displayFlags": DF_VISIBLE | (DF_ICON if icon else 0) if is_v or icon else DF_SHADOW,
                "visited": is_v, "clear": is_v and k != current or t > 36,
                "current": k == current,
            })
        cx, cy = LAYOUT[current][0], LAYOUT[current][1]
        if lost:
            rooms = [r for r in rooms if r["current"]]
        owned = [1, 2, 105, 3, 4, 5, 7, 9, 34, 118, 168, "#NEWT"]
        owned = owned[: 2 + (n % 120) // 9]
        return {
            "type": "state", "v": PROTOCOL,
            "meta": {"stage": stage, "stageType": stype, "levelName": name, "curses": curses, "seed": "ABCD 1234"},
            "map": {"currentRoom": idx(cx, cy), "mapHidden": lost, "rooms": rooms},
            "players": [self.player(i, owned, n) for i in range(len(self.player_names()))],
        }

    def player_names(self):
        return {"1": [0], "2": [0, 1], "jacob": [0, 1]}[self.players]

    def itemdb(self, state):
        out, seen = [], set()

        def add(e):
            key = (e["kind"], e["id"])
            if key not in seen:
                seen.add(key)
                out.append(e)

        for p in state["players"]:
            ids = [i["id"] for i in p["items"]] + ([p["active"]["id"]] if p["active"]["id"] else [])
            for k in ids:
                name, desc, _, eid = ITEMS[k]
                e = {"kind": "c", "id": k, "name": name, "desc": desc, "gfx": item_gfx(k), "active": k in ACTIVES}
                if eid:
                    e["eid"] = eid
                add(e)
            for t in p["trinkets"]:
                if t:
                    add({"kind": "t", "id": t, "name": TRINKETS[t][0], "desc": TRINKETS[t][1],
                         "gfx": "gfx/items/trinkets/trinket_%03d.png" % t})
            if p["card"]:
                add({"kind": "k", "id": p["card"], "name": CARDS[p["card"]][0], "desc": CARDS[p["card"]][1]})
            if p["pill"]:
                add({"kind": "p", "id": p["pill"], "name": PILLS[p["pill"]][0], "desc": PILLS[p["pill"]][1]})
        return {"type": "itemdb", "v": PROTOCOL, "items": out}


def line(obj):
    return (json.dumps(obj, separators=(",", ":")) + "\n").encode()


class Client(threading.Thread):
    def __init__(self, conn, addr, args):
        super().__init__(daemon=True)
        self.conn, self.addr, self.args = conn, addr, args

    def send(self, data):
        self.conn.sendall(data)

    def run(self):
        a = self.args
        print("client connected", self.addr, flush=True)
        try:
            if a.mode == "mute":
                while self.conn.recv(1024):
                    pass
                return
            self.send(line({"type": "hello", "v": PROTOCOL, "mod": "fake", "eid": True}))
            if a.mode == "idle":
                while True:
                    self.send(line({"type": "idle", "v": PROTOCOL}))
                    time.sleep(1)
            sc = Scenario(a.players)
            known, last, n = set(), None, 0
            while True:
                state = sc.step(n)
                if a.chaos and n % 5 == 2:
                    self.send(b"this is not json\n")
                    self.send(b'{"type":"state","v":1,"meta":\n')  # truncated
                    self.send(line({"type": "future-message", "v": 9, "x": [1, 2, 3]}))
                    state["extraField"] = {"nested": True}
                    state["meta"]["somethingNew"] = 1
                if state != last:
                    db = sc.itemdb(state)
                    fresh = [e for e in db["items"] if (e["kind"], e["id"]) not in known]
                    if fresh:
                        known.update((e["kind"], e["id"]) for e in fresh)
                        self.send(line({"type": "itemdb", "v": PROTOCOL, "items": fresh}))
                    self.send(line(state))
                    last = state
                self.send(line({"type": "ping", "v": PROTOCOL}))
                n += 1
                time.sleep(a.interval)
        except OSError as e:
            print("client gone:", e, flush=True)
        finally:
            self.conn.close()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=47777)
    ap.add_argument("--players", choices=["1", "2", "jacob"], default="1")
    ap.add_argument("--mode", choices=["run", "mute", "idle"], default="run")
    ap.add_argument("--chaos", action="store_true", help="inject malformed lines and unknown fields")
    ap.add_argument("--interval", type=float, default=1.0, help="seconds per scripted tick")
    args = ap.parse_args()

    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((args.host, args.port))
    srv.listen(5)
    print("fake isaac listening on %s:%d (%s, players=%s)" % (args.host, args.port, args.mode, args.players), flush=True)
    current = None
    while True:
        conn, addr = srv.accept()
        if current is not None:  # like the mod: a new client replaces the old one
            try:
                current.conn.close()
            except OSError:
                pass
        current = Client(conn, addr, args)
        current.start()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(0)
