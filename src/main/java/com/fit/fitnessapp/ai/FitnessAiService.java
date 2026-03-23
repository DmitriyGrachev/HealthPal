package com.fit.fitnessapp.ai;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class FitnessAiService {
/*
    private final ChatClient chatClient;
    private final UserRepository userRepository;
    private final WorkoutJpaRepository workoutJpaRepository;
    private final FatSecretService fatSecretService; // Assuming this exists

 */


    /**
     * The main entry point for AI interaction.
     * 1. Fetches User Context
     * 2. Constructs the System Prompt
     * 3. Calls Gemini
     */
    /*
    public String getPersonalizedAdvice(Long userId, String userQuestion) {
        // 1. Fetch Context
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Fetch last 5 workouts to give AI trend context
        List<Workout> recentWorkouts = workoutRepository.findTop5ByUserIdOrderByDateDesc(userId);

        // Fetch today's nutrition summary (mocked for this example)
        String nutritionSummary = fatSecretService.getDailySummary(userId, LocalDate.now());

        // 2. Construct the System Prompt (The Persona)
        // We use a template to inject data dynamically
        String systemText = """
            You are a professional, empathetic, and data-driven Personal Trainer.
            
            USER PROFILE:
            - Name: {name}
            - Age: {age}
            - Weight: {weight}kg
            - Goal: {goal}
            
            RECENT ACTIVITY:
            {workout_history}
            
            NUTRITION TODAY:
            {nutrition}
            
            Your goal is to answer the user's question based strictly on their data. 
            If they are failing to lose weight, analyze their nutrition and workout intensity.
            Keep answers concise and actionable.
            """;

        PromptTemplate systemPromptTemplate = new PromptTemplate(systemText);

        // Format the workout history into a readable string
        String workoutHistoryStr = recentWorkouts.isEmpty() ? "No recent workouts." :
                recentWorkouts.stream()
                        .map(w -> "- " + w.getDate() + ": " + w.getType() + " (" + w.getDurationMinutes() + " mins)")
                        .collect(Collectors.joining("\n"));

        // Inject variables
        Map<String, Object> promptVariables = Map.of(
                "name", user.getName(),
                "age", user.getAge(),
                "weight", user.getCurrentWeight(),
                "goal", user.getFitnessGoal(), // e.g., "Hypertrophy" or "Weight Loss"
                "workout_history", workoutHistoryStr,
                "nutrition", nutritionSummary
        );

        SystemMessage systemMessage = (SystemMessage) systemPromptTemplate.createMessage(promptVariables);
        UserMessage userMessage = new UserMessage(userQuestion);

        // 3. Call Gemini
        // We pass the SystemMessage (Context) and UserMessage (Question) together
        return chatClient.prompt(new Prompt(List.of(systemMessage, userMessage)))
                .call()
                .content();
    }

     */
}