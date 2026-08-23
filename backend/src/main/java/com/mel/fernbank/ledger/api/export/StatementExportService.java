package com.mel.fernbank.ledger.api.export;

import com.mel.fernbank.ledger.api.dto.StatementEntryResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * Renders a statement as CSV or PDF for the export endpoint - a wire-format concern,
 * not business logic, hence living alongside the DTOs/mappers in {@code api} rather
 * than {@code banking}. The PDF is a hand-drawn table via PDFBox's low-level content
 * stream API (no HTML/CSS templating engine) - plainer output, smaller dependency
 * footprint. Standard14 fonts only support WinAnsi-range text; a description containing
 * characters outside that range would fail to render - acceptable for this educational
 * project's scope.
 */
@Component
public class StatementExportService {

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_INSTANT;
	private static final float MARGIN = 50f;
	private static final float ROW_HEIGHT = 16f;
	private static final float FONT_SIZE = 9f;
	private static final int ROWS_PER_PAGE = 45;
	private static final int DESCRIPTION_COLUMN_WIDTH = 28;

	public byte[] toCsv(List<StatementEntryResponse> entries) {
		StringBuilder csv = new StringBuilder();
		csv.append("date,description,amount,currency\n");
		for (StatementEntryResponse entry : entries) {
			csv.append(TIMESTAMP_FORMAT.format(entry.createdAt()))
					.append(',')
					.append(csvEscape(entry.description()))
					.append(',')
					.append(entry.amount().amount())
					.append(',')
					.append(entry.amount().currency())
					.append('\n');
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	public byte[] toPdf(String accountNumber, Instant from, Instant to, List<StatementEntryResponse> entries) {
		try (PDDocument document = new PDDocument();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDType1Font headingFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
			PDType1Font textFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			PDType1Font monoFont = new PDType1Font(Standard14Fonts.FontName.COURIER);
			PDType1Font monoBoldFont = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);

			List<List<StatementEntryResponse>> pages = paginate(entries, ROWS_PER_PAGE);
			if (pages.isEmpty()) {
				pages.add(List.of());
			}

			for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
				PDPage page = new PDPage(PDRectangle.A4);
				document.addPage(page);
				try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
					float y = page.getMediaBox().getHeight() - MARGIN;
					if (pageIndex == 0) {
						y = writeDocumentHeader(stream, headingFont, textFont, accountNumber, from, to, y);
					}
					y = writeLine(stream, monoBoldFont, y, columns("Date", "Description", "Amount", "Currency"));
					for (StatementEntryResponse entry : pages.get(pageIndex)) {
						y = writeLine(
								stream,
								monoFont,
								y,
								columns(
										TIMESTAMP_FORMAT.format(entry.createdAt()),
										entry.description() == null ? "" : entry.description(),
										entry.amount().amount(),
										entry.amount().currency()));
					}
				}
			}

			document.save(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to render statement PDF", e);
		}
	}

	private float writeDocumentHeader(
			PDPageContentStream stream,
			PDType1Font headingFont,
			PDType1Font textFont,
			String accountNumber,
			Instant from,
			Instant to,
			float y)
			throws IOException {
		y = writeText(stream, headingFont, 16, MARGIN, y, "fernbank statement");
		y = writeText(stream, textFont, FONT_SIZE, MARGIN, y - 10, "Account: " + accountNumber);
		y = writeText(
				stream,
				textFont,
				FONT_SIZE,
				MARGIN,
				y - 4,
				"Period: " + TIMESTAMP_FORMAT.format(from) + " to " + TIMESTAMP_FORMAT.format(to));
		return y - 18;
	}

	private float writeText(PDPageContentStream stream, PDType1Font font, float size, float x, float y, String text)
			throws IOException {
		stream.beginText();
		stream.setFont(font, size);
		stream.newLineAtOffset(x, y);
		stream.showText(text);
		stream.endText();
		return y - size;
	}

	private float writeLine(PDPageContentStream stream, PDType1Font font, float y, String line) throws IOException {
		stream.beginText();
		stream.setFont(font, FONT_SIZE);
		stream.newLineAtOffset(MARGIN, y);
		stream.showText(line);
		stream.endText();
		return y - ROW_HEIGHT;
	}

	private String columns(String date, String description, String amount, String currency) {
		String truncatedDescription = description.length() > DESCRIPTION_COLUMN_WIDTH
				? description.substring(0, DESCRIPTION_COLUMN_WIDTH - 3) + "..."
				: description;
		return String.format("%-20s %-" + DESCRIPTION_COLUMN_WIDTH + "s %12s %6s", date, truncatedDescription, amount, currency);
	}

	private List<List<StatementEntryResponse>> paginate(List<StatementEntryResponse> entries, int rowsPerPage) {
		List<List<StatementEntryResponse>> pages = new ArrayList<>();
		for (int i = 0; i < entries.size(); i += rowsPerPage) {
			pages.add(entries.subList(i, Math.min(i + rowsPerPage, entries.size())));
		}
		return pages;
	}

	private String csvEscape(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}
}
