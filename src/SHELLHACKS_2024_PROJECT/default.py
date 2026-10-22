import html
import json
import os
import sys
import time

from dotenv import load_dotenv
from google import genai
from google.genai import errors, types
from googleapiclient.discovery import build
from pydantic import BaseModel

# Files live next to this script, so it works no matter where it's launched from
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE = os.path.join(BASE_DIR, "input.json")
OUTPUT_FILE = os.path.join(BASE_DIR, "output.json")
LESSONS_DIR = os.path.join(BASE_DIR, "lessons")
load_dotenv(os.path.join(BASE_DIR, ".env"))


def save_output(data):
    # Saves the current lesson, plus a copy in lessons/ for the start screen's Recent lessons
    with open(OUTPUT_FILE, "w") as f:
        json.dump(data, f)
    lesson_id = data.get("Response", {}).get("Id")
    if lesson_id:
        os.makedirs(LESSONS_DIR, exist_ok=True)
        with open(os.path.join(LESSONS_DIR, f"{lesson_id}.json"), "w") as f:
            json.dump(data, f)

# Free-tier models are sometimes busy (5xx), and each one only allows 20 requests a day
# (429), so try these in order. Each model has its own daily quota.
MODELS = [
    "gemini-3.8-flash",
    "gemini-3.7-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
    "gemini-3.1-flash-lite",
]


# Exit codes the app turns into friendly messages
EXIT_QUOTA = 3
EXIT_BUSY = 4
EXIT_BAD_KEY = 5


class QuotaExceeded(Exception):
    pass


class GeminiBusy(Exception):
    pass


class MissingKey(Exception):
    pass


def gemini_client():
    key = os.getenv("GEMINI_API_KEY")
    if not key:
        raise MissingKey()
    return genai.Client(api_key=key)


def generate(client, **request):
    # Two passes over the list, with a short pause, before giving up.
    # A model that's out of today's quota (429) is skipped on the second pass.
    out_of_quota = set()
    for attempt in range(2):
        for model in MODELS:
            if model in out_of_quota:
                continue
            try:
                return client.models.generate_content(model=model, **request)
            except errors.APIError as e:
                if e.code == 429:
                    out_of_quota.add(model)
                elif not isinstance(e, errors.ServerError):
                    raise
                print(f"{model} unavailable, trying next model: {e.code} {e.status}")
        if len(out_of_quota) == len(MODELS):
            raise QuotaExceeded()
        if attempt == 0:
            time.sleep(5)
    raise GeminiBusy()


def run_script(main, *args):
    # Runs a script's main function, turning Gemini failures into exit codes for the app
    try:
        main(*args)
    except QuotaExceeded:
        print("Every Gemini model is out of today's free requests.")
        sys.exit(EXIT_QUOTA)
    except GeminiBusy:
        print("Gemini is too busy right now.")
        sys.exit(EXIT_BUSY)
    except MissingKey:
        print("GEMINI_API_KEY is missing from .env")
        sys.exit(EXIT_BAD_KEY)
    except errors.ClientError as e:
        if e.code in (401, 403) or "API key" in str(e):
            print(f"Gemini rejected the API key: {e.code} {e.status}")
            sys.exit(EXIT_BAD_KEY)
        raise


# Formatting rules shared by every prompt, so the app's LaTeX renderer can draw the math
MATH_RULES = (
    "Write all math as LaTeX wrapped in double dollar signs, like $$x^2 + 1$$, "
    "everywhere including inside answer options. "
    "Write chemical formulas upright with \\mathrm, like $$\\mathrm{CO_2}$$ or "
    "$$\\mathrm{C_6H_{12}O_6}$$, and units with \\text, like $$9.8\\text{ m/s}^2$$. "
    "For a line break start a new paragraph; never use \\newline. "
    "Never use single dollar signs. Use plain text, not Markdown."
)

# How each level on the start screen should change the lesson
LEVEL_RULES = (
    "Pitch everything at the student's level. Beginner: assume little background, use "
    "simple language and concrete examples, and keep problems straightforward. "
    "Intermediate: a typical student taking the course. Advanced: go deeper, with more "
    "rigor, edge cases and harder multi-step problems. "
)

# The lesson is split into these sections; the app shows each one as its own card
LESSON_SECTIONS = (
    "Write the lesson as sections with exactly these headings, in this order: "
    "Overview (what it is and why it matters), "
    "Key ideas (the core concepts, one short paragraph each), "
    "Worked example (a complete example solved step by step), "
    "Common mistakes (what students often get wrong and how to avoid it), "
    "Summary (the few things to remember). "
    "Put each heading in 'heading' and its text in 'body'. "
    "Separate paragraphs and steps with a blank line. "
)

