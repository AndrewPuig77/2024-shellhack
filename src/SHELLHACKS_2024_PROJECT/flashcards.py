import json

from google.genai import types
from pydantic import BaseModel

# Shares the file paths, .env loading and model retries with the lesson script
from default import (LEVEL_RULES, MATH_RULES, OUTPUT_FILE, gemini_client, generate, repair_latex,
                     run_script, save_output)

DECK_SIZE = 10

SYSTEM_PROMPT = (
    "You are a professor making flashcards for a student who just studied a topic. "
    f"Write {DECK_SIZE} flashcards covering the most important things in the lesson: key terms, "
    "formulas, and 'why' or 'how' questions. Put a short question or term in 'front' and a "
    "concise answer, one to three sentences, in 'back'. "
    + LEVEL_RULES
    + MATH_RULES
)


class Card(BaseModel):
    front: str
    back: str


class Deck(BaseModel):
    cards: list[Card]


def flashcards_ai():
    with open(OUTPUT_FILE, "r") as f:
        data = json.load(f)
    lesson = data["Response"]

    response = generate(
        gemini_client(),
        contents=(
            f"Topic: {lesson.get('Topic', '')}\n"
            f"Student level: {lesson.get('Level', 'Intermediate')}\n\n"
            f"The lesson they studied:\n{lesson.get('Explanation', '')}"
        ),
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            response_mime_type="application/json",
            response_schema=Deck,
        ),
    )
    lesson["Flashcards"] = [
        {"front": repair_latex(card.front), "back": repair_latex(card.back)}
        for card in response.parsed.cards[:DECK_SIZE]
    ]
    save_output(data)


if __name__ == "__main__":
    run_script(flashcards_ai)
