import json
import time
from copy import deepcopy
from dataclasses import dataclass
from functools import total_ordering
from os import path
from typing import Generator

import requests
from ratelimit import limits
from ratelimit.decorators import sleep_and_retry

# Update parameter
AMOUNT_CHANGE_TRESHHOLD = 10  # in percent

# Paths
DATA_DIR = path.join(path.join(path.dirname(path.abspath(__file__)), ".."), "data")
TAGS_FILE = path.join(DATA_DIR, "tags.json")
TAGS_PRETTY_FILE = path.join(DATA_DIR, "tagsPretty.json")
VERSION_FILE = path.join(DATA_DIR, "tagsVersion")

# URLS
API_URL = "https://nhentai.net/api/v2/"
TAG_TYPES: list[tuple[str, int]] = [
    ("parody", 1),
    ("character", 2),
    ("tag", 3),
    ("artist", 4),
    ("group", 5),
    ("language", 6),
    ("category", 7),
]


@dataclass
@total_ordering
class TagInfo:
    id_: int
    title: str
    amount: int
    category: int

    def to_list(self) -> list[str | int]:
        return [self.id_, self.title, self.amount, self.category]

    def __eq__(self, other):
        if isinstance(other, TagInfo):
            return self.id_ == other.id_
        return False

    def __hash__(self):
        return self.id_

    def __lt__(self, other):
        if isinstance(other, TagInfo):
            if self.category == other.category:
                return self.id_ < other.id_
            return self.category < other.category
        return NotImplemented


@sleep_and_retry
@limits(calls=30, period=60)
def make_api_request(url_path: str) -> dict:
    user_agent_header = {
        "User-Agent": "NClient/tag-update-ci (https://github.com/maxwai/NClientV3)"}
    res = requests.get(API_URL + url_path, headers=user_agent_header)
    if res.status_code == 429:  # rate_limit
        print("Got rate limit, waiting 1 sec")
        time.sleep(1)
        return make_api_request(url_path)
    elif res.ok:
        return res.json()
    else:
        raise RuntimeError("Unexpected error code", res)


def get_tags(name, category) -> Generator[TagInfo]:
    current_page = 1
    while True:
        print(f"Getting tags type '{name}' page {current_page}")
        results = make_api_request(f"tags/{name}?page={current_page}&per_page=100")
        for result in results["result"]:
            yield TagInfo(result["id"], result["name"], result["count"], category)
        if current_page == results["num_pages"]:
            break
        current_page += 1


def main():
    # Read known tags
    with open(TAGS_FILE, "r") as reader:
        current_tags = json.load(reader)
    old_data_tags: list[TagInfo] = [TagInfo(x[0], x[1], x[2], x[3]) for x in current_tags]
    data_tags: dict[int, TagInfo] = {x.id_: x for x in deepcopy(old_data_tags)}

    # get tags from website
    online_data_tags: set[TagInfo] = set()
    for name, category in TAG_TYPES:
        online_data_tags.update(get_tags(name, category))

    # check list
    new_tags: list[TagInfo] = []
    for online_tag in online_data_tags:
        if online_tag.id_ not in data_tags:
            new_tags.append(online_tag)
        else:
            current_tag = data_tags[online_tag.id_]
            if current_tag.title != online_tag.title:
                print(f"Got tag id mismatch: old:{data_tags}, new:{online_tag}")
                exit(-1)
            shift = current_tag.amount * (AMOUNT_CHANGE_TRESHHOLD / 100.0)
            if not (current_tag.amount - shift <= online_tag.amount <= current_tag.amount + shift):
                current_tag.amount = online_tag.amount

    new_data_tags: list[TagInfo] = list(data_tags.values())
    new_data_tags.extend(new_tags)
    new_data_tags.sort()
    if old_data_tags == new_data_tags:
        print("No changes")
        return

    data_tags_json: list[list[int | str]] = [x.to_list() for x in new_data_tags]
    with open(TAGS_FILE, "w") as writer:
        json.dump(data_tags_json, writer, separators=(',', ':'))
    with open(TAGS_PRETTY_FILE, "w") as writer:
        json.dump(data_tags_json, writer, indent=4)
    with open(VERSION_FILE, "r") as reader:
        file_version = int(reader.read()) + 1
    with open(VERSION_FILE, "w") as writer:
        writer.write(f"{file_version}\n")


if __name__ == "__main__":
    main()
