# Read the current tags.json and replace the data if the new count is higher
# This prevents lower count since the site cannot give consistent numbers
# Also keeps old tags that's gone from the server
# There is a setting to overwrite tags.json with new data

from json import dump, load
from os import getcwd, makedirs, path
from pathlib import Path
from re import findall, search
from time import sleep

from bs4 import BeautifulSoup
from curl_cffi import Session, exceptions


def run_session_get(session, url):
    REQUEST_SLEEP = 0.1
    RETRY_ATTEMPTS = 5
    RETRY_SLEEP = 10
    for i in range(RETRY_ATTEMPTS):
        try:
            r = session.get(url, timeout=60)
            sleep(REQUEST_SLEEP)
            if r.status_code == 200:
                return r
        except exceptions.RequestException as e:
            print(
                f"Request failed: {e}.\nRetrying {i + 1}/{RETRY_ATTEMPTS} in {RETRY_SLEEP} seconds..."
            )
            sleep(RETRY_SLEEP)
        except Exception as e:
            print(
                f"Unexpected error: {e}.\nRetrying {i + 1}/{RETRY_ATTEMPTS} in {RETRY_SLEEP} seconds..."
            )
            sleep(RETRY_SLEEP)


def get_sitemap_tags_urls(session, url):
    try:
        sitemap_tags_pattern = (
            r"<loc>(https://nhentai\.net/sitemap/sitemap-tags-\d+\.xml)</loc>"
        )
        print(f"Fetching sitemap tags URLs... from {url}")
        r = run_session_get(session, url)
        sitemap_tags = findall(sitemap_tags_pattern, r.text)
        return sitemap_tags
    except Exception as e:
        print(f"Error fetching sitemap tags URLs: {e}")
        exit(1)


def get_language_and_category_urls(session, sitemap_tags_urls):
    try:
        language_pattern = r"<loc>(https://nhentai\.net/language/.+?)/</loc>"
        category_pattern = r"<loc>(https://nhentai\.net/category/.+?)/</loc>"
        language_and_category_links = []
        for sitemap_url in sitemap_tags_urls:
            print(f"Fetching language and category URLs... from {sitemap_url}")
            r = run_session_get(session, sitemap_url)
            language_matches = findall(language_pattern, r.text)
            category_matches = findall(category_pattern, r.text)
            language_and_category_links.extend(language_matches)
            language_and_category_links.extend(category_matches)
        return language_and_category_links
    except Exception as e:
        print(f"Error fetching language and category URLs: {e}")
        exit(2)


def convert_count(count_str):
    if count_str.endswith("K"):
        return int(count_str[:-1]) * 1000
    return int(count_str)


def get_tag_data_from_anchor_element(anchor_element, tag_type):
    try:
        print(f"Extracting tag data... from {anchor_element}")
        tag_pattern = r'class="tag tag-(\d+)"'
        tag = search(tag_pattern, str(anchor_element)).group(1)
        name = anchor_element.find("span", class_="name").text
        count = convert_count(anchor_element.find("span", class_="count").text)
        data_list = [int(tag), str(name), int(count), int(tag_type)]
        return data_list
    except Exception as e:
        print(f"Error extracting tag data: {e}")
        exit(4)


def get_tag_type(url, SITE_AND_TAG_TYPES):
    for site_url, tag_type in SITE_AND_TAG_TYPES:
        if url.startswith(site_url):
            return tag_type
    return 0


def process_language_and_category_urls(
    session, language_and_category_urls, SITE_AND_TAG_TYPES
):
    try:
        for url in language_and_category_urls:
            print(f"Processing language and category URLs... {url}")
            tag_type = get_tag_type(url, SITE_AND_TAG_TYPES)
            r = run_session_get(session, url)
            soup = BeautifulSoup(r.text, "html.parser")
            h1_element = soup.find("h1")
            anchor_element = h1_element.find("a", class_="tag")
            data = get_tag_data_from_anchor_element(anchor_element, tag_type)
            yield data
    except Exception as e:
        print(f"Error processing language and category URLs: {e}")
        exit(3)


def process_normal_urls(session, SITE_AND_TAG_TYPES, PAGE_QUERY):
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
                    data = get_tag_data_from_anchor_element(anchor_element, tag_type)
                    yield data
                current_page += 1
    except Exception as e:
        print(f"Error processing normal URLs: {e}")
        exit(5)


def json_dedupe_and_sort(data_tags_json):
    deduped_data_tags_json = list({item[0]: item for item in data_tags_json}.values())
    sorted_data_tags_json = sorted(deduped_data_tags_json, key=lambda x: x[0])
    return sorted_data_tags_json


