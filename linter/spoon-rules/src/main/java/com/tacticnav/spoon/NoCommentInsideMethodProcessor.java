package com.tacticnav.spoon;

import spoon.SpoonException;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtComment;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.Level;

/**
 * Spoon processor that detects comments
 * inside method/constructor bodies.
 */
public class NoCommentInsideMethodProcessor extends AbstractProcessor<CtExecutable<?>> {

    @Override
    public void process(CtExecutable<?> executable) {
        checkForComments(executable);
    }

    private void checkForComments(CtExecutable<?> executable) {
        CtElement body = executable.getBody();

        if (body == null) {
            return;
        }

        var comments = body.getElements(
            new TypeFilter<>(CtComment.class)
        );
        if (comments.isEmpty()) {
            return;
        }

        SourcePosition bodyPosition = body.getPosition();
        if (bodyPosition == null || !bodyPosition.isValidPosition()) {
            return;
        }

        boolean hasCommentInsideBody = false;
        int bodyStartLine = bodyPosition.getLine();
        int bodyStartColumn = bodyPosition.getColumn();
        for (CtComment comment : comments) {
            SourcePosition commentPosition = comment.getPosition();
            if (commentPosition == null || !commentPosition.isValidPosition()) {
                continue;
            }

            int commentLine = commentPosition.getLine();
            int commentColumn = commentPosition.getColumn();

            if (commentLine > bodyStartLine ||
                (commentLine == bodyStartLine && commentColumn > bodyStartColumn)) {
                hasCommentInsideBody = true;
                getFactory().getEnvironment().report(
                    this,
                    Level.WARN,
                    comment,
                    String.format(
                        "Comment detected inside method/constructor '%s'",
                        getExecutableName(executable)
                    )
                );
            }
        }

        if (hasCommentInsideBody) {
            throw new SpoonException(
                String.format(
                    "Comment detected inside method/constructor '%s'",
                    getExecutableName(executable)
                )
            );
        }
    }

    private String getExecutableName(CtExecutable<?> executable) {
        if (executable instanceof CtMethod) {
            return ((CtMethod<?>) executable).getSimpleName();
        }
        return executable.getSimpleName();
    }
}
