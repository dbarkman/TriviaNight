"""Builds app/src/main/assets/questions.json from data/opentdb.json and data/written/*.json.

Open Trivia DB's easy, medium and hard become levels 3, 4 and 5; the written files carry
their own levels. Each category gets one of the app's groups (Questions.kt's Groups).
Questions that repeat (same text) are kept once.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "questions.json"

LEVELS = {"easy": 3, "medium": 4, "hard": 5}

# Open Trivia DB's category names, as shown in the app, with their group.
OPENTDB = {
    "General Knowledge": ("General Knowledge", "General"),
    "Entertainment: Books": ("Books", "Arts, Words & Music"),
    "Entertainment: Film": ("Film", "Movies & TV"),
    "Entertainment: Music": ("Music", "Arts, Words & Music"),
    "Entertainment: Musicals & Theatres": ("Musicals & Theater", "Arts, Words & Music"),
    "Entertainment: Television": ("Television", "Movies & TV"),
    "Entertainment: Video Games": ("Video Games", "Sports & Games"),
    "Entertainment: Board Games": ("Board Games", "Sports & Games"),
    "Science & Nature": ("Science", "Science & Nature"),
    "Science: Computers": ("Computers", "Science & Nature"),
    "Science: Mathematics": ("Math", "Science & Nature"),
    "Mythology": ("Mythology", "Places & History"),
    "Sports": ("Sports", "Sports & Games"),
    "Geography": ("Geography", "Places & History"),
    "History": ("History", "Places & History"),
    "Politics": ("Politics", "Places & History"),
    "Art": ("Art", "Arts, Words & Music"),
    "Celebrities": ("Celebrities", "Movies & TV"),
    "Animals": ("Animals", "Science & Nature"),
    "Vehicles": ("Vehicles", "General"),
    "Entertainment: Comics": ("Comics", "Arts, Words & Music"),
    "Science: Gadgets": ("Gadgets", "Science & Nature"),
    "Entertainment: Japanese Anime & Manga": ("Anime & Manga", "Movies & TV"),
    "Entertainment: Cartoon & Animations": ("Cartoons", "Movies & TV"),
}

# The written files name their own groups; these are the app's spelling of them.
GROUP_NAMES = {"Arts & Words": "Arts, Words & Music"}


def main():
    categories = {}  # name -> (index, group)
    questions = []
    seen = set()

    def category(name, group):
        if name not in categories:
            categories[name] = (len(categories), GROUP_NAMES.get(group, group))
        return categories[name][0]

    def add(name, group, level, text, answer, wrong):
        text, answer = text.strip(), answer.strip()
        wrong = [w.strip() for w in wrong]
        key = text.lower()
        if key in seen or not text or not answer or answer in wrong or len(set(wrong)) != len(wrong):
            return
        seen.add(key)
        questions.append([category(name, group), level, text, answer, *wrong])

    for path in sorted((ROOT / "data" / "written").glob("*.json")):
        data = json.loads(path.read_text())
        for q in data["questions"]:
            add(data["category"], data["group"], q["level"], q["q"], q["a"], q["wrong"])

    for q in json.loads((ROOT / "data" / "opentdb.json").read_text()):
        name, group = OPENTDB[q["category"]]
        add(name, group, LEVELS[q["difficulty"]], q["q"], q["a"], q["wrong"])

    ordered = sorted(categories.items(), key=lambda item: item[1][0])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "categories": [{"name": name, "group": group} for name, (_, group) in ordered],
        "questions": questions,
    }, ensure_ascii=False, separators=(",", ":")))

    print(f"{len(questions)} questions in {len(categories)} categories")
    for level in range(1, 6):
        print(f"  level {level}: {sum(1 for q in questions if q[1] == level)}")


main()
