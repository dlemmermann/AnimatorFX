#!/usr/bin/env python3
"""Render the book PDF with Python-Markdown 3.10.2 and Chromium 131 or later.

Install the renderer with: python3 -m pip install Markdown==3.10.2
Run: python3 docs/manual/build.py [--chrome /path/to/chrome]
"""

import argparse
from pathlib import Path
import shutil
import subprocess
from tempfile import TemporaryDirectory
from urllib.parse import unquote, urlsplit, urlunsplit

import markdown
from markdown.extensions import Extension
from markdown.treeprocessors import Treeprocessor


HERE = Path(__file__).resolve().parent
DOCS = HERE.parent
REPO = DOCS.parent
SOURCE = DOCS / "USER_MANUAL.md"
OUTPUT = DOCS / "AnimatorFX-User-Manual.pdf"
GITHUB = "https://github.com/dlemmermann/AnimatorFX"


class PrintLinks(Treeprocessor):
    def run(self, root):
        for image in root.iter("img"):
            image.set("src", (DOCS / image.get("src")).resolve().as_uri())
        for link in root.iter("a"):
            url = urlsplit(link.get("href"))
            if not url.scheme and not url.netloc and url.path:
                target = (DOCS / unquote(url.path)).resolve()
                kind = "tree" if target.is_dir() else "blob"
                base = urlsplit(f"{GITHUB}/{kind}/HEAD/")
                path = target.relative_to(REPO).as_posix()
                link.set("href", urlunsplit(base._replace(
                    path=base.path + path, query=url.query, fragment=url.fragment
                )))


class PrintExtension(Extension):
    def extendMarkdown(self, md):
        md.treeprocessors.register(PrintLinks(md), "print-links", 5)


def find_chrome():
    for name in ("google-chrome", "chromium", "chromium-browser", "chrome"):
        if path := shutil.which(name):
            return path
    mac = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
    return str(mac) if mac.is_file() else None


def render_html():
    content = markdown.markdown(
        SOURCE.read_text(encoding="utf-8"),
        extensions=["fenced_code", "tables", "toc", PrintExtension()],
    )
    cover, found_contents, rest = content.partition('<h2 id="contents">')
    contents, found_chapter, body = rest.partition('<h2 id="1-get-started">')
    if not found_contents or not found_chapter:
        raise ValueError("The manual needs its Contents and first chapter headings")
    css = (HERE / "print.css").as_uri()
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>AnimatorFX User Manual</title>
<link rel="stylesheet" href="{css}">
</head>
<body>
<header class="cover">
<p class="eyebrow"><span></span> ANIMATORFX / PRODUCT GUIDE</p>
{cover}
<p class="cover-footer">DESKTOP EDITOR <b></b> JAVAFX RUNTIME</p>
</header>
<nav class="contents" aria-label="Table of contents">
<p class="eyebrow">HOW TO USE THIS GUIDE</p>
{found_contents}{contents}
</nav>
<main>
{found_chapter}{body}
</main>
</body>
</html>
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--chrome", default=find_chrome(), help="Chrome or Chromium executable")
    args = parser.parse_args()
    if not args.chrome:
        parser.error("could not find Chrome or Chromium; pass --chrome")

    with TemporaryDirectory(prefix="animatorfx-manual-") as directory:
        work = Path(directory)
        html = work / "manual.html"
        pdf = work / "manual.pdf"
        html.write_text(render_html(), encoding="utf-8")
        subprocess.run(
            [
                args.chrome, "--headless", "--no-first-run", "--disable-background-networking",
                "--log-level=3",
                "--no-pdf-header-footer",
                f"--user-data-dir={work / 'profile'}", f"--print-to-pdf={pdf}", html.as_uri(),
            ],
            check=True,
        )
        shutil.copyfile(pdf, OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    main()
