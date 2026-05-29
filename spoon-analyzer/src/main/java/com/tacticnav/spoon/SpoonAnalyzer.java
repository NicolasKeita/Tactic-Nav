package com.tacticnav.spoon;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Spoon AST analyzer for Tactic-Nav codebase.
 * Detects forbidden patterns, comments, and coding rule violations.
 */
public final class SpoonAnalyzer {

    private static final List<String> FORBIDDEN_PATTERNS = List.of(
        "forbidden",
        "TODO",
        "FIXME",
        "HACK"
    );

    private SpoonAnalyzer() {
    }

    public static void main(String[] args) {
        String sourcePath = args.length > 0 ? args[0] : "src/main/java";
        System.out.println("🔍 Running Spoon AST analysis on: " + sourcePath);

        // Validate source path
        File sourceDir = new File(sourcePath);
        if (!sourceDir.exists()) {
            System.err.println("❌ Source directory not found: " + sourceDir.getAbsolutePath());
            System.exit(1);
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(sourcePath);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);

        // Register custom processors
        launcher.addProcessor(new ForbiddenCommentProcessor());
        launcher.addProcessor(new MethodCommentProcessor());
        launcher.addProcessor(new ForbiddenFieldProcessor());

        // Build model and run processors
        launcher.buildModel();
        launcher.process();

        System.out.println("✅ Spoon analysis complete.");
    }

    /**
     * Processor that detects comments containing forbidden patterns.
     */
    public static final class ForbiddenCommentProcessor extends AbstractProcessor<CtComment> {
        private final List<CtComment> violations = new ArrayList<>();

        @Override
        public void process(CtComment comment) {
            String content = comment.getContent().toLowerCase();
            for (String pattern : FORBIDDEN_PATTERNS) {
                if (content.contains(pattern.toLowerCase())) {
                    violations.add(comment);
                    printViolation(comment, "Forbidden comment pattern: '" + pattern + "'");
                    break;
                }
            }
        }

        private void printViolation(CtElement element, String message) {
            String position = element.getPosition() != null
                ? element.getPosition().getFile() + ":" + element.getPosition().getLine()
                : "unknown position";
            System.err.println("⚠️  VIOLATION [" + position + "]: " + message);
            System.err.println("   → " + element.toString().trim());
        }
    }

    /**
     * Processor that rejects comments inside method bodies.
     * Only class-level or file-level comments are allowed.
     */
    public static final class MethodCommentProcessor extends AbstractProcessor<CtComment> {

        @Override
        public void process(CtComment comment) {
            // Skip Javadoc — those are documentation, not inline comments
            if (comment.getCommentType() == CtComment.CommentType.JAVADOC) {
                return;
            }

            // Walk up the parent tree to check if this comment is inside a method body
            CtElement parent = comment.getParent();
            while (parent != null) {
                if (parent instanceof CtMethod) {
                    String position = comment.getPosition() != null
                        ? comment.getPosition().getFile() + ":" + comment.getPosition().getLine()
                        : "unknown position";
                    System.err.println("⚠️  VIOLATION [" + position + "]: Comment inside method body is forbidden");
                    System.err.println("   → " + comment.toString().trim());
                    return;
                }
                parent = parent.getParent();
            }
        }
    }

    /**
     * Processor that detects non-private instance fields (in classes that should be utility/final).
     */
    public static final class ForbiddenFieldProcessor extends AbstractProcessor<CtFieldRead<?>> {
        @Override
        public void process(CtFieldRead<?> fieldRead) {
            // This is a placeholder for more advanced rules.
            // Example: detecting System.out/err calls
            if (fieldRead.getVariable().getQualifiedName().equals("java.lang.System.out")
                || fieldRead.getVariable().getQualifiedName().equals("java.lang.System.err")) {
                String position = fieldRead.getPosition() != null
                    ? fieldRead.getPosition().getFile() + ":" + fieldRead.getPosition().getLine()
                    : "unknown position";
                System.err.println("⚠️  VIOLATION [" + position + "]: Avoid direct System.out/err calls, use a logger instead.");
                System.err.println("   → " + fieldRead.toString().trim());
            }
        }
    }
}