def json_prettify(data_tags_json):
    data_tags_json_pretty = (
        str(data_tags_json)
        .replace("[[", "[\n [")
        .replace("],", "],\n")
        .replace("]]", "]\n]")
        .replace(", ", ",")
        .replace("'", '"')
    )
    return data_tags_json_pretty


def write_new_data(data_tags_json, DATA_DIR, TAGS_DIR, TAGS_PRETTY_DIR, VERSION_DIR):
    try:
        print("Dumping new JSON data...")
        data_tags_json = json_dedupe_and_sort(data_tags_json)
        makedirs(DATA_DIR, exist_ok=True)
        with open(TAGS_DIR, "w", encoding="utf8") as f:
            dump(data_tags_json, f, separators=(",", ":"))
        data_tags_json_pretty = json_prettify(data_tags_json)
        with open(TAGS_PRETTY_DIR, "w", encoding="utf8") as f:
            f.write(data_tags_json_pretty)
        version = "1"
        with open(VERSION_DIR, "w", encoding="utf8") as f:
            f.write(version)
    except Exception as e:
        print(f"Error dumping new JSON data: {e}")
        exit(6)


def update_data(data_tags_json, DATA_DIR, TAGS_DIR, TAGS_PRETTY_DIR, VERSION_DIR):
    try:
        print("Dumping updated JSON data...")
        data_tags_json = json_dedupe_and_sort(data_tags_json)
        makedirs(DATA_DIR, exist_ok=True)
        if not Path(TAGS_DIR).exists():
            with open(TAGS_DIR, "w", encoding="utf8") as f:
                dump(data_tags_json, f, separators=(",", ":"))
        else:
            with open(TAGS_DIR, encoding="utf8") as f:
                old_data_tags_json = json_dedupe_and_sort(load(f))
            d1 = {item[0]: item for item in data_tags_json}
            d2 = {item[0]: item for item in old_data_tags_json}
            all_keys = d1.keys() | d2.keys()
            merged = {}
            for key in all_keys:
                if key in d1 and key in d2:
                    merged[key] = d1[key] if d1[key][2] >= d2[key][2] else d2[key]
                elif key in d1:
                    merged[key] = d1[key]
                else:
                    merged[key] = d2[key]
            merged_list = list(merged.values())
            data_tags_json = json_dedupe_and_sort(merged_list)
            with open(TAGS_DIR, "w", encoding="utf8") as f:
                dump(data_tags_json, f, separators=(",", ":"))
        data_tags_json_pretty = json_prettify(data_tags_json)
        with open(TAGS_PRETTY_DIR, "w", encoding="utf8") as f:
            f.write(data_tags_json_pretty)
        version = "1"
        if not Path(VERSION_DIR).exists():
            with open(VERSION_DIR, "w", encoding="utf8") as f:
                f.write(version)
        else:
            with open(VERSION_DIR, encoding="utf8") as f:
                old_version = int(f.read())
            new_version = str(old_version + 1)
            with open(VERSION_DIR, "w", encoding="utf8") as f:
                f.write(new_version)
    except Exception as e:
        print(f"Error dumping updated JSON data: {e}")
        exit(7)


def main():
    DATA_DIR = path.join(getcwd(), "data")
    TAGS_DIR = path.join(DATA_DIR, "tags.json")
    TAGS_PRETTY_DIR = path.join(DATA_DIR, "tagsPretty.json")
    VERSION_DIR = path.join(DATA_DIR, "tagsVersion")
    SITEMAP_URL = "https://nhentai.net/sitemap/sitemap.xml"
    PAGE_QUERY = "?page="
    SITE_AND_TAG_TYPES = (
        ("https://nhentai.net/parodies/", 1),
        ("https://nhentai.net/characters/", 2),
        ("https://nhentai.net/tags/", 3),
        ("https://nhentai.net/artists/", 4),
        ("https://nhentai.net/groups/", 5),
        ("https://nhentai.net/language/", 6),
        ("https://nhentai.net/category/", 7),
    )
    data_tags_json = []
    session = Session(impersonate="chrome")
    sitemap_tags_urls = get_sitemap_tags_urls(session, SITEMAP_URL)
    language_and_category_urls = get_language_and_category_urls(
        session, sitemap_tags_urls
    )
    data_tags_json.extend(
        process_language_and_category_urls(
            session, language_and_category_urls, SITE_AND_TAG_TYPES
        )
    )
    data_tags_json.extend(process_normal_urls(session, SITE_AND_TAG_TYPES, PAGE_QUERY))
    WRITE_NEW_DATA = False
    if WRITE_NEW_DATA:
        write_new_data(data_tags_json, DATA_DIR, TAGS_DIR, TAGS_PRETTY_DIR, VERSION_DIR)
    else:
        update_data(data_tags_json, DATA_DIR, TAGS_DIR, TAGS_PRETTY_DIR, VERSION_DIR)


if __name__ == "__main__":
    main()
