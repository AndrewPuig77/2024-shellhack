# uHackEDU

An AI study buddy desktop app. Enter a topic, course and school, and it builds a lesson with
math rendered properly, suggests YouTube videos, answers follow-up questions, and quizzes you
with practice problems.

Originally built at ShellHacks 2024 by the team at
[ProgramPedro/SHELLHACKS_2024_PROJECT](https://github.com/ProgramPedro/SHELLHACKS_2024_PROJECT) but was left unable to be completed at that time.

## Features

- **Lessons** in sections (Overview, Key ideas, Worked example, Common mistakes, Summary), tailored
  to your course, school and level (Beginner / Intermediate / Advanced), with LaTeX math drawn inline
- **Go deeper**: adds a section with a harder example, where the ideas come from, or related topics
- **Videos**: three related YouTube videos as thumbnail cards
- **Follow-up questions** in a chat-style thread under the lesson
- **Practice problems** with instant feedback, "Why is my answer wrong?", Easier / Next / Harder, and a score
- **Quiz mode**: 5 questions in a row with a final score and a review of what you missed
- **Flashcards**: about 10 cards per lesson; flip them, mark Got it / Study again, and export them as a
  text file that flashcard apps can import
- **Study notes PDF**: a one-page study sheet plus the whole lesson, your questions, practice and
  quiz questions, flashcards and videos, with formulas drawn properly
- **Recent lessons** on the start screen: reopen one instantly without using a Gemini request

## Demo

https://github.com/user-attachments/assets/18c32475-8952-4913-a3d1-1d28685c2c70


## Setup

You need:

- **JDK 22 or newer** (e.g. `winget install EclipseAdoptium.Temurin.25.JDK`)
- **Python 3.11 or newer** (e.g. `winget install Python.Python.3.13`)
- Two free Google API keys (see below)

From the project folder:

```
python -m venv .venv
.venv\Scripts\pip install -r src\SHELLHACKS_2024_PROJECT\requirements.txt
```

### API keys

Copy `src/SHELLHACKS_2024_PROJECT/.env.example` to `.env` in the same folder and fill in:

| Key | Where to get it | Used for |
|---|---|---|
| `GEMINI_API_KEY` | [aistudio.google.com](https://aistudio.google.com) → Get API key | Lessons, follow-ups, practice |
| `YOUTUBE_API_KEY` | [Google Cloud Console](https://console.cloud.google.com) → enable **YouTube Data API v3** → Credentials → API key (starts with `AIza`) | Video suggestions |

Both are free. `.env` is gitignored, so keys never get committed.

## Run

```
.\mvnw.cmd clean javafx:run
```

Run it from the project folder: the app finds its Python scripts and data files by relative path.

If something goes wrong, check `logs/uhackedu.log`: it has timestamped messages from the app and the
Python scripts (Gemini errors, retries, timeouts).

## How it works

The JavaFX app (Java) handles the screens. The AI work happens in small Python scripts that the
app runs in the background:

| Script | Does |
|---|---|
| `default.py` | Builds a lesson + practice problem, finds videos. Also holds the shared Gemini code. |
| `followup.py` | Answers a follow-up question using the lesson and earlier follow-ups as context. |
| `practice.py` | Writes a new practice question (`same`, `easier` or `harder`). |
| `quiz.py` | Writes a 5-question quiz in a single request. |
| `deeper.py` | Adds one "Go deeper" section to the lesson. |
| `flashcards.py` | Makes a deck of about 10 flashcards for the lesson. |
| `summary.py` | Writes the study sheet (key points, formulas, common mistakes) for the PDF notes. |

They talk through two JSON files next to the scripts: `input.json` (what you typed) and
`output.json` (the current lesson). Every lesson is also saved in `lessons/` for the Recent
lessons list. All of these are created automatically and gitignored.

## Free tier limits

- **Gemini**: each model allows about 20 requests per day on the free tier. The scripts rotate
  through five models, so roughly 100 requests a day. Every lesson, follow-up or new practice
  question is one request. When they're all used up, the app says so; limits reset at midnight
  Pacific time.
- **YouTube**: 10,000 units a day, and each lesson's video search uses 100 (about 100 lessons).

## Project layout

```
src/main/java/com/example/app/      Java app (HelloController drives all screens)
src/main/resources/com/example/app/ Screens (.fxml), styles.css, background video
src/SHELLHACKS_2024_PROJECT/        Python AI scripts, .env, input/output JSON
```
