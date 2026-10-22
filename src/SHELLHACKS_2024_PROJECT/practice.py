import json
import os
import sys

from google import genai
from google.genai import types
from pydantic import BaseModel

# Shares the file paths, .env loading and model retries with the lesson script
from default import OUTPUT_FILE, answer_number, generate

SYSTEM_PROMPT = (
    "You are a professor writing practice problems for a student who just studied a topic. "
    "Write one new multiple choice question about the topic with exactly 4 options. "
    "It must test something different from the questions the student has already done. "
    "Put only the question in 'question' and the 4 options, without letter labels, "
    "in 'options'. Then explain the correct answer. "
    "Write all math as LaTeX wrapped in double dollar signs, like $$x^2 + 1$$, "
    "everywhere including inside the options. "
    "Never use single dollar signs. Use plain text, not Markdown."
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

    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
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
    with open(OUTPUT_FILE, "w") as f:
        json.dump(data, f)


if __name__ == "__main__":
    practice_ai(sys.argv[1] if len(sys.argv) > 1 else "same")
