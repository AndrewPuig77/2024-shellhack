import json
import os
import time

from dotenv import load_dotenv
from google import genai
from google.genai import errors, types

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
    "They have read your earlier lesson and practice problem and now have a follow-up "
    "question. Answer it clearly, referring back to the lesson where it helps. "
    "Write all math as LaTeX wrapped in double dollar signs, like $$x^2 + 1$$. "
    "Never use single dollar signs. Use plain text, not Markdown."
)


def followup_ai():
    with open(INPUT_FILE, "r") as f:
        question = json.load(f)["Input"]

    # The previous lesson is still in output.json from default.py
    with open(OUTPUT_FILE, "r") as f:
        data = json.load(f)
    previous = data.get("Response", {})
    options = "\n".join(f"{letter}) {option}" for letter, option in zip("ABCD", previous.get("Options", [])))
    lesson = (
        f"Explanation:\n{previous.get('Explanation', '')}\n\n"
        f"Practice problem:\n{previous.get('Question', '')}\n{options}\n\n"
        f"Answer:\n{previous.get('Answer', '')}"
    )

    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
    request = dict(
        contents=f"Your earlier lesson:\n{lesson}\n\nThe student now asks: {question}",
        config=types.GenerateContentConfig(system_instruction=SYSTEM_PROMPT),
    )
    response = generate(client, **request)
    output = response.text

    data.setdefault("Response", {})["Output"] = output
    with open(OUTPUT_FILE, "w") as f:
        json.dump(data, f)
    return output


if __name__ == "__main__":
    followup_ai()
