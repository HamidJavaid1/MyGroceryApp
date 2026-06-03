package com.bazarlink.shared.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Heuristic parser for grocery label OCR text. */
final class LabelTextParser {

    private static final Pattern UNIT_PATTERN = Pattern.compile(
            "\\b(\\d+[\\d.,]*)?\\s*(kg|kgs|g|gm|gms|gram|grams|l|ltr|litre|liter|ml|bag|bags|pack|packs|crate|crates|pcs|pc|piece|pieces|unit|units|dozen)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(?:rs\\.?|pkr|₨|price)\\s*[:\\-]?\\s*([\\d,]+(?:\\.\\d+)?)|([\\d,]+(?:\\.\\d+)?)\\s*(?:rs\\.?|pkr)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WEIGHT_QTY_PATTERN = Pattern.compile(
            "\\b(net\\s*wt\\.?|net weight|weight|qty|quantity)?\\s*[:\\-]?\\s*(\\d+[\\d.,]*)\\s*(kg|g|gm|ml|l)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private LabelTextParser() {
    }

    static ScannedLabel parse(String rawText) {
        ScannedLabel label = new ScannedLabel();
        if (rawText == null || rawText.trim().isEmpty()) {
            return label;
        }
        String normalized = rawText.replace('\r', '\n');
        List<String> lines = new ArrayList<>();
        for (String part : normalized.split("\n")) {
            String line = part.trim();
            if (line.length() >= 2) {
                lines.add(line);
            }
        }

        Matcher priceMatcher = PRICE_PATTERN.matcher(normalized);
        if (priceMatcher.find()) {
            String p = priceMatcher.group(1) != null ? priceMatcher.group(1) : priceMatcher.group(2);
            label.price = cleanNumber(p);
        }

        Matcher unitMatcher = UNIT_PATTERN.matcher(normalized);
        if (unitMatcher.find()) {
            label.unit = normalizeUnit(unitMatcher.group(2));
            String amount = unitMatcher.group(1);
            if (amount != null && !amount.trim().isEmpty()) {
                label.stock = cleanNumber(amount);
            }
        }

        Matcher weightMatcher = WEIGHT_QTY_PATTERN.matcher(normalized);
        if (weightMatcher.find()) {
            if (label.unit == null && weightMatcher.group(3) != null) {
                label.unit = normalizeUnit(weightMatcher.group(3));
            }
            if (label.stock == null && weightMatcher.group(2) != null) {
                label.stock = cleanNumber(weightMatcher.group(2));
            }
        }

        String bestName = null;
        int bestScore = -1;
        for (String line : lines) {
            if (isSkippableLine(line)) {
                continue;
            }
            int score = nameScore(line);
            if (score > bestScore) {
                bestScore = score;
                bestName = line;
            }
        }
        if (bestName != null) {
            label.name = bestName.length() > 80 ? bestName.substring(0, 80) : bestName;
        } else if (!lines.isEmpty()) {
            label.name = lines.get(0);
        }

        if (label.unit == null) {
            label.unit = "kg";
        }
        return label;
    }

    private static boolean isSkippableLine(String line) {
        String lower = line.toLowerCase(Locale.US);
        if (PRICE_PATTERN.matcher(line).find()) {
            return true;
        }
        if (lower.contains("barcode") || lower.contains("batch") || lower.contains("exp") || lower.contains("mfg")) {
            return true;
        }
        return line.replaceAll("[^0-9]", "").length() > line.length() * 0.6;
    }

    private static int nameScore(String line) {
        if (line.length() < 3) {
            return 0;
        }
        int letters = 0;
        for (char c : line.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
            }
        }
        return letters + Math.min(line.length(), 40);
    }

    private static String normalizeUnit(String unit) {
        if (unit == null) {
            return "kg";
        }
        String u = unit.toLowerCase(Locale.US);
        if (u.startsWith("kg") || u.equals("kgs")) {
            return "kg";
        }
        if (u.equals("g") || u.startsWith("gm") || u.contains("gram")) {
            return "g";
        }
        if (u.equals("l") || u.contains("lit")) {
            return "L";
        }
        if (u.equals("ml")) {
            return "ml";
        }
        if (u.contains("bag")) {
            return "bag";
        }
        if (u.contains("pack")) {
            return "pack";
        }
        if (u.contains("crate")) {
            return "crate";
        }
        if (u.contains("pc") || u.contains("piece") || u.equals("unit") || u.equals("units")) {
            return "pcs";
        }
        if (u.contains("dozen")) {
            return "dozen";
        }
        return unit;
    }

    private static String cleanNumber(String value) {
        if (value == null) {
            return null;
        }
        return value.replace(",", "").trim();
    }
}