SYSTEM_PROMPT = (
    "You are a professor helping a student understand a topic from their course. "
    "Tailor the lesson to the institution and course they give you. "
    + LEVEL_RULES
    + LESSON_SECTIONS +
    "After the lesson, write one practice problem as a multiple choice question "
    "with exactly 4 options. Put only the question in 'question' and the 4 options, "
    "without letter labels, in 'options'. Then explain the correct answer. "
    + MATH_RULES
)


def repair_latex(text):
    # Gemini sometimes writes LaTeX like \frac inside its JSON without doubling the backslash,
    # so the JSON parser turns \f, \b, \t, \r and \n into control characters ("\frac" arrives
    # as a form feed + "rac"). Turn them back into backslashes. Inside $$...$$ that covers all
    # five; outside math only \f, \b and \t (Gemini's prose never has real tabs, but it sometimes
    # puts \text outside $$), since newlines are normal in text.
    in_math = {"\f": "\\f", "\b": "\\b", "\t": "\\t", "\r": "\\r", "\n": "\\n"}
    in_text = {"\f": "\\f", "\b": "\\b", "\t": "\\t"}
    parts = text.split("$$")
    for i, part in enumerate(parts):
        for char, escaped in (in_math if i % 2 == 1 else in_text).items():
            part = part.replace(char, escaped)
        parts[i] = part
    return "$$".join(parts)


def answer_number(letter):
    # "B" -> 2, matching the order of the options; 0 if the model gave something odd
    letter = letter.strip().upper()[:1]
    return "ABCD".index(letter) + 1 if letter and letter in "ABCD" else 0


class Section(BaseModel):
    heading: str
    body: str


def lesson_text(sections):
    # The whole lesson as plain text, for prompts that need it as context
    return "\n\n".join(f"{section['heading']}\n{section['body']}" for section in sections)


def section_dict(section):
    return {"heading": repair_latex(section.heading), "body": repair_latex(section.body)}


class Lesson(BaseModel):
    sections: list[Section]
    question: str
    options: list[str]
    answer_explanation: str
    correct_letter: str


def search_videos(query):
    youtube = build("youtube", "v3", developerKey=os.getenv("YOUTUBE_API_KEY"))
    request = youtube.search().list(part="snippet", type="video", q=query, maxResults=3)
    return request.execute()


def video_links(topic):
    # Videos are a bonus; don't fail the whole lesson if YouTube is down or out of quota
    try:
        results = search_videos(topic)
    except Exception as e:
        print(f"YouTube search failed: {e}")
        return []
    return [
        {
            "url": f"https://www.youtube.com/watch?v={item['id']['videoId']}",
            "title": html.unescape(item["snippet"]["title"]),
            "channel": html.unescape(item["snippet"]["channelTitle"]),
            "thumbnail": item["snippet"]["thumbnails"]["medium"]["url"],
        }
        for item in results.get("items", [])
    ]


def default_ai():
    with open(INPUT_FILE, "r") as f:
        data = json.load(f)
    school = data["School"]
    course = data["Course"]
    topic = data["Topic"]
    level = data.get("Level", "Intermediate")

    client = gemini_client()
    request = dict(
        contents=f"Institution: {school} | Course: {course} | Topic: {topic} | Level: {level}",
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            response_mime_type="application/json",
            response_schema=Lesson,
        ),
    )
    response = generate(client, **request)
    lesson = response.parsed

    correct_answer = answer_number(lesson.correct_letter)
    sections = [section_dict(section) for section in lesson.sections]

    output = {
        "Response": {
            "Id": time.strftime("%Y%m%d-%H%M%S"),
            "Topic": topic,
            "Course": course,
            "School": school,
            "Level": level,
            "Sections": sections,
            "Explanation": lesson_text(sections),  # Plain-text copy used as context by the other scripts
            "Videos": video_links(topic),
            "Question": repair_latex(lesson.question),
            "Options": [repair_latex(option) for option in lesson.options],
            "Answer": repair_latex(lesson.answer_explanation),
            "correctAnswer": correct_answer,
        }
    }
    save_output(output)
    return output["Response"]["Explanation"]


if __name__ == "__main__":
    run_script(default_ai)
