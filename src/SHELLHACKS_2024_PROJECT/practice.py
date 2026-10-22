import json
import sys

from google.genai import types
from pydantic import BaseModel

# Shares the file paths, .env loading and model retries with the lesson script
from default import MATH_RULES, OUTPUT_FILE, answer_number, gemini_client, generate, run_script, save_output

SYSTEM_PROMPT = (
    "You are a professor writing practice problems for a student who just studied a topic. "
    "Write one new multiple choice question about the topic with exactly 4 options. "
    "It must test something different from the questions the student has already done. "
    "Put only the question in 'question' and the 4 options, without letter labels, "
    "in 'options'. Then explain the correct answer. "
    + MATH_RULES
)

DIFFICULTY = {
    "same": "Keep the difficulty about the same as the last question.",
    "easier": "Make it easier than the last question; the student found it hard.",
    "harder": "Make it harder than the last question; the student wants a challenge.",
}


class Problem(BaseModel):
    question: str
    options: list[str]
    answer_explanation: str
    correct_letter: str


def practice_ai(difficulty):
    with open(OUTPUT_FILE, "r") as f:
        data = json.load(f)
    lesson = data["Response"]

    # Every question asked so far for this lesson, so the new one isn't a repeat
    asked = lesson.get("PastQuestions", []) + [lesson.get("Question", "")]
    contents = (
        f"Topic: {lesson.get('Topic', '')}\n\n"
        f"The lesson they studied:\n{lesson.get('Explanation', '')}\n\n"
        "Questions they've already done:\n"
        + "\n".join(f"- {question}" for question in asked)
        + f"\n\n{DIFFICULTY[difficulty]}"
    )

    client = gemini_client()
    response = generate(
        client,
        contents=contents,
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            response_mime_type="application/json",
            response_schema=Problem,
        ),
    )
    problem = response.parsed

    lesson["PastQuestions"] = asked
    lesson["Question"] = problem.question
    lesson["Options"] = problem.options
    lesson["Answer"] = problem.answer_explanation
    lesson["correctAnswer"] = answer_number(problem.correct_letter)
    save_output(data)


if __name__ == "__main__":
    run_script(practice_ai, sys.argv[1] if len(sys.argv) > 1 else "same")
