import json

from google.genai import types

# Shares the file paths, .env loading and model retries with the lesson script
from default import (LEVEL_RULES, MATH_RULES, OUTPUT_FILE, Section, gemini_client, generate,
                     lesson_text, run_script, save_output, section_dict)

SYSTEM_PROMPT = (
    "You are a professor. The student has read your lesson and wants to go deeper. "
    "Write ONE new section that goes further than everything so far, for example a harder "
    "worked example, where a formula or rule comes from, an important special case, or how "
    "this connects to related topics. Don't repeat what the lesson already covers. "
    "Give it a short, specific heading in 'heading' and its text in 'body'. "
    "Separate paragraphs and steps with a blank line. "
    + LEVEL_RULES
    + MATH_RULES
)


def deeper_ai():
    with open(OUTPUT_FILE, "r") as f:
        data = json.load(f)
    lesson = data["Response"]
    # Lessons made before sections existed only have the single Explanation paragraph
    sections = lesson.get("Sections") or [{"heading": "Lesson", "body": lesson.get("Explanation", "")}]

    response = generate(
        gemini_client(),
        contents=(
            f"Topic: {lesson.get('Topic', '')}\n"
            f"Student level: {lesson.get('Level', 'Intermediate')}\n\n"
            f"The lesson so far:\n{lesson_text(sections)}"
        ),
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            response_mime_type="application/json",
            response_schema=Section,
        ),
    )
    sections.append(section_dict(response.parsed))
    lesson["Sections"] = sections
    lesson["Explanation"] = lesson_text(sections)
    save_output(data)


if __name__ == "__main__":
    run_script(deeper_ai)
