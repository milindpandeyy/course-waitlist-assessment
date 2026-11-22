package com.example.waitlist.cli;

import com.example.waitlist.model.LearnerDetails;
import com.example.waitlist.service.CourseRegistrationService;
import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.Set;

/**
 * Interactive console front end for {@link CourseRegistrationService} (DESIGN.md section 13).
 *
 * <p>Input and output only: every rule (validation, normalization, eligibility, capacity,
 * waitlist, promotion) is applied by the service, and the CLI prints the result it returns.
 */
public final class WaitlistCli {

    private static final String MENU = """

            === Course Waitlist ===
            1. Create course
            2. Enroll learner
            3. Drop learner
            4. Check status
            5. Available seats
            6. Exit""";

    private final Scanner in;
    private final PrintStream out;
    private CourseRegistrationService service;

    public WaitlistCli(Scanner in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public static void main(String[] args) {
        new WaitlistCli(new Scanner(System.in), System.out).run();
    }

    /** Shows the menu until the user chooses Exit or the input ends. */
    public void run() {
        while (true) {
            out.println(MENU);
            String choice = prompt("Choose an option (1-6): ");
            if (choice == null) {
                return;
            }
            switch (choice.strip()) {
                case "1" -> createCourse();
                case "2" -> enrollLearner();
                case "3" -> dropLearner();
                case "4" -> checkStatus();
                case "5" -> availableSeats();
                case "6" -> {
                    out.println("Exit.");
                    return;
                }
                default -> out.println("Invalid option. Enter a number from 1 to 6.");
            }
        }
    }

    private void createCourse() {
        if (service != null) {
            out.println("A course has already been created. Only one course is allowed.");
            return;
        }
        String courseId = prompt("Course ID: ");
        String capacityText = prompt("Capacity: ");
        String prerequisites = prompt("Prerequisite course IDs (comma-separated, blank for none): ");
        if (prerequisites == null) {
            return;
        }
        int capacity;
        try {
            capacity = Integer.parseInt(capacityText.strip());
        } catch (NumberFormatException e) {
            out.println("Capacity must be a whole number.");
            return;
        }
        try {
            service = new CourseRegistrationService(
                    CourseRegistrationService.createCourse(courseId, capacity, splitIds(prerequisites)));
            out.println("Course created.");
        } catch (IllegalArgumentException e) {
            out.println("Course not created: " + e.getMessage());
        }
    }

    private void enrollLearner() {
        if (!hasCourse()) {
            return;
        }
        String courseId = prompt("Course ID: ");
        String learnerId = prompt("Learner ID: ");
        String completed = prompt("Completed course IDs (comma-separated, blank for none): ");
        if (completed == null) {
            return;
        }
        out.println("Result: " + service.enroll(courseId, new LearnerDetails(learnerId, splitIds(completed))));
    }

    private void dropLearner() {
        if (!hasCourse()) {
            return;
        }
        String learnerId = prompt("Learner ID: ");
        if (learnerId == null) {
            return;
        }
        out.println("Result: " + service.drop(learnerId));
    }

    private void checkStatus() {
        if (!hasCourse()) {
            return;
        }
        String learnerId = prompt("Learner ID: ");
        if (learnerId == null) {
            return;
        }
        out.println("Status: " + service.getLearnerStatus(learnerId));
    }

    private void availableSeats() {
        if (hasCourse()) {
            out.println("Available seats: " + service.getAvailableSeats());
        }
    }

    /**
     * True if a course exists. Otherwise tells the user to create one first and runs the
     * Create course prompts; the chosen option is not carried out, and the menu is shown again.
     */
    private boolean hasCourse() {
        if (service == null) {
            out.println("No course yet. Create a course first.");
            createCourse();
            return false;
        }
        return true;
    }

    /** Prints the prompt and reads one line; returns null when the input has ended. */
    private String prompt(String text) {
        out.print(text);
        out.flush();
        return in.hasNextLine() ? in.nextLine() : null;
    }

    /** Splits a comma-separated list into IDs, skipping empty entries; the service normalizes them. */
    private static Set<String> splitIds(String text) {
        Set<String> ids = new LinkedHashSet<>();
        for (String part : text.split(",")) {
            if (!part.isBlank()) {
                ids.add(part);
            }
        }
        return ids;
    }
}
