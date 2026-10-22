import json

from google.genai import types
from pydantic import BaseModel

# Shares the file paths, .env loading and model retries with the lesson script
from default import LEVEL_RULES, MATH_RULES, OUTPUT_FILE, answer_number, gemini_client, generate, repair_latex, run_script, save_output

QUIZ_LENGTH = 5

SYSTEM_PROMPT = (
    "You are a professor writing a short quiz for a student who just studied a topic. "
    f"Write exactly {QUIZ_LENGTH} multiple choice questions, each with exactly 4 options. "
    "Cover different parts of the lesson and mix easier and harder questions. "
    "Don't repeat any question the student has already done. "
    "For each question put only the question in 'question' and the 4 options, without "
    "letter labels, in 'options'. Then explain the correct answer. "
    + LEVEL_RULES
    + MATH_RULES
)


class QuizQuestion(BaseModel):
    question: str
    options: list[str]
    answer_explanation: str
    correct_letter: str


class Quiz(BaseModel):
    questions: list[QuizQuestion]


def quiz_ai():
    with open(OUTPUT_FILE, "r") as f:
        data = json.load(f)
    lesson = data["Response"]

    asked = lesson.get("PastQuestions", []) + [lesson.get("Question", "")]
    contents = (
        f"Topic: {lesson.get('Topic', '')}\n"
        f"Student level: {lesson.get('Level', 'Intermediate')}\n\n"
        f"The lesson they studied:\n{lesson.get('Explanation', '')}\n\n"
        "Questions they've already done:\n" + "\n".join(f"- {question}" for question in asked)
    )

    response = generate(
        gemini_client(),
        contents=contents,
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            response_mime_type="application/json",
            response_schema=Quiz,
        ),
    )
    lesson["Quiz"] = [
        {
            "Question": repair_latex(q.question),
            "Options": [repair_latex(option) for option in q.options],
            "Answer": repair_latex(q.answer_explanation),
            "correctAnswer": answer_number(q.correct_letter),
        }
        for q in response.parsed.questions[:QUIZ_LENGTH]
    ]
    save_output(data)


if __name__ == "__main__":
    run_script(quiz_ai)
