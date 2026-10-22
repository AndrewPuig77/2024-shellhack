package com.example.app;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

//Builds the "Save study notes" PDF from a saved lesson: study sheet, lesson, follow-ups,
//practice and quiz questions, flashcards and videos
public final class NotesPdf {
    private static final Color ACCENT = new Color(14, 116, 144);
    private static final Color MUTED = new Color(100, 116, 139);
    //Formulas are drawn at 3x and scaled down so they stay sharp when printed
    private static final float MATH_SCALE = 3;

    private static final Font TITLE = font(true, 22, Color.BLACK);
    private static final Font META = font(false, 10, MUTED);
    private static final Font HEADING = font(true, 15, ACCENT);
    private static final Font SUBHEADING = font(true, 12, Color.BLACK);
    private static final Font BODY = font(false, 11, Color.BLACK);
    private static final Font BOLD = font(true, 11, Color.BLACK);

    private NotesPdf() {
    }

    public static void write(JsonObject lesson, File file) throws IOException {
        Document doc = new Document(PageSize.LETTER, 56, 56, 56, 56);
        try (FileOutputStream out = new FileOutputStream(file)) {
            PdfWriter.getInstance(doc, out);
            doc.open();
            addTitle(doc, lesson);
            addStudySheet(doc, lesson);
            addLesson(doc, lesson);
            addFollowUps(doc, lesson);
            addPractice(doc, lesson);
            addQuiz(doc, lesson);
            addFlashcards(doc, lesson);
            addVideos(doc, lesson);
            doc.close();
        } catch (DocumentException e) {
            throw new IOException(e);
        }
    }

    private static void addTitle(Document doc, JsonObject lesson) {
        doc.add(new Paragraph(text(lesson, "Topic"), TITLE));
        List<String> parts = new ArrayList<>();
        for (String part : List.of(text(lesson, "Course"), text(lesson, "School"), text(lesson, "Level"))) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        parts.add("Study notes from uHackEDU, " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        Paragraph meta = new Paragraph(String.join("  ·  ", parts), META);
        meta.setSpacingAfter(10);
        doc.add(meta);
    }

    private static void addStudySheet(Document doc, JsonObject lesson) {
        if (!lesson.has("Summary")) {
            return;
        }
        JsonObject summary = lesson.getAsJsonObject("Summary");
        heading(doc, "Study sheet");
        bulletList(doc, "Key points", summary.getAsJsonArray("KeyPoints"));
        bulletList(doc, "Formulas and definitions to know", summary.getAsJsonArray("Formulas"));
        bulletList(doc, "Common mistakes", summary.getAsJsonArray("Mistakes"));
    }

    private static void addLesson(Document doc, JsonObject lesson) {
        JsonArray sections = array(lesson, "Sections");
        if (sections.isEmpty()) {
            heading(doc, "Lesson");
            addText(doc, text(lesson, "Explanation"), BODY);
            return;
        }
        for (JsonElement element : sections) {
            JsonObject section = element.getAsJsonObject();
            heading(doc, text(section, "heading"));
            addText(doc, text(section, "body"), BODY);
        }
    }

    private static void addFollowUps(Document doc, JsonObject lesson) {
        JsonArray followUps = array(lesson, "FollowUps");
        if (followUps.isEmpty()) {
            return;
        }
        heading(doc, "Your questions");
        for (JsonElement element : followUps) {
            JsonObject turn = element.getAsJsonObject();
            addText(doc, "Q: " + text(turn, "question"), BOLD);
            addText(doc, text(turn, "answer"), BODY);
        }
    }

    private static void addPractice(Document doc, JsonObject lesson) {
        if (text(lesson, "Question").isEmpty()) {
            return;
        }
        heading(doc, "Practice problem");
        addQuestion(doc, "", lesson);
    }

    private static void addQuiz(Document doc, JsonObject lesson) {
        JsonArray quiz = array(lesson, "Quiz");
        if (quiz.isEmpty()) {
            return;
        }
        heading(doc, "Quiz questions");
        for (int i = 0; i < quiz.size(); i++) {
            addQuestion(doc, (i + 1) + ". ", quiz.get(i).getAsJsonObject());
        }
    }

    //A multiple choice question with its options, the right answer and the explanation
    private static void addQuestion(Document doc, String prefix, JsonObject question) {
        addText(doc, prefix + text(question, "Question"), BOLD);
        JsonArray options = array(question, "Options");
        for (int i = 0; i < options.size(); i++) {
            Paragraph option = mathParagraph((char) ('A' + i) + ")  " + options.get(i).getAsString(), BODY);
            option.setIndentationLeft(14);
            doc.add(option);
        }
        int correct = question.has("correctAnswer") ? question.get("correctAnswer").getAsInt() : 0;
        String answer = correct >= 1 ? "Answer: " + (char) ('A' + correct - 1) + ".  " : "Answer: ";
        addText(doc, answer + text(question, "Answer"), BODY);
    }

    private static void addFlashcards(Document doc, JsonObject lesson) {
        JsonArray cards = array(lesson, "Flashcards");
        if (cards.isEmpty()) {
            return;
        }
        heading(doc, "Flashcards");
        PdfPTable table = new PdfPTable(new float[]{2, 3});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        for (String header : List.of("Front", "Back")) {
            PdfPCell cell = new PdfPCell(new Paragraph(header, BOLD));
            cell.setBackgroundColor(new Color(226, 240, 247));
            cell.setPadding(6);
            table.addCell(cell);
        }
        for (JsonElement element : cards) {
            JsonObject card = element.getAsJsonObject();
            for (String side : List.of("front", "back")) {
                //addElement() keeps the paragraph's own line spacing; new PdfPCell(paragraph)
                //would ignore it and let formulas overlap the line above
                PdfPCell cell = new PdfPCell();
                cell.addElement(mathParagraph(text(card, side), BODY));
                cell.setPadding(6);
                cell.setPaddingTop(2);
                table.addCell(cell);
            }
        }
        doc.add(table);
    }

    private static void addVideos(Document doc, JsonObject lesson) {
        JsonArray videos = array(lesson, "Videos");
        if (videos.isEmpty()) {
            return;
        }
        heading(doc, "Videos to watch");
        for (JsonElement element : videos) {
            JsonObject video = element.getAsJsonObject();
            Paragraph line = new Paragraph();
            line.add(new Chunk(text(video, "title"), BOLD));
            line.add(new Chunk("\n" + text(video, "url"), META));
            line.setSpacingAfter(6);
            doc.add(line);
        }
    }

    private static void heading(Document doc, String title) {
        Paragraph heading = new Paragraph(title, HEADING);
        heading.setSpacingBefore(14);
        heading.setSpacingAfter(6);
        doc.add(heading);
    }

    private static void bulletList(Document doc, String title, JsonArray items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Paragraph label = new Paragraph(title, SUBHEADING);
        label.setSpacingBefore(6);
        label.setSpacingAfter(2);
        doc.add(label);
        for (JsonElement item : items) {
            Paragraph bullet = mathParagraph("•  " + item.getAsString(), BODY);
            bullet.setIndentationLeft(14);
            bullet.setFirstLineIndent(-10);
            bullet.setSpacingAfter(2);
            doc.add(bullet);
        }
    }

    //Blank lines in the text start new paragraphs
    private static void addText(Document doc, String text, Font font) {
        for (String block : text.split("\\n\\s*\\n")) {
            if (!block.isBlank()) {
                Paragraph paragraph = mathParagraph(block.trim(), font);
                paragraph.setSpacingAfter(6);
                doc.add(paragraph);
            }
        }
    }

    //Gemini sometimes uses \newline, which the formula renderer can't draw. Outside formulas it
    //becomes a real line break; inside, a space. (\\ is left alone: it separates rows in arrays,
    //e.g. stacked binomials.) Shared with the app's mathText().
    static String cleanLineBreaks(String content) {
        String[] parts = content.split("\\$\\$", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].replace("\\newline", i % 2 == 1 ? " " : "\n");
        }
        return String.join("$$", parts);
    }

