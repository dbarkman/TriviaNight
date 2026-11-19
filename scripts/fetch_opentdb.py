"""Downloads every verified Open Trivia DB question into data/opentdb.json.

Open Trivia DB (https://opentdb.com) is CC BY-SA 4.0. It allows one request every 5 seconds,
so a full download takes about ten minutes.
"""
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://opentdb.com"
OUT = Path(__file__).resolve().parent.parent / "data" / "opentdb.json"


def get(path):
    for attempt in range(6):
        time.sleep(5.5)
        try:
            with urllib.request.urlopen(API + path, timeout=30) as response:
                body = json.load(response)
        except Exception as error:  # network hiccup or 429: back off and try again
            print("retry", path, error, file=sys.stderr)
            time.sleep(5 * 2**attempt)
            continue
        if body.get("response_code") == 5:  # rate limited
            time.sleep(5 * 2**attempt)
            continue
        return body
    raise RuntimeError(f"gave up on {path}")


def decode(text):
    return urllib.parse.unquote(text)


def main():
    token = get("/api_token.php?command=request")["token"]
    categories = get("/api_category.php")["trivia_categories"]
    questions = []
    for category in categories:
        count = get(f"/api_count.php?category={category['id']}")["category_question_count"]["total_question_count"]
        remaining = count
        while remaining > 0:
            amount = min(50, remaining)
            body = get(f"/api.php?amount={amount}&category={category['id']}&token={token}&encode=url3986")
            code = body["response_code"]
            if code == 4:  # the token has handed out everything in this category
                break
            if code == 1:  # fewer left than asked for
                remaining = amount - 1 if amount > 1 else 0
                continue
            for q in body["results"]:
                questions.append({
                    "category": decode(q["category"]),
                    "difficulty": q["difficulty"],
                    "type": q["type"],
                    "q": decode(q["question"]),
                    "a": decode(q["correct_answer"]),
                    "wrong": [decode(w) for w in q["incorrect_answers"]],
                })
            remaining -= len(body["results"])
        print(category["name"], count, len(questions), file=sys.stderr)
    OUT.write_text(json.dumps(questions, ensure_ascii=False, indent=0))
    print("saved", len(questions))


main()
