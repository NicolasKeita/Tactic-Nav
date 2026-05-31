package com.tacticnav.pmd;

import java.util.HashSet;
import java.util.Set;

import net.sourceforge.pmd.lang.java.ast.ASTBlock;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.ast.impl.javacc.JjtreeNode;

public class CommentsInsideMethodsRule extends AbstractJavaRule {

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        checkBlockForComments(node, node.getBody(), data);
        return super.visit(node, data);
    }

    @Override
    public Object visit(ASTConstructorDeclaration node, Object data) {
        checkBlockForComments(node, node.getBody(), data);
        return super.visit(node, data);
    }

    private void checkBlockForComments(JjtreeNode<?> declaration, ASTBlock block, Object data) {
        if (block == null) {
            return;
        }
        Set<JavaccToken> reported = new HashSet<>();
        JavaccToken firstToken = block.getFirstToken();
        JavaccToken lastToken = block.getLastToken();

        // Ligne de l'accolade ouvrante : les commentaires avant cette ligne sont en dehors du corps
        int braceLine = firstToken.getReportLocation().getStartLine();
        int braceColumn = firstToken.getReportLocation().getStartColumn();

        JavaccToken token = firstToken.getNext() != null ? firstToken.getNext() : firstToken;
        while (token != null) {
            JavaccToken comment = token.getPreviousComment();
            if (comment != null && reported.add(comment)) {
                // Ne signaler que si le commentaire est après l'accolade ouvrante '{'
                int commentLine = comment.getReportLocation().getStartLine();
                int commentColumn = comment.getReportLocation().getStartColumn();
                if (commentLine > braceLine ||
                    (commentLine == braceLine && commentColumn > braceColumn)) {
                    asCtx(data).addViolation(block, declaration.getImage());
                }
            }
            if (token == lastToken) {
                break;
            }
            token = token.getNext();
        }
    }
}