    //Text with $$...$$ formulas drawn inline as images, like the app's mathText()
    private static Paragraph mathParagraph(String content, Font font) {
        content = cleanLineBreaks(content);
        Paragraph paragraph = new Paragraph();
        //Extra line spacing when there are formulas, since fractions are taller than text
        paragraph.setLeading(font.getSize() * (content.contains("$$") ? 1.8f : 1.45f));
        String[] tokens = content.split("\\$\\$");
        for (int i = 0; i < tokens.length; i++) {
            Chunk formula = i % 2 == 1 ? mathChunk(tokens[i], font.getSize()) : null;
            if (formula != null) {
                paragraph.add(formula);
            } else {
                paragraph.add(new Chunk(tokens[i].replace("$", ""), font));
            }
        }
        return paragraph;
    }

    private static Chunk mathChunk(String latex, float fontSize) {
        try {
            TeXIcon icon = new TeXFormula(latex).createTeXIcon(TeXConstants.STYLE_TEXT, fontSize * MATH_SCALE);
            icon.setForeground(Color.BLACK);
            //White background instead of transparency, since the page is white anyway
            BufferedImage image = new BufferedImage(Math.max(1, icon.getIconWidth()), Math.max(1, icon.getIconHeight()),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            icon.paintIcon(null, graphics, 0, 0);
            graphics.dispose();

            com.lowagie.text.Image pdfImage = com.lowagie.text.Image.getInstance(image, null);
            pdfImage.scalePercent(100 / MATH_SCALE);
            //Drop the image by its depth below the baseline so it lines up with the text
            return new Chunk(pdfImage, 0, -icon.getIconDepth() / MATH_SCALE, true);
        } catch (Exception e) {
            AppLog.log("Error drawing formula in PDF: " + e);
            return null; //Falls back to showing the LaTeX as plain text
        }
    }

    //Segoe UI (Windows' interface font) covers symbols like π and ≈; Helvetica is the fallback
    private static Font font(boolean bold, float size, Color color) {
        String path = bold ? "C:/Windows/Fonts/segoeuib.ttf" : "C:/Windows/Fonts/segoeui.ttf";
        if (new File(path).exists()) {
            try {
                BaseFont base = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                return new Font(base, size, Font.NORMAL, color);
            } catch (Exception e) {
                AppLog.log("Couldn't load " + path + ", using Helvetica: " + e);
            }
        }
        return FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size, color);
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }
}
