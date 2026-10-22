import json
import os

from google import genai
from google.genai import types

# Shares the file paths, .env loading and model retries with the lesson script
from default import INPUT_FILE, OUTPUT_FILE, generate


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
    history = previous.get("FollowUps", [])
    lesson = (
        f"Explanation:\n{previous.get('Explanation', '')}\n\n"
        f"Practice problem:\n{previous.get('Question', '')}\n{options}\n\n"
        f"Answer:\n{previous.get('Answer', '')}"
    )
    # Earlier follow-ups, so questions like "explain that again" have context
    conversation = "".join(
        f"\n\nThe student asked: {turn['question']}\nYou answered: {turn['answer']}" for turn in history
    )

    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
    request = dict(
        contents=f"Your earlier lesson:\n{lesson}{conversation}\n\nThe student now asks: {question}",
        config=types.GenerateContentConfig(system_instruction=SYSTEM_PROMPT),
    )
    response = generate(client, **request)
    output = response.text

    response_data = data.setdefault("Response", {})
    response_data["Output"] = output  # The latest answer, which the app shows right away
    response_data["FollowUps"] = history + [{"question": question, "answer": output}]
    with open(OUTPUT_FILE, "w") as f:
        json.dump(data, f)
    return output


if __name__ == "__main__":
    followup_ai()
