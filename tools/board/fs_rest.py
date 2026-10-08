"""Firestore REST helper using the local firebase CLI login (owner account). Token is read, never printed."""
import json, os, sys, urllib.request, urllib.error

BASE = "https://firestore.googleapis.com/v1/projects/readyport-app/databases/(default)/documents"


def token():
    p = os.path.expanduser("~/.config/configstore/firebase-tools.json")
    return json.load(open(p, encoding="utf-8"))["tokens"]["access_token"]


def call(method, path, body=None):
    req = urllib.request.Request(
        BASE + path, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Authorization": "Bearer " + token(), "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read() or "{}")
    except urllib.error.HTTPError as e:
        return {"error": e.code, "body": e.read().decode()[:600]}


def val(v):
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, str):
        return {"stringValue": v}
    if isinstance(v, list):
        return {"arrayValue": {"values": [val(x) for x in v]}}
    if isinstance(v, dict) and "__ts__" in v:
        return {"timestampValue": v["__ts__"]}
    raise TypeError(v)


def fields(d):
    return {"fields": {k: val(v) for k, v in d.items()}}
