#!/usr/bin/env python3
"""Points Ephyra's F-Droid inclusion merge request at a release.

    python3 .claude/skills/fdroid-update/scripts/update-mr.py [X.Y.Z] [--dry-run]
    python3 .claude/skills/fdroid-update/scripts/update-mr.py --status

Without a version it takes NAME from EphyraVersion.kt. The release must already be published:
the tag v<X.Y.Z> and its arm64 APK are what F-Droid builds and compares against.

It edits metadata/nl.midasvo.findroid.ce.yml on the merge request's source branch through the
GitLab API (one commit, no clone of fdroiddata), so it needs `glab` and `gh` logged in.

Exit codes: 0 updated, already up to date, or nothing to do (merged); 1 on any error.
"""

import argparse
import base64
import difflib
import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import quote

UPSTREAM = "fdroid/fdroiddata"
MR = 56048
METADATA = "metadata/nl.midasvo.findroid.ce.yml"
GITHUB_REPO = "midasvo/ephyra"
# The APK named by `Binaries:` in the metadata; F-Droid's build must reproduce it.
REFERENCE_APK = "ephyra-v{v}-libre-arm64-v8a.apk"
VERSION_FILE = Path("buildSrc/src/main/kotlin/EphyraVersion.kt")


def fail(message):
    sys.exit(f"error: {message}")


def run(cmd, body=None):
    result = subprocess.run(cmd, input=body, capture_output=True)
    if result.returncode != 0:
        fail(f"{' '.join(cmd[:4])}...: {result.stderr.decode(errors='replace').strip()}")
    return result.stdout


def glab(path, method="GET", body=None):
    cmd = ["glab", "api", "-X", method, path]
    if body is not None:
        cmd += ["-H", "Content-Type: application/json", "--input", "-"]
        body = json.dumps(body).encode()
    return json.loads(run(cmd, body))


def gh(path):
    return json.loads(run(["gh", "api", path]))


def version_code(version):
    major, minor, patch = (int(part) for part in version.split("."))
    return major * 1_000_000 + minor * 10_000 + patch * 100


def substitute(text, pattern, value):
    """Replaces the one match of `pattern`'s group 2; anything else means the file changed shape."""
    new, count = re.subn(pattern, lambda m: m.group(1) + value, text, flags=re.MULTILINE)
    if count != 1:
        fail(f"expected exactly one match for {pattern!r} in {METADATA}, found {count}")
    return new


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("version", nargs="?", help="X.Y.Z (default: NAME in EphyraVersion.kt)")
    parser.add_argument("--dry-run", action="store_true", help="show the diff, change nothing")
    parser.add_argument("--status", action="store_true", help="show the MR and its last pipeline")
    args = parser.parse_args()

    upstream = quote(UPSTREAM, safe="")
    mr = glab(f"projects/{upstream}/merge_requests/{MR}")
    print(f"{mr['web_url']} is {mr['state']}")

    if args.status:
        pipelines = glab(f"projects/{upstream}/merge_requests/{MR}/pipelines")
        if pipelines:
            last = pipelines[0]
            print(f"pipeline {last['status']} for {last['sha'][:8]}: {last['web_url']}")
        print(f"unresolved threads: {not mr.get('blocking_discussions_resolved', True)}")
        return

    if mr["state"] == "merged":
        print("Merged: F-Droid's checkupdates follows the tags from here on. Nothing to do.")
        return
    if mr["state"] != "opened":
        fail("the merge request is closed; ask the user what happened before doing anything")

    version = args.version
    if version is None:
        match = re.search(r'const val NAME = "(\d+\.\d+\.\d+)"', VERSION_FILE.read_text())
        if not match:
            fail(f"could not parse NAME from {VERSION_FILE} (run from the repo root)")
        version = match.group(1)
    version = version.removeprefix("v")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        fail(f"{version!r} is not X.Y.Z; betas don't go to F-Droid")
    code = version_code(version)

    # The tag is annotated, so ask for the commit it points to, not the tag object.
    commit = gh(f"repos/{GITHUB_REPO}/commits/v{version}")["sha"]
    assets = [a["name"] for a in gh(f"repos/{GITHUB_REPO}/releases/tags/v{version}")["assets"]]
    reference = REFERENCE_APK.format(v=version)
    if reference not in assets:
        fail(f"release v{version} has no {reference} yet; wait for release.yml to finish")

    project, branch = mr["source_project_id"], mr["source_branch"]
    file_path = f"projects/{project}/repository/files/{quote(METADATA, safe='')}"
    remote = glab(f"{file_path}?ref={quote(branch, safe='')}")
    old = base64.b64decode(remote["content"]).decode()

    # Before the merge the metadata keeps only the latest version, so there is one build to edit.
    new = substitute(old, r"^(  - versionName: ).*$", version)
    new = substitute(new, r"^(    versionCode: ).*$", str(code))
    new = substitute(new, r"^(    commit: )[0-9a-f]{40}$", commit)
    new = substitute(new, r"^(CurrentVersion: ).*$", version)
    new = substitute(new, r"^(CurrentVersionCode: ).*$", str(code))

    if new == old:
        print(f"Already at {version} ({code}, {commit[:8]}). Nothing to do.")
        return

    sys.stdout.writelines(
        difflib.unified_diff(
            old.splitlines(keepends=True), new.splitlines(keepends=True), METADATA, METADATA
        )
    )
    if args.dry_run:
        print("Dry run: nothing pushed.")
        return

    glab(
        file_path,
        method="PUT",
        body={
            "branch": branch,
            "content": new,
            "commit_message": f"Update Ephyra to {version} ({code})",
            # Refuses the write if someone else changed the file since we read it.
            "last_commit_id": remote["last_commit_id"],
        },
    )
    print(f"Pushed to {branch}: Ephyra {version} ({code}) at {commit}")


if __name__ == "__main__":
    main()
