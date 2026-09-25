#!/usr/bin/env bash
# Exercise the RemoteService deployment from this machine. Prints statuses only.

set -euo pipefail

BASE="${SF_REMOTE_NOTES_URL:-http://192.168.110.116:19110}"
python3 - "${BASE}" <<'PY'
import json, sys, urllib.parse, urllib.request

base = sys.argv[1].rstrip("/")
checks = []

def request(method, path, form=None):
    data = None
    headers = {}
    if form is not None:
        data = urllib.parse.urlencode(form).encode("utf-8")
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8")
        try:
            body = json.loads(raw) if raw else {}
        except Exception:
            body = {"raw": raw[:180]}
        return error.code, body

def check(name, ok, detail):
    checks.append((ok, name, detail))
    print(("PASS " if ok else "FAIL ") + name + " " + detail)

status, health = request("GET", "/health")
mysql = health.get("mysql") or {}
framework = health.get("framework") or {}
check("health",
      status == 200 and health.get("service") == "remote-notes"
      and mysql.get("database") == "sf_serviceframework_e2e"
      and str(mysql.get("user", "")).startswith("sf_e2e")
      and "8.0.46" in str(mysql.get("version", "")),
      "status=%s version=%s marker=%s release=%s mysql=%s" % (
          status, health.get("appVersion"), health.get("marker"),
          health.get("releaseId"), mysql.get("database")))
orm = framework.get("orm") or {}
web = framework.get("web") or {}
check("framework-jars",
      status == 200 and orm.get("file", "").startswith("serviceframework-orm")
      and web.get("file", "").startswith("serviceframework-web")
      and len(orm.get("sha256") or "") == 64
      and len(web.get("sha256") or "") == 64
      and orm.get("buildId")
      and orm.get("buildId") == web.get("buildId"),
      "orm=%s web=%s build=%s" % (orm.get("file"), web.get("file"), orm.get("buildId")))

status, bad = request("POST", "/tags", {"name": ""})
check("tag-presence", status == 400 and bad.get("error") == "invalid",
      "status=%s orm=%s" % (status, bad.get("orm")))

suffix = health.get("releaseId") or "run"
status, tag = request("POST", "/tags", {"name": "lan-" + suffix})
check("tag-create", status == 200 and tag.get("id"),
      "status=%s id=%s orm=%s" % (status, tag.get("id"), tag.get("orm")))
tag_id = tag.get("id")

status, again = request("POST", "/tags", {"name": "lan-" + suffix})
check("tag-uniqueness", status == 409 and again.get("error") == "duplicate",
      "status=%s orm=%s" % (status, again.get("orm")))

status, shown = request("GET", "/tags/%s" % tag_id)
check("tag-find", status == 200 and shown.get("id") == tag_id and shown.get("noteCount") == 0,
      "status=%s orm=%s" % (status, shown.get("orm")))

status, missing_note = request("POST", "/notes", {"title": ""})
check("note-presence", status == 400 and missing_note.get("error") == "invalid",
      "status=%s orm=%s" % (status, missing_note.get("orm")))

status, note = request("POST", "/notes", {
    "title": "first-" + suffix,
    "body": "mysql row",
    "tagId": tag_id,
})
check("note-create", status == 200 and note.get("tagId") == tag_id and note.get("tagName") == "lan-" + suffix,
      "status=%s id=%s orm=%s" % (status, note.get("id"), note.get("orm")))
note_id = note.get("id")

status, second = request("POST", "/notes", {
    "title": "second-" + suffix,
    "body": "page",
    "tagId": tag_id,
})
check("note-second", status == 200 and second.get("id") != note_id,
      "status=%s id=%s" % (status, second.get("id")))

status, page = request("GET", "/notes?tagId=%s&limit=1&offset=0" % tag_id)
items = page.get("items") or []
check("note-page",
      status == 200 and page.get("matched") == 2 and len(items) == 1
      and items[0].get("id") == note_id,
      "status=%s matched=%s orm=%s" % (status, page.get("matched"), page.get("orm")))

status, assoc = request("GET", "/tags/%s/notes" % tag_id)
assoc_items = assoc.get("items") or []
check("association",
      status == 200 and assoc.get("matched") == 2 and len(assoc_items) == 2,
      "status=%s matched=%s orm=%s" % (status, assoc.get("matched"), assoc.get("orm")))

status, updated = request("POST", "/notes/%s" % note_id, {"title": "edited-" + suffix, "body": "updated"})
check("note-update",
      status == 200 and updated.get("title") == "edited-" + suffix and updated.get("tagId") == tag_id,
      "status=%s orm=%s" % (status, updated.get("orm")))

status, loaded = request("GET", "/notes/%s" % note_id)
check("note-reload",
      status == 200 and loaded.get("title") == "edited-" + suffix and loaded.get("body") == "updated",
      "status=%s orm=%s" % (status, loaded.get("orm")))

status, blank = request("POST", "/notes/%s" % note_id, {"title": ""})
check("note-update-invalid", status == 400, "status=%s orm=%s" % (status, blank.get("orm")))

status, removed = request("DELETE", "/notes/%s" % second.get("id"))
check("note-delete", status == 200 and removed.get("deleted") is True,
      "status=%s orm=%s" % (status, removed.get("orm")))

status, gone = request("GET", "/notes/%s" % second.get("id"))
check("note-deleted", status == 404, "status=%s" % status)

status, kept = request("GET", "/notes/%s" % note_id)
check("note-kept", status == 200 and kept.get("title") == "edited-" + suffix,
      "status=%s id=%s" % (status, kept.get("id")))

failed = [name for ok, name, _detail in checks if not ok]
print("RESULT %s/%s" % (len(checks) - len(failed), len(checks)))
if failed:
    sys.exit(1)
PY
