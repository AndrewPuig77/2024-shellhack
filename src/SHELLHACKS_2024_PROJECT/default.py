import json
import os
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
load_dotenv(os.path.join(BASE_DIR, ".env"))

# Free-tier models sometimes return 503 under load, so try these in order
MODELS = ["gemini-3.8-flash", "gemini-3.5-flash", "gemini-3.1-flash-lite"]


def generate(client, **request):
    # Two passes over the list, with a short pause, before giving up
    attempts = MODELS + MODELS
    for i, model in enumerate(attempts):
        try:
            return client.models.generate_content(model=model, **request)
        except errors.ServerError as e:
            if i == len(attempts) - 1:
                raise
            print(f"{model} unavailable, trying next model: {e}")
            if i == len(MODELS) - 1:
                time.sleep(5)


SYSTEM_PROMPT = (
    "You are a professor helping a student understand a topic from their course. "
    "Tailor the explanation to the institution and course they give you. "
    "After the explanation, write one practice problem as a 4-option multiple choice "
    "question with options labeled A) through D), then explain the correct answer. "
    "Write all math as LaTeX wrapped in double dollar signs, like $$x^2 + 1$$. "
    "Never use single dollar signs. Use plain text, not Markdown."
)


class Lesson(BaseModel):
    explanation: str
    question: str
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
        return ""
    lines = [
        f"https://www.youtube.com/watch?v={item['id']['videoId']} ({item['snippet']['title']})"
        for item in results.get("items", [])
    ]
    return "(Extra Resources):\n" + "\n".join(lines) + "\n\n" if lines else ""


def default_ai():
    with open(INPUT_FILE, "r") as f:
        data = json.load(f)
    school = data["School"]
    course = data["Course"]
    topic = data["Topic"]

    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
    request = dict(
        contents=f"Institution: {school} | Course: {course} | Topic: {topic}",
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            response_mime_type="application/json",
            response_schema=Lesson,
        ),
    )
    response = generate(client, **request)
    lesson = response.parsed

    letter = lesson.correct_letter.strip().upper()[:1]
    correct_answer = "ABCD".index(letter) + 1 if letter and letter in "ABCD" else 0

    output = {
        "Response": {
            "Explanation": f"{lesson.explanation}\n\n{video_links(topic)}",
            "Question": lesson.question,
            "Answer": lesson.answer_explanation,
            "correctAnswer": correct_answer,
        }
    }
    with open(OUTPUT_FILE, "w") as f:
        json.dump(output, f)
    return lesson.explanation


if __name__ == "__main__":
    default_ai()
