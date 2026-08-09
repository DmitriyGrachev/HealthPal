package com.fit.fitnessapp.telegram.application.service;

public final class TelegramMessages {

    public static final String LINK_REQUIRED = "Please link your account first using `/link`.";
    public static final String LINK_CODE_REQUIRED = "Please provide the linking code: `/link 123456`.";
    public static final String LINK_SUCCESS =
            "Success! Your account is now linked. You can start using FitnessApp commands.";
    public static final String LINK_INVALID =
            "Invalid or expired code. Please generate a new one on the website.";
    public static final String LINK_RATE_LIMITED =
            "Too many invalid link attempts. Please wait before trying again.";
    public static final String ASK_REQUIRED =
            "Please provide a question: `/ask How much protein did I have today?`.";
    public static final String ASK_THINKING = "Thinking...";
    public static final String ASK_RATE_LIMITED = "Too many AI requests. Please wait a moment and try again.";
    public static final String TODAY_GENERATING =
            "Analysing your data and generating daily insight...";
    public static final String NOTE_TYPE_PROMPT = "What type of note is this?";
    public static final String NOTE_TYPE_INVALID =
            "Please choose one of the suggested note types.";
    public static final String NOTE_CONTENT_PROMPT = "Got it. Now, what would you like to record?";
    public static final String WEIGHT_PROMPT = "Please enter your current weight in kg (e.g., 75.5):";
    public static final String WEIGHT_UNREALISTIC = "Please enter a realistic weight value.";
    public static final String WEIGHT_INVALID_FORMAT =
            "Invalid format. Please enter a number (e.g., 80 or 72.5):";

    private TelegramMessages() {
    }

    public static String startLinked() {
        return """
                Welcome back to *FitnessApp*!

                Your account is linked. You can use commands like:
                /today - Get daily insights
                /week - Get weekly report
                /note - Save a quick note
                /weight - Log your weight""";
    }

    public static String startUnlinked() {
        return """
                Hello! Welcome to *FitnessApp Bot*!

                To start tracking your progress here, you need to link your account.

                1. Go to the FitnessApp Web Dashboard.
                2. Find the 'Link Telegram' section.
                3. Use the command `/link <your_code>` here.""";
    }

    public static String noteSaved(String type, String content) {
        return "Note received for processing.\nType: " + type + "\nContent: " + content;
    }

    public static String weightRecorded(String formattedWeight) {
        return "Got it! " + formattedWeight + " kg received for processing.";
    }

    public static String testCodeGenerated(String code) {
        return "TEST MODE: Generated code for User ID 1: `" + code + "`\n\nUse `/link " + code + "` to bind.";
    }

    public static String insight(Object insightType, Object date, String content) {
        return "*Your " + insightType + " Insight (" + date + "):*\n\n" + content;
    }

    public static String personalizedInsight(String telegramSummary) {
        return "*Personalized Insight:*\n\n" + telegramSummary;
    }
}
