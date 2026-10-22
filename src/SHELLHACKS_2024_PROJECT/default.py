from openai import OpenAI
import os
from dotenv import load_dotenv
import json
from googleapiclient.discovery import build
load_dotenv()

client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
with open("C:\Users\puigb\Downloads\APP\APP\src\SHELLHACKS_2024_PROJECT\input.json", "r+") as input:
    data = json.load(input)
    school = data["School"]
    course = data["Course"]
    topic = data['Topic']

def search_videos(query):
    youtube = build('youtube', 'v3', developerKey=os.getenv("GOOGLE_API_KEY"))
    request = youtube.search().list(part='snippet', type='video', q=query, maxResults=3)
    response = request.execute()
    return response

def default_ai():
    completion = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {"role": "system", "content": "You are a professor who is aiding students in their desired topics in order for them to better understand it please refer to the institution and course they provided. After an explanation work out a practice problem and make the answer of the problem a choice in a 4 option multiple choice for that practice problem. Make sure the explanation, multiple choice question, and the answer explanation are seperated by 'spc' and all '\' should be replaced by '$' for easier formatting. Also seperated by 'spc' put the letter of the answer alone at the bottom"},
            {
                "role": "user",
                "content": f"Institution: {school} | Course: {course} | Topic: {topic}"
            } 
        ]
    )
    output = completion.choices[0].message.content.split("spc")

    results = search_videos(topic)
    videos = []
    for i in range(len(results["items"])):
        videos.append((f"https://www.youtube.com/watch?v={results['items'][i]['id']['videoId']}", results["items"][i]['snippet']['title']))


    with open("C:\Users\puigb\Downloads\APP\APP\src\SHELLHACKS_2024_PROJECT\output.json", "r+") as file:
        data = json.load(file)
        data['Response']['Explanation'] = f"{output[0]}(Extra Resources): {videos[0][0]} ({videos[0][-1]})\n{videos[1][0]} ({videos[1][-1]})\n{videos[-1][0]} ({videos[-1][-1]})\n\n"
        data['Response']['Question'] = output[1]
        data['Response']['Answer'] = output[2]
        if output[-1][-1].lower() == 'a':
            output[-1] = 1
        elif output[-1][-1].lower() == 'b':
            output[-1] = 2
        elif output[-1][-1].lower() == 'c':
            output[-1] = 3
        elif output[-1][-1].lower() == 'd':
            output[-1] = 4
        else:
            output[-1] = 0
        data['Response']['correctAnswer'] = output[-1]
        file.seek(0)
        file.truncate(0)
        file.write(json.dumps(data))
    return output[0]

default_ai()