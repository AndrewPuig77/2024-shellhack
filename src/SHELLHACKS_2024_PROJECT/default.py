import html
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

# Free-tier models are sometimes busy (5xx), and each one only allows 20 requests a day
# (429), so try these in order. Each model has its own daily quota.
MODELS = [
    "gemini-3.8-flash",
    "gemini-3.7-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
    "gemini-3.1-flash-lite",
]


def generate(client, **request):
    # Two passes over the list, with a short pause, before giving up
    attempts = MODELS + MODELS
    for i, model in enumerate(attempts):
        try:
            return client.models.generate_content(model=model, **request)
        except errors.APIError as e:
            retryable = isinstance(e, errors.ServerError) or e.code == 429
            if not retryable or i == len(attempts) - 1:
                raise
            print(f"{model} unavailable, trying next model: {e.code} {e.status}")
            if i == len(MODELS) - 1:
                time.sleep(5)


SYSTEM_PROMPT = (
    "You are a professor helping a student understand a topic from their course. "
    "Tailor the explanation to the institution and course they give you. "
    "After the explanation, write one practice problem as a multiple choice question "
    "with exactly 4 options. Put only the question in 'question' and the 4 options, "
    "without letter labels, in 'options'. Then explain the correct answer. "
    "Write all math as LaTeX wrapped in double dollar signs, like $$x^2 + 1$$, "
    "everywhere including inside the options. "
    "Never use single dollar signs. Use plain text, not Markdown."
)


def answer_number(letter):
    # "B" -> 2, matching the order of the options; 0 if the model gave something odd
    letter = letter.strip().upper()[:1]
    return "ABCD".index(letter) + 1 if letter and letter in "ABCD" else 0


class Lesson(BaseModel):
    explanation: str
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

    correct_answer = answer_number(lesson.correct_letter)

    output = {
        "Response": {
            "Topic": topic,
            "Explanation": lesson.explanation,
            "Videos": video_links(topic),
            "Question": lesson.question,
            "Options": lesson.options,
            "Answer": lesson.answer_explanation,
            "correctAnswer": correct_answer,
        }
    }
    with open(OUTPUT_FILE, "w") as f:
        json.dump(output, f)
    return lesson.explanation


if __name__ == "__main__":
    default_ai()
