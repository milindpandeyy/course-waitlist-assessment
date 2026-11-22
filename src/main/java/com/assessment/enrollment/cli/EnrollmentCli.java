package com.assessment.enrollment.cli;

import com.assessment.enrollment.model.LearnerDetails;
import com.assessment.enrollment.service.CourseConfigurationService;
import com.assessment.enrollment.service.EnrollmentService;
import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.Set;

/**
 * Interactive console front end (DESIGN.md section 13).
 *
 * <p>Input and output only: it reads and converts input, calls the services and prints what
 * they return. Every business rule (configure once, validation, normalization, eligibility,
 * capacity, waitlist, promotion) is enforced by the services.
 */
public final class EnrollmentCli {

    private static final String MENU = """

            === Course Waitlist ===
            1. Create course
            2. Enroll learner
            3. Drop learner
            4. Check status
            5. Available seats
            6. Exit""";

    private final CourseConfigurationService configuration;
    private final EnrollmentService enrollment;
    private final Scanner in;
    private final PrintStream out;

    public EnrollmentCli(CourseConfigurationService configuration, EnrollmentService enrollment,
                         Scanner in, PrintStream out) {
        this.configuration = configuration;
        this.enrollment = enrollment;
        this.in = in;
        this.out = out;
    }

    public static void main(String[] args) {
        CourseConfigurationService configuration = new CourseConfigurationService();
        EnrollmentService enrollment = new EnrollmentService(configuration);
        new EnrollmentCli(configuration, enrollment, new Scanner(System.in), System.out).run();
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
        if (configuration.isConfigured()) {
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
            configuration.configure(courseId, capacity, splitIds(prerequisites));
            out.println("Course created.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            out.println("Course not created: " + e.getMessage());
        }
    }

    private void enrollLearner() {
        if (!courseReady()) {
            return;
        }
        String courseId = prompt("Course ID: ");
        String learnerId = prompt("Learner ID: ");
        String completed = prompt("Completed course IDs (comma-separated, blank for none): ");
        if (completed == null) {
            return;
        }
        out.println("Result: " + enrollment.enroll(courseId, new LearnerDetails(learnerId, splitIds(completed))));
    }

    private void dropLearner() {
        if (!courseReady()) {
            return;
        }
        String learnerId = prompt("Learner ID: ");
        if (learnerId == null) {
            return;
        }
        out.println("Result: " + enrollment.drop(learnerId));
    }

    private void checkStatus() {
        if (!courseReady()) {
            return;
        }
        String learnerId = prompt("Learner ID: ");
        if (learnerId == null) {
            return;
        }
        out.println("Status: " + enrollment.getLearnerStatus(learnerId));
    }

    private void availableSeats() {
        if (courseReady()) {
            out.println("Available seats: " + enrollment.getAvailableSeats());
        }
    }

    /**
     * Menu flow, not a rule: if no course is configured yet, says so and runs the Create course
     * prompts instead of the chosen option. The services themselves reject any operation
     * before configuration.
     */
    private boolean courseReady() {
        if (configuration.isConfigured()) {
            return true;
        }
        out.println("No course yet. Create a course first.");
        createCourse();
        return false;
    }

    /** Prints the prompt and reads one line; returns null when the input has ended. */
    private String prompt(String text) {
        out.print(text);
        out.flush();
        return in.hasNextLine() ? in.nextLine() : null;
    }

    /** Splits a comma-separated list into IDs, skipping empty entries; the services normalize them. */
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
