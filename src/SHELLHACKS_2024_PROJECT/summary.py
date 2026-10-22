import json
import re

from google.genai import types
from pydantic import BaseModel

# Shares the file paths, .env loading and model retries with the lesson script
from default import (LEVEL_RULES, MATH_RULES, OUTPUT_FILE, gemini_client, generate, repair_latex,
                     run_script, save_output)

SYSTEM_PROMPT = (
    "You are a professor writing a one-page study sheet for a student who just studied a topic. "
    "From the lesson and the student's questions, list the key points to remember (short "
    "sentences), the formulas or definitions to memorize (each formula wrapped in $$...$$, or "
    "a short definition), and the most common mistakes to avoid. Keep every item to one or two "
    "sentences. If the topic has no formulas, put key definitions in 'formulas' instead. "
    + LEVEL_RULES
    + MATH_RULES
)


def as_math(item):
    # Gemini sometimes leaves the $$ markers off formula-only items; a backslash, ^ or _
    # means it's LaTeX, so wrap it to have it drawn as a formula
    item = repair_latex(item)
    if "$$" not in item and re.search(r"[\\^_]", item):
        return f"$${item}$$"
    return item


class StudySheet(BaseModel):
    key_points: list[str]
    formulas: list[str]
    mistakes: list[str]


def summary_ai():
    with open(OUTPUT_FILE, "r") as f:
        data = json.load(f)
    lesson = data["Response"]
    questions = "".join(
        f"\n- {turn['question']}" for turn in lesson.get("FollowUps", [])
    )

    response = generate(
        gemini_client(),
        contents=(
            f"Topic: {lesson.get('Topic', '')}\n"
            f"Student level: {lesson.get('Level', 'Intermediate')}\n\n"
            f"The lesson:\n{lesson.get('Explanation', '')}\n\n"
            f"Questions the student asked:{questions or ' none'}"
        ),
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            response_mime_type="application/json",
            response_schema=StudySheet,
        ),
    )
    sheet = response.parsed
    lesson["Summary"] = {
        "KeyPoints": [repair_latex(item) for item in sheet.key_points],
        "Formulas": [as_math(item) for item in sheet.formulas],
        "Mistakes": [repair_latex(item) for item in sheet.mistakes],
    }
    save_output(data)


if __name__ == "__main__":
    run_script(summary_ai)
