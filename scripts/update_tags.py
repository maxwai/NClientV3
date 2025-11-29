import json
import re
from copy import deepcopy
from dataclasses import dataclass
from functools import total_ordering
from os import path
from time import sleep
from typing import Generator

from bs4 import BeautifulSoup, Tag
from curl_cffi import Session, exceptions, Response

# Update parameter
AMOUNT_CHANGE_TRESHHOLD = 10  # in percent

# Paths
DATA_DIR = path.join(path.join(path.dirname(path.abspath(__file__)), ".."), "data")
TAGS_FILE = path.join(DATA_DIR, "tags.json")
TAGS_PRETTY_FILE = path.join(DATA_DIR, "tagsPretty.json")
VERSION_FILE = path.join(DATA_DIR, "tagsVersion")

# Request Options
REQUEST_SLEEP = 0.1
RETRY_ATTEMPTS = 5
RETRY_SLEEP = 10

# URLS
SITEMAP_URL = "https://nhentai.net/sitemap/sitemap.xml"
PAGE_QUERY = "?page="
SITE_AND_TAG_TYPES: list[tuple[str, int]] = [
    ("https://nhentai.net/parodies/", 1),
    ("https://nhentai.net/characters/", 2),
    ("https://nhentai.net/tags/", 3),
    ("https://nhentai.net/artists/", 4),
    ("https://nhentai.net/groups/", 5),
    ("https://nhentai.net/language/", 6),
    ("https://nhentai.net/category/", 7),
]
SITEMAP_TAGS_PATTERN = r"<loc>(https://nhentai\.net/sitemap/sitemap-tags-\d+\.xml)</loc>"
LANGUAGE_PATTERN = r"<loc>(https://nhentai\.net/language/.+?)/</loc>"
CATEGORY_PATTERN = r"<loc>(https://nhentai\.net/category/.+?)/</loc>"
TAG_PATTERN = r'class="tag tag-(\d+)"'


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


def run_session_get(session: Session, url: str) -> Response:
    for i in range(RETRY_ATTEMPTS):
        try:
            r = session.get(url, timeout=60)
            sleep(REQUEST_SLEEP)
            if r.ok:
                return r
        except exceptions.RequestException as e:
            print(
                f"Request failed: {e}.\nRetry {i + 1}/{RETRY_ATTEMPTS} in {RETRY_SLEEP} seconds..."
            )
            sleep(RETRY_SLEEP)
        except Exception as e:
            print(
                f"Exception: {e}.\nRetry {i + 1}/{RETRY_ATTEMPTS} in {RETRY_SLEEP} seconds..."
            )
            sleep(RETRY_SLEEP)
    raise Exception


def get_sitemap_tags_urls(session: Session) -> list[str]:
    try:
        print(f"Fetching sitemap tags URLs... from {SITEMAP_URL}")
        r = run_session_get(session, SITEMAP_URL)
        sitemap_tags: list[str] = re.findall(SITEMAP_TAGS_PATTERN, r.text)
        return sitemap_tags
    except Exception as e:
        print(f"Error fetching sitemap tags URLs: {e}")
        exit(1)


def get_language_and_category_urls(session: Session, sitemap_tags_urls: list[str]) -> list[str]:
    try:
        language_and_category_links: list[str] = []
        for sitemap_url in sitemap_tags_urls:
            print(f"Fetching language and category URLs... from {sitemap_url}")
            r = run_session_get(session, sitemap_url)
            language_matches: list[str] = re.findall(LANGUAGE_PATTERN, r.text)
            category_matches: list[str] = re.findall(CATEGORY_PATTERN, r.text)
            language_and_category_links.extend(language_matches)
            language_and_category_links.extend(category_matches)
        return language_and_category_links
    except Exception as e:
        print(f"Error fetching language and category URLs: {e}")
        exit(2)


def convert_count(count_str: str) -> int:
    if count_str.endswith("K"):
        return int(count_str[:-1]) * 1000
    return int(count_str)


def get_tag_data_from_anchor_element(anchor_element: Tag, tag_type: int) -> TagInfo:
    try:
        print(f"Extracting tag data... from {anchor_element}")
        tag_id = re.search(TAG_PATTERN, str(anchor_element)).group(1)
        name = anchor_element.find("span", class_="name").text
        count = convert_count(anchor_element.find("span", class_="count").text)
        return TagInfo(int(tag_id), name, int(count), tag_type)
    except Exception as e:
        print(f"Error extracting tag data: {e}")
        exit(4)


def get_tag_type(url: str) -> int:
    for site_url, tag_type in SITE_AND_TAG_TYPES:
        if url.startswith(site_url):
            return tag_type
    print(f"Unknown Tag Type for {url}")
    raise NotImplemented


def process_language_and_category_urls(
    session: Session, language_and_category_urls: list[str]
) -> Generator[TagInfo]:
    try:
        for url in language_and_category_urls:
            print(f"Processing language and category URLs... {url}")
            tag_type = get_tag_type(url)
            r = run_session_get(session, url)
            soup = BeautifulSoup(r.text, "html.parser")
            h1_element = soup.find("h1")
            anchor_element = h1_element.find("a", class_="tag")
            yield get_tag_data_from_anchor_element(anchor_element, tag_type)
    except Exception as e:
        print(f"Error processing language and category URLs: {e}")
        exit(3)


def process_normal_urls(session: Session) -> Generator[TagInfo]:
    try:
        for site_url, tag_type in SITE_AND_TAG_TYPES:
            if tag_type >= 6:
                break
            current_page = 1
            while True:
                url = f"{site_url}{PAGE_QUERY}{current_page}"
                r = run_session_get(session, url)
                soup = BeautifulSoup(r.text, "html.parser")
                tag_container = soup.find("div", id="tag-container")
                anchor_elements = tag_container.find_all("a", class_="tag")
                if not anchor_elements:
                    break
                print(f"Processing normal URLs... {url}")
                for anchor_element in anchor_elements:
                    yield get_tag_data_from_anchor_element(anchor_element, tag_type)
                current_page += 1
    except Exception as e:
        print(f"Error processing normal URLs: {e}")
        exit(5)


def json_dedupe_and_sort(data_tags: list[TagInfo]) -> list[TagInfo]:
    deduped_data_tags_json = list(set(data_tags))
    sorted_data_tags_json = sorted(deduped_data_tags_json)
    return sorted_data_tags_json


def main():
    # Read known tags
    with open(TAGS_FILE, "r") as reader:
        current_tags = json.load(reader)
    old_data_tags: list[TagInfo] = [TagInfo(x[0], x[1], x[2], x[3]) for x in current_tags]
    data_tags: dict[int, TagInfo] = {x.id_: x for x in deepcopy(old_data_tags)}

    # get tags from website
    session: Session = Session(impersonate="chrome")
    sitemap_tags_urls: list[str] = get_sitemap_tags_urls(session)
    language_and_category_urls = get_language_and_category_urls(session, sitemap_tags_urls)
    online_data_tags: set[TagInfo] = set()
    online_data_tags.update(process_language_and_category_urls(session, language_and_category_urls))
    online_data_tags.update(process_normal_urls(session))

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
