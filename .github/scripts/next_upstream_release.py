#!/usr/bin/env python3
"""Select the oldest unprocessed stable upstream GitHub Release."""

import argparse
import json
import os
import re
import urllib.request
from pathlib import Path


def stable_releases(repo: str, token: str) -> list[dict]:
    releases = []
    page = 1
    while True:
        request = urllib.request.Request(
            f"https://api.github.com/repos/{repo}/releases?per_page=100&page={page}",
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {token}",
                "User-Agent": "ani-rss-openlist-upstream-sync",
            },
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            batch = json.load(response)
        releases.extend(release for release in batch if not release["draft"] and not release["prerelease"])
        if len(batch) < 100:
            break
        page += 1
    return sorted(releases, key=lambda release: release["published_at"])


def next_release(releases: list[dict], marker: str) -> dict | None:
    for index, release in enumerate(releases):
        if release["tag_name"] == marker:
            return releases[index + 1] if index + 1 < len(releases) else None
    raise ValueError(f"Tracked release {marker!r} was not found in upstream's stable releases")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--marker", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    marker = args.marker.read_text(encoding="utf-8").strip()
    release = next_release(stable_releases(args.repo, os.environ["GH_TOKEN"]), marker)
    if release is None:
        print(f"Already at the newest stable upstream release: {marker}")
        return
    tag = release["tag_name"]
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,80}", tag):
        raise ValueError(f"Unexpected upstream release tag: {tag!r}")
    branch = f"sync/upstream-{tag}"
    with args.output.open("a", encoding="utf-8") as output:
        output.write(f"tag={tag}\nbranch={branch}\n")
    print(f"Next upstream release: {tag}")


if __name__ == "__main__":
    main()
